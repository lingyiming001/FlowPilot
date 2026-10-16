package com.flowpilot.app.config;

import com.flowpilot.common.util.Json;
import com.flowpilot.security.mapper.SecurityMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "flowpilot.bootstrap-enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
  private final SecurityMapper db;
  private final PasswordEncoder encoder;
  private final String username, password, team;

  public BootstrapAdminInitializer(
      SecurityMapper db,
      PasswordEncoder encoder,
      @Value("${flowpilot.bootstrap-username:admin}") String username,
      @Value("${flowpilot.bootstrap-password}") String password,
      @Value("${flowpilot.bootstrap-team:team-default}") String team) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.team = team;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (db.count("SELECT COUNT(*) FROM sys_user") > 0) return;
    if (password.length() < 12 || password.length() > 72)
      throw new IllegalArgumentException("Bootstrap password must be 12-72 characters");
    String id = Json.id();
    db.update(
        "INSERT INTO sys_user(id,username,password_hash,display_name,team_id) VALUES(?,?,?,?,?)",
        id,
        username,
        encoder.encode(password),
        "系统管理员",
        team);
    db.update("INSERT INTO sys_user_role(id,user_id,role_id) VALUES(?,?,'ADMIN')", Json.id(), id);
    db.update(
        "INSERT INTO audit_log(id,team_id,actor_id,event_type,summary) VALUES(?,?,?,?,?)",
        Json.id(),
        team,
        id,
        "BOOTSTRAP_ADMIN",
        "首次管理员初始化");
  }
}
