package com.flowpilot.audit.service.impl;

import com.flowpilot.audit.mapper.AuditMapper;
import com.flowpilot.audit.service.AuditService;
import com.flowpilot.common.util.Json;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AuditServiceImpl implements AuditService {
  private final AuditMapper db;

  public AuditServiceImpl(AuditMapper db) {
    this.db = db;
  }

  public void record(String team, String workflow, String actor, String event, String summary) {
    db.update(
        "INSERT INTO audit_log(id,team_id,workflow_id,actor_id,event_type,summary) VALUES(?,?,?,?,?,?)",
        Json.id(),
        team,
        workflow,
        actor,
        event,
        summary);
  }

  public List<Map<String, Object>> list(String team, int page) {
    return db.rows(
        "SELECT id,workflow_id,actor_id,event_type,summary,created_at FROM audit_log WHERE team_id=? ORDER BY created_at DESC LIMIT 50 OFFSET ?",
        team,
        Math.max(0, page - 1) * 50);
  }
}
