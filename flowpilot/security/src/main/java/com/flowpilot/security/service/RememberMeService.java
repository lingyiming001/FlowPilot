package com.flowpilot.security.service;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.security.dto.Actor;
import com.flowpilot.security.mapper.SecurityMapper;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RememberMeService {
  public record Session(
      Map<String, Object> access, String refreshToken, boolean remembered, Instant expires) {}

  private final SecurityMapper db;
  private final AuthService auth;
  private final Crypto crypto;
  private final TransactionTemplate tx;

  public RememberMeService(
      SecurityMapper db, AuthService auth, Crypto crypto, TransactionTemplate tx) {
    this.db = db;
    this.auth = auth;
    this.crypto = crypto;
    this.tx = tx;
  }

  private String randomToken() {
    byte[] b = new byte[48];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  private void revoke(String raw) {
    if (raw == null || raw.isBlank()) return;
    var rows =
        db.rows("SELECT family_id FROM auth_refresh_session WHERE token_hash=?", Json.hash(raw));
    if (!rows.isEmpty())
      db.update(
          "UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(6)),replacement_cipher=NULL WHERE family_id=?",
          rows.getFirst().get("family_id"));
  }

  public Session create(Actor actor, boolean remember, String oldCookie) {
    return tx.execute(
        status -> {
          revoke(oldCookie);
          String family = Json.id(), token = randomToken();
          Instant expiry = Instant.now().plusSeconds(remember ? 30L * 86400 : 12L * 3600);
          String stamp =
              Json.hash(
                  db.one("SELECT password_hash FROM sys_user WHERE id=?", actor.id())
                      .get("password_hash")
                      .toString());
          insert(actor.id(), family, stamp, token, remember, expiry);
          return new Session(auth.issueAccess(actor, family), token, remember, expiry);
        });
  }

  private void insert(
      String user, String family, String stamp, String raw, boolean remember, Instant expiry) {
    db.update(
        "INSERT INTO auth_refresh_session(id,user_id,family_id,token_hash,password_stamp,remembered,expires_at) VALUES(?,?,?,?,?,?,?)",
        Json.id(),
        user,
        family,
        Json.hash(raw),
        stamp,
        remember,
        java.sql.Timestamp.from(expiry));
  }

  public Session refresh(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > 200) throw unauthorized();
    Session result =
        tx.execute(
            status -> {
              var rows =
                  db.rows(
                      "SELECT * FROM auth_refresh_session WHERE token_hash=? FOR UPDATE",
                      Json.hash(raw));
              if (rows.isEmpty()) return null;
              var s = rows.getFirst();
              String user = s.get("user_id").toString(), family = s.get("family_id").toString();
              Instant expiry = ((java.sql.Timestamp) s.get("expires_at")).toInstant();
              Actor actor;
              try {
                actor = auth.load(user);
              } catch (BusinessException e) {
                revoke(raw);
                return null;
              }
              String stamp =
                  Json.hash(
                      db.one("SELECT password_hash FROM sys_user WHERE id=?", user)
                          .get("password_hash")
                          .toString());
              if (!expiry.isAfter(Instant.now()) || !stamp.equals(s.get("password_stamp"))) {
                revoke(raw);
                return null;
              }
              boolean remember = Boolean.TRUE.equals(s.get("remembered"));
              if (s.get("revoked_at") != null) {
                Instant revoked = ((java.sql.Timestamp) s.get("revoked_at")).toInstant();
                if (revoked.isAfter(Instant.now().minusSeconds(10))
                    && s.get("replacement_cipher") != null) {
                  String replacement = crypto.decrypt(s.get("replacement_cipher").toString());
                  if (db.count(
                          "SELECT COUNT(*) FROM auth_refresh_session WHERE token_hash=? AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(6)",
                          Json.hash(replacement))
                      > 0)
                    return new Session(
                        auth.issueAccess(actor, family), replacement, remember, expiry);
                }
                revoke(raw);
                return null;
              }
              String token = randomToken();
              insert(user, family, stamp, token, remember, expiry);
              db.update(
                  "UPDATE auth_refresh_session SET revoked_at=UTC_TIMESTAMP(6),replacement_cipher=? WHERE id=?",
                  crypto.encrypt(token),
                  s.get("id"));
              return new Session(auth.issueAccess(actor, family), token, remember, expiry);
            });
    if (result == null) throw unauthorized();
    return result;
  }

  public void logout(String raw) {
    tx.executeWithoutResult(s -> revoke(raw));
  }

  private BusinessException unauthorized() {
    return new BusinessException("UNAUTHORIZED", "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED);
  }
}
