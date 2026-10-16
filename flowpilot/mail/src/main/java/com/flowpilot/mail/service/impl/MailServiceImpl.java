package com.flowpilot.mail.service.impl;

import com.flowpilot.common.util.*;
import com.flowpilot.mail.mapper.MailMapper;
import com.flowpilot.mail.service.MailService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailServiceImpl implements MailService {
  private final MailMapper db;
  private final Crypto crypto;

  public MailServiceImpl(MailMapper db, Crypto crypto) {
    this.db = db;
    this.crypto = crypto;
  }

  public List<Map<String, Object>> list(String team) {
    return db.rows(
        "SELECT m.id,m.subject,m.status,m.identity_verified,m.customer_id,m.created_at,w.id AS workflow_id,w.state AS workflow_state FROM mail_message m LEFT JOIN workflow_instance w ON w.mail_id=m.id WHERE m.team_id=? ORDER BY m.created_at DESC LIMIT 100",
        team);
  }

  public Map<String, Object> detail(String id, String team) {
    var m =
        db.one(
            "SELECT id,subject,status,identity_verified,customer_id,body_cipher,created_at FROM mail_message WHERE id=? AND team_id=?",
            id,
            team);
    var v = new LinkedHashMap<>(m);
    v.put("body", crypto.decrypt(v.remove("body_cipher").toString()));
    return v;
  }

  @Transactional
  public String receive(
      String account, String messageId, String from, String subject, String body, boolean trusted) {
    var a = db.one("SELECT team_id FROM mail_account WHERE id=? AND enabled=TRUE", account);
    String hash = crypto.identityHash(from);
    var c =
        db.rows(
            "SELECT c.id FROM customer c JOIN customer_identity i ON i.customer_id=c.id WHERE c.team_id=? AND i.identity_hash=? AND i.verified=TRUE",
            a.get("team_id"),
            hash);
    String customer = c.isEmpty() ? null : c.getFirst().get("id").toString();
    String message =
        (messageId == null || messageId.isBlank()) ? Json.hash(from + subject + body) : messageId;
    String id = Json.id();
    db.update(
        "INSERT IGNORE INTO mail_message(id,team_id,customer_id,account_id,message_id,subject,body_cipher,sender_hash,identity_verified) VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        a.get("team_id"),
        customer,
        account,
        message,
        subject,
        crypto.encrypt(body),
        hash,
        trusted && customer != null);
    return db.one(
            "SELECT id FROM mail_message WHERE account_id=? AND message_id=?", account, message)
        .get("id")
        .toString();
  }
}
