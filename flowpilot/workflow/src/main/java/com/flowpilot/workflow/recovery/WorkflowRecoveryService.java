package com.flowpilot.workflow.recovery;

import com.flowpilot.workflow.engine.WorkflowEngine;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.state.AgentStateManager;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRecoveryService {
  private final WorkflowMapper db;
  private final WorkflowEngine engine;
  private final AgentStateManager states;

  public WorkflowRecoveryService(
      WorkflowMapper db, WorkflowEngine engine, AgentStateManager states) {
    this.db = db;
    this.engine = engine;
    this.states = states;
  }

  @Scheduled(fixedDelay = 30000)
  @Transactional
  public void recover() {
    for (var t :
        db.rows(
            "SELECT * FROM workflow_task WHERE status='RUNNING' AND lease_until<UTC_TIMESTAMP(6) FOR UPDATE SKIP LOCKED")) {
      boolean write =
          t.get("task_type").equals("EXECUTE")
              && db.count(
                      "SELECT COUNT(*) FROM operation_execution WHERE workflow_id=?",
                      t.get("workflow_id"))
                  > 0;
      db.update(
          "UPDATE workflow_task SET status='PENDING',task_type=?,lease_owner=NULL,lease_until=NULL,fencing_token=fencing_token+1,next_run_at=UTC_TIMESTAMP(6) WHERE id=?",
          write ? "RECONCILE" : t.get("task_type"),
          t.get("id"));
    }
    for (var w :
        db.rows(
            "SELECT id FROM workflow_instance WHERE state='WAITING_APPROVAL' AND deadline<UTC_TIMESTAMP(6) FOR UPDATE SKIP LOCKED")) {
      states.move(w.get("id").toString(), "MANUAL_REVIEW", "工作流审批等待超时，需重新生成建议");
      db.update(
          "UPDATE approval_request SET status='EXPIRED',version=version+1 WHERE workflow_id=? AND status='PENDING'",
          w.get("id"));
    }
  }
}
