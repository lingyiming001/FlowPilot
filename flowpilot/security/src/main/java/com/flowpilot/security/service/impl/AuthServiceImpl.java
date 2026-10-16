package com.flowpilot.security.service.impl;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.security.dto.Actor;
import com.flowpilot.security.mapper.SecurityMapper;
import com.flowpilot.security.service.AuthService;
import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
  private final SecurityMapper db;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  public AuthServiceImpl(SecurityMapper db, PasswordEncoder passwords, JwtEncoder encoder) {
    this.db = db;
    this.passwords = passwords;
    this.encoder = encoder;
  }

  public Map<String, Object> login(String username, String password) {
    var rows = db.rows("SELECT * FROM sys_user WHERE username=? AND enabled=TRUE", username);
    if (rows.isEmpty()
        || !passwords.matches(password, rows.getFirst().get("password_hash").toString()))
      throw new BusinessException(
          "BAD_CREDENTIALS", "用户名或密码错误", org.springframework.http.HttpStatus.UNAUTHORIZED);
    var actor = load(rows.getFirst().get("id").toString());
    return issueAccess(actor, null);
  }

  public Map<String, Object> issueAccess(Actor actor, String familyId) {
    Instant now = Instant.now();
    var builder =
        JwtClaimsSet.builder()
            .issuer("flowpilot")
            .subject(actor.id())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .id(UUID.randomUUID().toString());
    if (familyId != null) builder.claim("sid", familyId);
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    builder.build()))
            .getTokenValue();
    return Map.of("token", token, "user", actor, "expiresIn", 3600);
  }

  public Actor load(String id) {
    var u = db.one("SELECT id,username,display_name,team_id,enabled FROM sys_user WHERE id=?", id);
    if (!Boolean.TRUE.equals(u.get("enabled")) && !"1".equals(u.get("enabled").toString()))
      throw BusinessException.denied();
    Set<String> roles = new HashSet<>(), permissions = new HashSet<>();
    db.rows(
            "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=?",
            id)
        .forEach(r -> roles.add(r.get("code").toString()));
    db.rows(
            "SELECT DISTINCT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=?",
            id)
        .forEach(r -> permissions.add(r.get("code").toString()));
    return new Actor(
        id,
        u.get("username").toString(),
        u.get("display_name").toString(),
        u.get("team_id").toString(),
        roles,
        permissions);
  }

  public Actor current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) throw BusinessException.denied();
    if (jwt.getExpiresAt() != null && !jwt.getExpiresAt().isAfter(Instant.now()))
      throw new BusinessException(
          "UNAUTHORIZED", "登录已过期", org.springframework.http.HttpStatus.UNAUTHORIZED);
    String family = jwt.getClaimAsString("sid");
    if (family != null
        && db.count(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id=? AND user_id=? AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(6)",
                family,
                jwt.getSubject())
            == 0)
      throw new BusinessException(
          "UNAUTHORIZED", "登录已退出或过期", org.springframework.http.HttpStatus.UNAUTHORIZED);
    return load(jwt.getSubject());
  }

  public void require(String p) {
    if (!current().has(p)) throw BusinessException.denied();
  }

  public List<Map<String, Object>> users() {
    require("users:manage");
    return db
        .rows(
            "SELECT id,username,display_name,team_id,enabled,version FROM sys_user WHERE team_id=?",
            current().teamId())
        .stream()
        .map(
            u -> {
              var v = new LinkedHashMap<>(u);
              v.put("roles", loadRoles(u.get("id").toString()));
              return (Map<String, Object>) v;
            })
        .toList();
  }

  private List<String> loadRoles(String id) {
    return db
        .rows(
            "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON r.id=ur.role_id WHERE ur.user_id=?",
            id)
        .stream()
        .map(r -> r.get("code").toString())
        .toList();
  }

  public List<Map<String, Object>> roles() {
    require("users:manage");
    return db.rows(
        "SELECT r.id,r.code,r.name,p.code AS permission FROM sys_role r LEFT JOIN sys_role_permission rp ON r.id=rp.role_id LEFT JOIN sys_permission p ON p.id=rp.permission_id ORDER BY r.code,p.code");
  }

  @Transactional
  public void updateUser(String id, boolean enabled, List<String> roles) {
    require("users:manage");
    Actor self = current();
    if (self.id().equals(id)) throw BusinessException.invalid("不能修改自己的账号权限");
    db.one("SELECT id FROM sys_user WHERE id=? AND team_id=? FOR UPDATE", id, self.teamId());
    for (String r : roles) db.one("SELECT id FROM sys_role WHERE code=?", r);
    db.update("UPDATE sys_user SET enabled=?,version=version+1 WHERE id=?", enabled, id);
    db.update("DELETE FROM sys_user_role WHERE user_id=?", id);
    for (String r : roles)
      db.update(
          "INSERT INTO sys_user_role(id,user_id,role_id) SELECT ?,?,id FROM sys_role WHERE code=?",
          UUID.randomUUID().toString(),
          id,
          r);
    db.update(
        "INSERT INTO audit_log(id,team_id,actor_id,event_type,summary) VALUES (?,?,?,?,?)",
        UUID.randomUUID().toString(),
        self.teamId(),
        self.id(),
        "USER_ROLES_CHANGED",
        "账号权限变更");
  }

  @Transactional
  public String createUser(
      String username, String password, String displayName, List<String> roles) {
    require("users:manage");
    if (password.length() < 12 || password.length() > 72)
      throw BusinessException.invalid("密码长度必须为12至72个字符");
    Actor actor = current();
    for (String role : roles) db.one("SELECT id FROM sys_role WHERE code=?", role);
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO sys_user(id,username,password_hash,display_name,team_id) VALUES(?,?,?,?,?)",
        id,
        username,
        passwords.encode(password),
        displayName,
        actor.teamId());
    for (String role : roles)
      db.update(
          "INSERT INTO sys_user_role(id,user_id,role_id) VALUES(?,?,?)",
          UUID.randomUUID().toString(),
          id,
          role);
    db.update(
        "INSERT INTO audit_log(id,team_id,actor_id,event_type,summary) VALUES(?,?,?,?,?)",
        UUID.randomUUID().toString(),
        actor.teamId(),
        actor.id(),
        "USER_CREATED",
        "新增用户与角色分配");
    return id;
  }
}
