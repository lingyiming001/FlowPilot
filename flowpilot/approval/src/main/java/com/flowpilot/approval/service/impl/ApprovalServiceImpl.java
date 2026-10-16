package com.flowpilot.approval.service.impl;

import com.flowpilot.approval.mapper.ApprovalMapper;
import com.flowpilot.approval.service.ApprovalService;
import com.flowpilot.audit.service.AuditService;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.Json;
import com.flowpilot.security.dto.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalServiceImpl implements ApprovalService {
  private final ApprovalMapper db;
  private final AuditService audit;

  public ApprovalServiceImpl(ApprovalMapper db, AuditService audit) {
    this.db = db;
    this.audit = audit;
  }

  public String create(
      String workflow,
      String team,
      String proposal,
      String action,
      String target,
      String digest,
      String role,
      String requester,
      Instant expires) {
    String id = Json.id();
    db.update(
        "INSERT INTO approval_request(id,team_id,workflow_id,proposal_id,action_type,target_id,snapshot_digest,required_role,requested_by,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
        id,
        team,
        workflow,
        proposal,
        action,
        target,
        digest,
        role,
        requester,
        java.sql.Timestamp.from(expires));
    return id;
  }

  @Transactional
  public Map<String, Object> decide(
      String id, Actor actor, int version, String decision, String comment) {
    var a =
        db.one(
            "SELECT * FROM approval_request WHERE id=? AND team_id=? FOR UPDATE",
            id,
            actor.teamId());
    if (!actor.has("approvals:decide")
        || !(actor.role(a.get("required_role").toString())
            || ("AGENT_OPERATOR".equals(a.get("required_role")) && actor.role("SUPERVISOR"))))
      throw BusinessException.denied();
    if (actor.id().equals(a.get("requested_by"))) throw BusinessException.denied();
    if (!"PENDING".equals(a.get("status")) || ((Number) a.get("version")).intValue() != version)
      throw BusinessException.conflict("审批已被处理或版本已变化");
    if (((java.sql.Timestamp) a.get("expires_at")).toInstant().isBefore(Instant.now()))
      throw BusinessException.conflict("审批已过期");
    if (!Set.of("APPROVE", "REJECT", "RETURN").contains(decision))
      throw BusinessException.invalid("无效审批决策");
    String status =
        switch (decision) {
          case "APPROVE" -> "APPROVED";
          case "REJECT" -> "REJECTED";
          default -> "RETURNED";
        };
    if (db.update(
            "UPDATE approval_request SET status=?,version=version+1 WHERE id=? AND status='PENDING' AND version=?",
            status,
            id,
            version)
        != 1) throw BusinessException.conflict("并发审批冲突");
    db.update(
        "INSERT INTO approval_record(id,approval_id,actor_id,decision,comment,snapshot_digest) VALUES(?,?,?,?,?,?)",
        Json.id(),
        id,
        actor.id(),
        decision,
        comment,
        a.get("snapshot_digest"));
    audit.record(
        actor.teamId(),
        a.get("workflow_id").toString(),
        actor.id(),
        "APPROVAL_" + decision,
        "审批操作 " + a.get("action_type"));
    return a;
  }

  public Map<String, Object> approved(String id) {
    return db.one(
        "SELECT a.*,r.actor_id FROM approval_request a JOIN approval_record r ON r.approval_id=a.id WHERE a.id=? AND a.status='APPROVED' AND r.decision='APPROVE'",
        id);
  }

  public List<Map<String, Object>> list(String team) {
    return db.rows(
        "SELECT id,workflow_id,proposal_id,action_type,target_id,status,required_role,expires_at,version,created_at,snapshot_digest FROM approval_request WHERE team_id=? ORDER BY created_at DESC LIMIT 100",
        team);
  }
}
