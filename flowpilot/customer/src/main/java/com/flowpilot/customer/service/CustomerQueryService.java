package com.flowpilot.customer.service;

import com.flowpilot.common.util.Crypto;
import com.flowpilot.customer.mapper.CustomerMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CustomerQueryService {
  private final CustomerMapper db;
  private final Crypto crypto;

  public CustomerQueryService(CustomerMapper db, Crypto crypto) {
    this.db = db;
    this.crypto = crypto;
  }

  public Map<String, Object> find(String customer, String team) {
    var row =
        db.one("SELECT id,name,team_id FROM customer WHERE id=? AND team_id=?", customer, team);
    var view = new LinkedHashMap<>(row);
    String name = String.valueOf(row.get("name"));
    view.put("name", name.isEmpty() ? "***" : name.substring(0, 1) + "**");
    return view;
  }

  public String resolve(String email, String team) {
    return db.one(
            "SELECT c.id FROM customer c JOIN customer_identity i ON i.customer_id=c.id WHERE i.identity_hash=? AND i.verified=TRUE AND c.team_id=?",
            crypto.identityHash(email),
            team)
        .get("id")
        .toString();
  }

  public String email(String customer, String team) {
    return crypto.decrypt(
        db.one("SELECT email_cipher FROM customer WHERE id=? AND team_id=?", customer, team)
            .get("email_cipher")
            .toString());
  }
}
