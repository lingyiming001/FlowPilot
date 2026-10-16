package com.flowpilot.workflow.scheduler;

import com.flowpilot.workflow.engine.WorkflowEngine;
import com.flowpilot.workflow.mapper.WorkflowMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowpilot.auto-analyze-enabled", havingValue = "true")
public class InboundWorkflowScheduler {
  private final WorkflowMapper db;
  private final WorkflowEngine engine;

  public InboundWorkflowScheduler(WorkflowMapper db, WorkflowEngine engine) {
    this.db = db;
    this.engine = engine;
  }

  @Scheduled(fixedDelay = 10000)
  public void scan() {
    for (var m :
        db.rows(
            "SELECT m.id FROM mail_message m LEFT JOIN workflow_instance w ON w.mail_id=m.id WHERE m.identity_verified=TRUE AND m.status='RECEIVED' AND w.id IS NULL ORDER BY m.created_at LIMIT 20")) {
      try {
        engine.startInbound(m.get("id").toString());
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Inbound workflow creation failed type={}", e.getClass().getSimpleName());
      }
    }
  }
}
