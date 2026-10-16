package com.flowpilot.workflow.state;

import com.flowpilot.audit.service.AuditService;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.Json;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.transition.WorkflowTransitionRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentStateManager {
  private final WorkflowMapper db;
  private final AuditService audit;

  public AgentStateManager(WorkflowMapper db, AuditService audit) {
    this.db = db;
    this.audit = audit;
  }

  @Transactional
  public void move(String id, String to, String summary) {
    var w = db.one("SELECT state,version,team_id FROM workflow_instance WHERE id=? FOR UPDATE", id);
    String from = w.get("state").toString();
    if (from.equals(to)) return;
    WorkflowTransitionRules.check(from, to);
    if (db.update(
            "UPDATE workflow_instance SET state=?,version=version+1 WHERE id=? AND version=?",
            to,
            id,
            w.get("version"))
        != 1) throw BusinessException.conflict("工作流已被更新");
    db.update(
        "INSERT INTO workflow_transition_log(id,workflow_id,from_state,to_state,summary) VALUES(?,?,?,?,?)",
        Json.id(),
        id,
        from,
        to,
        summary);
    audit.record(w.get("team_id").toString(), id, "SYSTEM", "STATE_" + to, summary);
  }
}
