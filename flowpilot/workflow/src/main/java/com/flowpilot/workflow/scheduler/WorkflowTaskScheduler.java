package com.flowpilot.workflow.scheduler;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.Json;
import com.flowpilot.workflow.engine.WorkflowEngine;
import com.flowpilot.workflow.executor.ToolExecutionService;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.state.AgentStateManager;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WorkflowTaskScheduler {
  private final WorkflowMapper db;
  private final WorkflowEngine engine;
  private final ToolExecutionService executor;
  private final AgentStateManager states;
  private final TransactionTemplate tx;
  private final boolean enabled;
  private final String owner = UUID.randomUUID().toString();

  public WorkflowTaskScheduler(
      WorkflowMapper db,
      WorkflowEngine engine,
      ToolExecutionService executor,
      AgentStateManager states,
      TransactionTemplate tx,
      @Value("${flowpilot.scheduler-enabled:true}") boolean enabled) {
    this.db = db;
    this.engine = engine;
    this.executor = executor;
    this.states = states;
    this.tx = tx;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelay = 1000)
  public void tick() {
    if (!enabled) return;
    try {
      runOne();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .error("Scheduler failure type={}", e.getClass().getSimpleName());
    }
  }

  public boolean runOne() {
    Map<String, Object> task =
        tx.execute(
            s -> {
              var r =
                  db.rows(
                      "SELECT * FROM workflow_task WHERE status='PENDING' AND next_run_at<=UTC_TIMESTAMP(6) ORDER BY next_run_at LIMIT 1 FOR UPDATE SKIP LOCKED");
              if (r.isEmpty()) return null;
              String id = r.getFirst().get("id").toString();
              db.update(
                  "UPDATE workflow_task SET status='RUNNING',lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 120 SECOND),fencing_token=fencing_token+1 WHERE id=?",
                  owner,
                  id);
              return db.one("SELECT * FROM workflow_task WHERE id=?", id);
            });
    if (task == null) return false;
    try {
      switch (task.get("task_type").toString()) {
        case "ANALYZE" -> engine.analyze(task);
        case "EXECUTE" -> executor.execute(task);
        case "RECONCILE" -> executor.reconcile(task);
        default -> throw BusinessException.invalid("无效任务类型");
      }
    } catch (Exception e) {
      handle(task, e);
    }
    return true;
  }

  private void handle(Map<String, Object> t, Exception error) {
    tx.executeWithoutResult(
        s -> {
          try {
            engine.fence(t);
          } catch (BusinessException stale) {
            return;
          }
          String id = t.get("workflow_id").toString();
          var w = db.one("SELECT * FROM workflow_instance WHERE id=? FOR UPDATE", id);
          String state = w.get("state").toString();
          boolean retryable =
              error instanceof BusinessException b
                  ? b.retryable
                  : !(error instanceof IllegalArgumentException);
          int attempts = ((Number) t.get("attempts")).intValue() + 1;
          String code = error instanceof BusinessException b ? b.code : "TASK_ERROR";
          if (t.get("task_type").equals("ANALYZE")
              && retryable
              && attempts <= 3
              && Set.of("ANALYZING", "QUERYING_TOOLS").contains(state)) {
            states.move(id, "RETRY_PENDING", "暂时失败，已安排指数退避重试");
            db.update(
                "UPDATE workflow_task SET status='PENDING',attempts=?,lease_until=NULL,next_run_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE id=?",
                attempts,
                (1 << attempts) * 2 + new Random().nextInt(3),
                t.get("id"));
          } else {
            if (!Set.of("MANUAL_REVIEW", "COMPLETED", "FAILED", "REJECTED", "CANCELLED")
                .contains(state)) {
              if (t.get("task_type").equals("ANALYZE")
                  && retryable
                  && attempts > 3
                  && Set.of("ANALYZING", "QUERYING_TOOLS").contains(state)) {
                states.move(id, "RETRY_PENDING", "重试次数已耗尽");
                states.move(id, "FAILED", "达到最大重试次数");
              } else states.move(id, "MANUAL_REVIEW", "任务需人工接管：" + code);
            }
            db.update(
                "UPDATE workflow_task SET status='FAILED',attempts=?,lease_until=NULL WHERE id=?",
                attempts,
                t.get("id"));
            db.update(
                "INSERT INTO failed_task(id,workflow_id,task_id,team_id,error_code,summary) VALUES(?,?,?,?,?,?)",
                Json.id(),
                id,
                t.get("id"),
                w.get("team_id"),
                code,
                "任务失败，请核对工具、业务或外部执行记录");
          }
          db.update("UPDATE workflow_instance SET error_code=? WHERE id=?", code, id);
        });
  }
}
