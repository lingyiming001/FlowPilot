package com.flowpilot.workflow.executor;

import com.flowpilot.agent.dto.ProposedAction;
import com.flowpilot.approval.service.ApprovalService;
import com.flowpilot.audit.service.AuditService;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.customer.service.CustomerQueryService;
import com.flowpilot.integration.service.ExternalGateway;
import com.flowpilot.order.service.OrderOperationService;
import com.flowpilot.security.policy.ExecutionPolicyService;
import com.flowpilot.security.service.AuthService;
import com.flowpilot.workflow.engine.WorkflowEngine;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.state.AgentStateManager;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ToolExecutionService {
  private final WorkflowMapper db;
  private final WorkflowEngine engine;
  private final AgentStateManager states;
  private final ApprovalService approvals;
  private final AuthService auth;
  private final ExecutionPolicyService policy;
  private final OrderOperationService orders;
  private final CustomerQueryService customers;
  private final ExternalGateway gateway;
  private final AuditService audit;
  private final Crypto crypto;
  private final TransactionTemplate tx;

  public ToolExecutionService(
      WorkflowMapper db,
      WorkflowEngine engine,
      AgentStateManager states,
      ApprovalService approvals,
      AuthService auth,
      ExecutionPolicyService policy,
      OrderOperationService orders,
      CustomerQueryService customers,
      ExternalGateway gateway,
      AuditService audit,
      Crypto crypto,
      TransactionTemplate tx) {
    this.db = db;
    this.engine = engine;
    this.states = states;
    this.approvals = approvals;
    this.auth = auth;
    this.policy = policy;
    this.orders = orders;
    this.customers = customers;
    this.gateway = gateway;
    this.audit = audit;
    this.crypto = crypto;
    this.tx = tx;
  }

  public void execute(Map<String, Object> task) {
    String approvalId = Json.map(task.get("payload_json").toString()).get("approvalId").toString();
    var operation =
        tx.execute(
            s -> {
              engine.fence(task);
              var prior =
                  db.rows(
                      "SELECT * FROM operation_execution WHERE approval_id=? FOR UPDATE",
                      approvalId);
              if (!prior.isEmpty()) return prior.getFirst();
              var a = approvals.approved(approvalId);
              var w =
                  db.one(
                      "SELECT * FROM workflow_instance WHERE id=? FOR UPDATE",
                      a.get("workflow_id"));
              var proposal =
                  db.one("SELECT * FROM agent_proposal WHERE id=?", a.get("proposal_id"));
              String snapshot = crypto.decrypt(proposal.get("snapshot_cipher").toString());
              var actor = auth.load(a.get("actor_id").toString());
              policy.validate(
                  actor,
                  a.get("team_id").toString(),
                  a.get("required_role").toString(),
                  a.get("snapshot_digest").toString(),
                  Json.hash(snapshot),
                  ((java.sql.Timestamp) a.get("expires_at")).toInstant(),
                  a.get("status").toString());
              ProposedAction p = Json.read(snapshot, ProposedAction.class);
              if (!p.actionType().equals(a.get("action_type"))
                  || !p.targetId().equals(a.get("target_id"))) throw BusinessException.denied();
              if (!p.actionType().equals("sendCustomerEmail")) {
                var facts =
                    orders.validate(
                        p.actionType(),
                        p.targetId(),
                        w.get("team_id").toString(),
                        w.get("customer_id").toString(),
                        ((Number) p.parameters().get("orderVersion")).intValue());
                if (p.actionType().equals("requestRefund")) {
                  if (!p.parameters().containsKey("amount")
                      || !p.parameters().containsKey("currency"))
                    throw BusinessException.invalid("退款快照缺少金额或币种");
                  if (new java.math.BigDecimal(facts.get("amount").toString())
                              .compareTo(
                                  new java.math.BigDecimal(p.parameters().get("amount").toString()))
                          != 0
                      || !facts.get("currency").equals(p.parameters().get("currency")))
                    throw BusinessException.conflict("退款金额或币种已变化");
                }
              } else if (!customers
                  .email(w.get("customer_id").toString(), w.get("team_id").toString())
                  .equals(p.parameters().get("to"))) throw BusinessException.denied();
              states.move(w.get("id").toString(), "EXECUTING", "重新校验权限与业务，预登记幂等执行");
              String id = Json.id();
              db.update(
                  "INSERT INTO operation_execution(id,workflow_id,approval_id,action_type,idempotency_key,status,active_resource_key) VALUES(?,?,?,?,?,'DISPATCHING',?)",
                  id,
                  w.get("id"),
                  approvalId,
                  p.actionType(),
                  "flowpilot-" + approvalId,
                  p.actionType().equals("sendCustomerEmail") ? null : "order:" + p.targetId());
              audit.record(
                  w.get("team_id").toString(),
                  w.get("id").toString(),
                  actor.id(),
                  "EXECUTION_PREPARED",
                  "操作与审批绑定：" + p.actionType());
              var fresh = db.one("SELECT * FROM operation_execution WHERE id=?", id);
              fresh.put("_fresh", true);
              return fresh;
            });
    if (operation == null) return;
    String status = operation.get("status").toString();
    if (status.equals("SUCCESS") || status.equals("FAILED")) {
      tx.executeWithoutResult(
          s -> {
            engine.fence(task);
            engine.done(task);
          });
      return;
    }
    // A fresh execution has no provider_request_id. A recovered DISPATCHING record is reconciled,
    // never blindly reissued.
    var p = loadProposal(approvalId);
    ExternalGateway.Result result;
    if (!Boolean.TRUE.equals(operation.get("_fresh"))
        || db.count(
                "SELECT COUNT(*) FROM integration_request_log WHERE idempotency_key=?",
                operation.get("idempotency_key"))
            > 0)
      result = gateway.lookup(p.actionType(), operation.get("idempotency_key").toString());
    else
      result =
          gateway.write(
              p.actionType(), operation.get("idempotency_key").toString(), p.parameters());
    finish(operation, p, result);
    tx.executeWithoutResult(
        s -> {
          engine.fence(task);
          engine.done(task);
        });
  }

  public void manualReconcile(
      String workflow, String execution, int expectedVersion, String outcome, String evidence) {
    var actor = auth.current();
    if (!actor.role("SUPERVISOR") || !actor.has("workflows:retry"))
      throw BusinessException.denied();
    if (!Set.of("SUCCESS", "FAILED").contains(outcome)
        || evidence == null
        || evidence.trim().length() < 12
        || evidence.length() > 1000) throw BusinessException.invalid("必须提供明确结果和至少12字符的核对凭据引用");
    tx.executeWithoutResult(
        transaction -> {
          db.one(
              "SELECT id FROM workflow_instance WHERE id=? AND team_id=? FOR UPDATE",
              workflow,
              actor.teamId());
          var operation =
              db.one(
                  "SELECT * FROM operation_execution WHERE id=? AND workflow_id=? FOR UPDATE",
                  execution,
                  workflow);
          if (!Set.of("UNKNOWN", "DISPATCHING").contains(operation.get("status"))
              || ((Number) operation.get("version")).intValue() != expectedVersion)
            throw BusinessException.conflict("执行记录已变化，请刷新后核对");
          audit.record(
              actor.teamId(),
              workflow,
              actor.id(),
              "MANUAL_RECONCILIATION",
              "人工核对结果=" + outcome + "；凭据摘要=" + Json.hash(evidence));
          var proposal = loadProposal(operation.get("approval_id").toString());
          finish(
              operation,
              proposal,
              new ExternalGateway.Result(
                  outcome,
                  operation.get("idempotency_key").toString(),
                  Map.of(
                      "mode",
                      "MANUAL_RECONCILIATION",
                      "actorId",
                      actor.id(),
                      "evidenceHash",
                      Json.hash(evidence))));
        });
  }

  private ProposedAction loadProposal(String approvalId) {
    var p =
        db.one(
            "SELECT p.snapshot_cipher FROM agent_proposal p JOIN approval_request a ON a.proposal_id=p.id WHERE a.id=?",
            approvalId);
    return Json.read(crypto.decrypt(p.get("snapshot_cipher").toString()), ProposedAction.class);
  }

  public void reconcile(Map<String, Object> task) {
    String id = task.get("workflow_id").toString();
    for (var op :
        db.rows(
            "SELECT * FROM operation_execution WHERE workflow_id=? AND status IN ('DISPATCHING','UNKNOWN','PREPARED')",
            id)) {
      var p = loadProposal(op.get("approval_id").toString());
      var r = gateway.lookup(p.actionType(), op.get("idempotency_key").toString());
      finish(op, p, r);
    }
    tx.executeWithoutResult(
        s -> {
          engine.fence(task);
          engine.done(task);
        });
  }

  private void finish(Map<String, Object> op, ProposedAction p, ExternalGateway.Result result) {
    tx.executeWithoutResult(
        s -> {
          var current =
              db.one("SELECT * FROM operation_execution WHERE id=? FOR UPDATE", op.get("id"));
          if (Set.of("SUCCESS", "FAILED").contains(current.get("status"))) return;
          String id = op.get("workflow_id").toString();
          var w = db.one("SELECT * FROM workflow_instance WHERE id=? FOR UPDATE", id);
          String state = w.get("state").toString();
          if (result.status().equals("SUCCESS")) {
            db.update(
                "UPDATE operation_execution SET status='SUCCESS',provider_request_id=?,result_json=?,active_resource_key=NULL,last_checked_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=?",
                result.requestId(),
                Json.write(result.data()),
                op.get("id"));
            if (!p.actionType().equals("sendCustomerEmail"))
              orders.apply(p.actionType(), p.targetId(), op.get("id").toString(), p.parameters());
            if (state.equals(
                "MANUAL_REVIEW")) { // Reconciliation resumes from known external success without
              // resubmission.
              states.move(id, "EXECUTING", "已核对外部操作成功，恢复本地记账");
            }
            audit.record(
                w.get("team_id").toString(),
                id,
                "SYSTEM",
                "EXECUTION_SUCCESS",
                p.actionType() + " 已确认成功");
            if (p.actionType().equals("sendCustomerEmail")) {
              states.move(id, "COMPLETED", "客户邮件发送成功，业务闭环完成");
              db.update(
                  "UPDATE mail_message SET status='COMPLETED',version=version+1 WHERE id=?",
                  w.get("mail_id"));
            } else {
              states.move(id, "GENERATING_PROPOSAL", "业务操作成功，单独生成客户通知审批");
              Map<String, Object> params = new TreeMap<>();
              params.put(
                  "to",
                  customers.email(w.get("customer_id").toString(), w.get("team_id").toString()));
              params.put("subject", "订单处理结果通知");
              params.put(
                  "body", "您好，订单 " + p.targetId() + " 的" + p.actionType() + "操作已处理成功。如有疑问请联系客服。");
              params.put("orderId", p.targetId());
              var next =
                  new ProposedAction(
                      "sendCustomerEmail",
                      w.get("customer_id").toString(),
                      params,
                      "LOW",
                      "基于已确认的业务执行结果生成通知",
                      "向客户发送处理结果",
                      "AGENT_OPERATOR",
                      Instant.now().plusSeconds(86400).toString(),
                      engine.nextVersion(id));
              engine.saveProposal(engine.context(id), next, null);
            }
            db.update("UPDATE failed_task SET resolved=TRUE WHERE workflow_id=?", id);
          } else {
            String status = result.status().equals("FAILED") ? "FAILED" : "UNKNOWN";
            db.update(
                "UPDATE operation_execution SET status=?,provider_request_id=?,result_json=?,last_checked_at=UTC_TIMESTAMP(6),active_resource_key=IF(?='FAILED',NULL,active_resource_key),version=version+1 WHERE id=?",
                status,
                result.requestId(),
                Json.write(result.data()),
                status,
                op.get("id"));
            if (!state.equals("MANUAL_REVIEW"))
              states.move(
                  id,
                  "MANUAL_REVIEW",
                  status.equals("UNKNOWN") ? "外部结果未知，禁止重发，等待核对" : "外部明确失败，需人工接管");
            if (db.count(
                    "SELECT COUNT(*) FROM failed_task WHERE workflow_id=? AND resolved=FALSE AND error_code=?",
                    id,
                    "EXTERNAL_" + status)
                == 0)
              db.update(
                  "INSERT INTO failed_task(id,workflow_id,team_id,error_code,summary) VALUES(?,?,?,?,?)",
                  Json.id(),
                  id,
                  w.get("team_id"),
                  "EXTERNAL_" + status,
                  "外部操作需核对；禁止无条件重复执行");
            audit.record(
                w.get("team_id").toString(),
                id,
                "SYSTEM",
                "EXECUTION_" + status,
                p.actionType() + " 外部执行未完成确认");
          }
        });
  }
}
