package com.flowpilot.workflow.service.impl;

import com.flowpilot.agent.service.ProposalGenerator;
import com.flowpilot.approval.service.ApprovalService;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.security.service.AuthService;
import com.flowpilot.workflow.engine.WorkflowEngine;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.service.WorkflowApplicationService;
import com.flowpilot.workflow.state.AgentStateManager;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowApplicationServiceImpl implements WorkflowApplicationService {
  private final WorkflowMapper db;
  private final AuthService auth;
  private final ApprovalService approval;
  private final AgentStateManager states;
  private final Crypto crypto;
  private final ProposalGenerator proposals;
  private final WorkflowEngine engine;
  private final com.flowpilot.workflow.executor.ToolExecutionService executions;

  public WorkflowApplicationServiceImpl(
      WorkflowMapper db,
      AuthService auth,
      ApprovalService approval,
      AgentStateManager states,
      Crypto crypto,
      ProposalGenerator proposals,
      WorkflowEngine engine,
      com.flowpilot.workflow.executor.ToolExecutionService executions) {
    this.db = db;
    this.auth = auth;
    this.approval = approval;
    this.states = states;
    this.crypto = crypto;
    this.proposals = proposals;
    this.engine = engine;
    this.executions = executions;
  }

  private Map<String, Object> scoped(String id) {
    auth.require("workflows:read");
    return db.one(
        "SELECT * FROM workflow_instance WHERE id=? AND team_id=?", id, auth.current().teamId());
  }

  @Transactional
  public String analyze(String mail) {
    auth.require("mails:analyze");
    db.one("SELECT id FROM mail_message WHERE id=? AND team_id=?", mail, auth.current().teamId());
    return engine.startInbound(mail);
  }

  public List<Map<String, Object>> list() {
    auth.require("workflows:read");
    return db.rows(
        "SELECT w.id,w.mail_id,w.state,w.intent,w.error_code,w.version,w.created_at,m.subject FROM workflow_instance w JOIN mail_message m ON m.id=w.mail_id WHERE w.team_id=? ORDER BY w.created_at DESC LIMIT 100",
        auth.current().teamId());
  }

  public Map<String, Object> detail(String id) {
    var w = scoped(id);
    Map<String, Object> view = new LinkedHashMap<>();
    for (String k :
        List.of(
            "id",
            "mail_id",
            "state",
            "intent",
            "error_code",
            "version",
            "created_at",
            "updated_at",
            "deadline")) view.put(k, w.get(k));
    var ps =
        db.rows(
            "SELECT id,action_type,target_id,risk_level,proposal_version,snapshot_cipher,digest,created_at FROM agent_proposal WHERE workflow_id=? ORDER BY proposal_version",
            id);
    view.put(
        "proposals",
        ps.stream()
            .map(
                p -> {
                  var v = new LinkedHashMap<>(p);
                  v.put(
                      "snapshot",
                      redact(Json.map(crypto.decrypt(v.remove("snapshot_cipher").toString()))));
                  return v;
                })
            .toList());
    view.put("events", events(id));
    view.put("toolCalls", tools(id));
    view.put(
        "executions",
        db.rows(
            "SELECT id,approval_id,action_type,status,provider_request_id,result_json,version,created_at FROM operation_execution WHERE workflow_id=? ORDER BY created_at",
            id));
    view.put(
        "approvals",
        db.rows(
            "SELECT id,proposal_id,action_type,target_id,status,required_role,expires_at,version,created_at FROM approval_request WHERE workflow_id=? ORDER BY created_at",
            id));
    view.put(
        "tasks",
        db.rows(
            "SELECT id,task_type,status,attempts,next_run_at,created_at FROM workflow_task WHERE workflow_id=? ORDER BY created_at",
            id));
    return view;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> redact(Map<String, Object> snapshot) {
    var v = new LinkedHashMap<>(snapshot);
    if (v.get("parameters") instanceof Map<?, ?> p) {
      var q = new LinkedHashMap<>((Map<String, Object>) p);
      if (q.containsKey("to")) q.put("to", "***@***");
      if (q.containsKey("address")) q.put("address", "[收货地址已加密保存]");
      v.put("parameters", q);
    }
    return v;
  }

  public List<Map<String, Object>> tools(String id) {
    scoped(id);
    return db.rows(
        "SELECT call_id,name,arguments_json,result_json,status,error_code,duration_ms,created_at FROM agent_tool_call WHERE workflow_id=? ORDER BY created_at",
        id);
  }

  public List<Map<String, Object>> events(String id) {
    scoped(id);
    return db.rows(
        "SELECT id,from_state,to_state,summary,created_at FROM workflow_transition_log WHERE workflow_id=? ORDER BY created_at,id",
        id);
  }

  public List<Map<String, Object>> approvals() {
    auth.require("workflows:read");
    return approval.list(auth.current().teamId());
  }

  @Transactional
  public void decide(String id, int version, String decision, String comment) {
    auth.require("approvals:decide");
    var a = approval.decide(id, auth.current(), version, decision, comment);
    String workflow = a.get("workflow_id").toString();
    scoped(workflow);
    states.move(
        workflow,
        decision.equals("APPROVE")
            ? "APPROVED"
            : decision.equals("REJECT") ? "REJECTED" : "MANUAL_REVIEW",
        "人工审批：" + decision);
    if (decision.equals("APPROVE")) engine.enqueue(workflow, "EXECUTE", Map.of("approvalId", id));
  }

  @Transactional
  public void retry(String id) {
    auth.require("workflows:retry");
    var w = scoped(id);
    if (!Set.of("RETRY_PENDING", "MANUAL_REVIEW").contains(w.get("state")))
      throw BusinessException.conflict("当前状态不可重试");
    if (db.count(
            "SELECT COUNT(*) FROM operation_execution WHERE workflow_id=? AND status IN ('DISPATCHING','UNKNOWN','PREPARED')",
            id)
        > 0) {
      engine.enqueue(id, "RECONCILE", Map.of());
      return;
    }
    if (!w.get("state").equals("RETRY_PENDING"))
      throw BusinessException.conflict("需修订建议后重新审批，不能重放写操作");
    db.update(
        "UPDATE workflow_task SET next_run_at=UTC_TIMESTAMP(6) WHERE workflow_id=? AND status='PENDING'",
        id);
  }

  @Transactional
  public void revise(
      String id, String action, String order, Map<String, Object> params, String reason) {
    auth.require("mails:analyze");
    var w = scoped(id);
    if (!"MANUAL_REVIEW".equals(w.get("state"))
        || db.count(
                "SELECT COUNT(*) FROM operation_execution WHERE workflow_id=? AND status IN ('PREPARED','DISPATCHING','UNKNOWN')",
                id)
            > 0) throw BusinessException.conflict("当前不能修订，必须先核对未决操作");
    var c = engine.context(id);
    var p =
        proposals.generate(
            c,
            Json.write(
                Map.of(
                    "actionType",
                    action,
                    "orderId",
                    order,
                    "parameters",
                    params,
                    "reason",
                    reason)),
            engine.nextVersion(id));
    states.move(id, "GENERATING_PROPOSAL", "人工修订操作建议");
    engine.saveProposal(c, p, auth.current().id());
    db.update("UPDATE failed_task SET resolved=TRUE WHERE workflow_id=?", id);
  }

  public List<Map<String, Object>> failures() {
    auth.require("workflows:read");
    return db.rows(
        "SELECT id,workflow_id,error_code,summary,resolved,created_at FROM failed_task WHERE team_id=? ORDER BY created_at DESC LIMIT 100",
        auth.current().teamId());
  }

  public Map<String, Object> dashboard() {
    auth.require("workflows:read");
    String team = auth.current().teamId();
    return Map.of(
        "workflowStates",
        db.rows(
            "SELECT state,COUNT(*) AS count FROM workflow_instance WHERE team_id=? GROUP BY state",
            team),
        "mailCount",
        db.count("SELECT COUNT(*) FROM mail_message WHERE team_id=?", team),
        "pendingApprovals",
        db.count(
            "SELECT COUNT(*) FROM approval_request WHERE team_id=? AND status='PENDING'", team),
        "failedCount",
        db.count("SELECT COUNT(*) FROM failed_task WHERE team_id=? AND resolved=FALSE", team),
        "recent",
        list());
  }

  public String proposalWorkflow(String proposalId) {
    auth.require("workflows:read");
    return db.one(
            "SELECT p.workflow_id FROM agent_proposal p JOIN workflow_instance w ON p.workflow_id=w.id WHERE p.id=? AND w.team_id=?",
            proposalId,
            auth.current().teamId())
        .get("workflow_id")
        .toString();
  }

  public void reconcile(
      String workflow, String execution, int expectedVersion, String outcome, String evidence) {
    auth.require("workflows:retry");
    scoped(workflow);
    executions.manualReconcile(workflow, execution, expectedVersion, outcome, evidence);
  }

  @Transactional
  public void cancel(String id, int expectedVersion, String reason) {
    auth.require("workflows:retry");
    scoped(id);
    var workflow =
        db.one("SELECT state,version,mail_id FROM workflow_instance WHERE id=? FOR UPDATE", id);
    if (((Number) workflow.get("version")).intValue() != expectedVersion
        || !Set.of("RECEIVED", "WAITING_APPROVAL", "RETRY_PENDING", "MANUAL_REVIEW")
            .contains(workflow.get("state"))) throw BusinessException.conflict("当前状态或版本不能取消");
    if (db.count(
            "SELECT COUNT(*) FROM operation_execution WHERE workflow_id=? AND status IN ('UNKNOWN','DISPATCHING','PREPARED')",
            id)
        > 0) throw BusinessException.conflict("必须先核对未决的外部操作");
    states.move(id, "CANCELLED", "人工终止工作流；原因摘要=" + Json.hash(reason));
    db.update(
        "UPDATE workflow_task SET status='CANCELLED',fencing_token=fencing_token+1,lease_until=NULL WHERE workflow_id=? AND status IN ('PENDING','RUNNING')",
        id);
    db.update(
        "UPDATE approval_request SET status='CANCELLED',version=version+1 WHERE workflow_id=? AND status='PENDING'",
        id);
    db.update(
        "UPDATE mail_message SET status='CANCELLED',version=version+1 WHERE id=?",
        workflow.get("mail_id"));
  }
}
