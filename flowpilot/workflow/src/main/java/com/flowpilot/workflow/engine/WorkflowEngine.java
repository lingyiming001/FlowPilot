package com.flowpilot.workflow.engine;

import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.agent.dto.ProposedAction;
import com.flowpilot.agent.orchestrator.AgentOrchestrator;
import com.flowpilot.agent.service.ProposalGenerator;
import com.flowpilot.agent.tool.ToolDispatcher;
import com.flowpilot.approval.service.ApprovalService;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.state.AgentStateManager;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkflowEngine {
  private final WorkflowMapper db;
  private final AgentStateManager states;
  private final AgentOrchestrator agent;
  private final ToolDispatcher tools;
  private final ProposalGenerator proposals;
  private final ApprovalService approvals;
  private final Crypto crypto;
  private final TransactionTemplate tx;

  public WorkflowEngine(
      WorkflowMapper db,
      AgentStateManager states,
      AgentOrchestrator agent,
      ToolDispatcher tools,
      ProposalGenerator proposals,
      ApprovalService approvals,
      Crypto crypto,
      TransactionTemplate tx) {
    this.db = db;
    this.states = states;
    this.agent = agent;
    this.tools = tools;
    this.proposals = proposals;
    this.approvals = approvals;
    this.crypto = crypto;
    this.tx = tx;
  }

  @org.springframework.transaction.annotation.Transactional
  public String startInbound(String mail) {
    var m = db.one("SELECT * FROM mail_message WHERE id=? FOR UPDATE", mail);
    var prior = db.rows("SELECT id FROM workflow_instance WHERE mail_id=?", mail);
    if (!prior.isEmpty()) return prior.getFirst().get("id").toString();
    if (m.get("customer_id") == null || !Boolean.TRUE.equals(m.get("identity_verified")))
      throw BusinessException.invalid("发件身份尚未验证");
    String id = Json.id();
    db.update(
        "INSERT INTO workflow_instance(id,team_id,mail_id,customer_id,state,deadline) VALUES(?,?,?,?,'RECEIVED',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 24 HOUR))",
        id,
        m.get("team_id"),
        mail,
        m.get("customer_id"));
    db.update(
        "INSERT INTO workflow_context(id,workflow_id,context_cipher) VALUES(?,?,?)",
        Json.id(),
        id,
        crypto.encrypt(
            Json.write(
                new AgentContext(
                    id,
                    mail,
                    m.get("team_id").toString(),
                    m.get("customer_id").toString(),
                    crypto.decrypt(m.get("body_cipher").toString()),
                    0,
                    new ArrayList<>()))));
    db.update(
        "INSERT INTO workflow_transition_log(id,workflow_id,to_state,summary) VALUES(?,?,'RECEIVED','邮件接收并创建分析任务')",
        Json.id(),
        id);
    enqueue(id, "ANALYZE", Map.of());
    db.update("UPDATE mail_message SET status='PROCESSING',version=version+1 WHERE id=?", mail);
    return id;
  }

  public void enqueue(String id, String type, Map<String, Object> payload) {
    db.update(
        "INSERT INTO workflow_task(id,workflow_id,task_type,payload_json) VALUES(?,?,?,?)",
        Json.id(),
        id,
        type,
        Json.write(payload));
  }

  public AgentContext context(String id) {
    return Json.read(
        crypto.decrypt(
            db.one("SELECT context_cipher FROM workflow_context WHERE workflow_id=?", id)
                .get("context_cipher")
                .toString()),
        AgentContext.class);
  }

  public int nextVersion(String id) {
    return (int)
        db.count(
            "SELECT COALESCE(MAX(proposal_version),0)+1 FROM agent_proposal WHERE workflow_id=?",
            id);
  }

  public void saveProposal(AgentContext c, ProposedAction p, String requester) {
    String snapshot = Json.canonical(p), digest = Json.hash(snapshot), id = Json.id();
    db.update(
        "INSERT INTO agent_proposal(id,workflow_id,action_type,target_id,snapshot_cipher,digest,proposal_version,risk_level) VALUES(?,?,?,?,?,?,?,?)",
        id,
        c.workflowId(),
        p.actionType(),
        p.targetId(),
        crypto.encrypt(snapshot),
        digest,
        p.proposalVersion(),
        p.riskLevel());
    approvals.create(
        c.workflowId(),
        c.teamId(),
        id,
        p.actionType(),
        p.targetId(),
        digest,
        p.approvalPolicy(),
        requester,
        Instant.parse(p.expirationTime()));
    states.move(c.workflowId(), "WAITING_APPROVAL", "具体操作已绑定不可变快照，等待人工授权");
  }

  public void analyze(Map<String, Object> task) {
    String id = task.get("workflow_id").toString();
    AgentContext c = context(id);
    if (c.round() >= 8) throw BusinessException.invalid("模型轮数超限");
    tx.executeWithoutResult(
        s -> {
          fence(task);
          var w = db.one("SELECT state FROM workflow_instance WHERE id=? FOR UPDATE", id);
          if (Set.of("RECEIVED", "QUERYING_TOOLS", "RETRY_PENDING").contains(w.get("state")))
            states.move(id, "ANALYZING", "恢复分析轮次 " + c.round());
        });
    var stored =
        db.rows(
            "SELECT reply_cipher FROM agent_round_checkpoint WHERE workflow_id=? AND round_no=?",
            id,
            c.round());
    com.flowpilot.agent.llm.LlmClient.Reply reply;
    if (stored.isEmpty()) {
      var response = agent.next(c);
      tx.executeWithoutResult(
          s -> {
            fence(task);
            db.update(
                "INSERT IGNORE INTO agent_round_checkpoint(id,workflow_id,round_no,reply_cipher) VALUES(?,?,?,?)",
                Json.id(),
                id,
                c.round(),
                crypto.encrypt(Json.write(response)));
          });
      reply = response;
    } else
      reply =
          Json.read(
              crypto.decrypt(stored.getFirst().get("reply_cipher").toString()),
              com.flowpilot.agent.llm.LlmClient.Reply.class);
    List<AgentContext.Turn> history = new ArrayList<>(c.history());
    history.add(new AgentContext.Turn("assistant", reply.text(), reply.calls(), null, null));
    if (!reply.calls().isEmpty()) {
      if (reply.calls().size() > 5) throw BusinessException.invalid("单轮工具调用超限");
      tx.executeWithoutResult(
          s -> {
            fence(task);
            states.move(id, "QUERYING_TOOLS", "模型发出只读工具请求");
          });
      for (var call : reply.calls()) {
        String result = tools.dispatch(c, call);
        history.add(new AgentContext.Turn("tool", result, List.of(), call.id(), call.name()));
      }
      tx.executeWithoutResult(
          s -> {
            fence(task);
            persist(c, history);
            enqueue(id, "ANALYZE", Map.of());
            done(task);
          });
    } else {
      ProposedAction p = proposals.generate(c, reply.text(), nextVersion(id));
      tx.executeWithoutResult(
          s -> {
            fence(task);
            states.move(id, "GENERATING_PROPOSAL", "校验模型结构化建议与业务事实");
            db.update(
                "UPDATE workflow_instance SET intent=? WHERE id=?",
                String.valueOf(Json.map(reply.text()).get("intent")),
                id);
            persist(c, history);
            saveProposal(c, p, null);
            done(task);
          });
    }
  }

  private void persist(AgentContext c, List<AgentContext.Turn> history) {
    AgentContext next =
        new AgentContext(
            c.workflowId(),
            c.mailId(),
            c.teamId(),
            c.customerId(),
            c.body(),
            c.round() + 1,
            history);
    db.update(
        "UPDATE workflow_context SET context_cipher=?,version=version+1 WHERE workflow_id=?",
        crypto.encrypt(Json.write(next)),
        c.workflowId());
    for (int i = c.history().size(); i < history.size(); i++) {
      var turn = history.get(i);
      db.update(
          "INSERT IGNORE INTO agent_message(id,workflow_id,sequence_no,role,content_cipher) VALUES(?,?,?,?,?)",
          Json.id(),
          c.workflowId(),
          i,
          turn.role(),
          crypto.encrypt(Json.write(turn)));
    }
  }

  public void fence(Map<String, Object> task) {
    var t =
        db.one(
            "SELECT status,fencing_token,lease_until FROM workflow_task WHERE id=? FOR UPDATE",
            task.get("id"));
    if (!"RUNNING".equals(t.get("status"))
        || !t.get("fencing_token").toString().equals(task.get("fencing_token").toString())
        || ((java.sql.Timestamp) t.get("lease_until")).toInstant().isBefore(Instant.now()))
      throw BusinessException.conflict("任务租约失效");
  }

  public void done(Map<String, Object> task) {
    db.update(
        "UPDATE workflow_task SET status='DONE',lease_until=NULL WHERE id=? AND fencing_token=?",
        task.get("id"),
        task.get("fencing_token"));
  }

  public String mode(String team) {
    return agent.mode(team);
  }

  public String mode() {
    return agent.mode();
  }
}
