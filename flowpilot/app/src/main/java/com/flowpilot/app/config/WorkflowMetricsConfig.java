package com.flowpilot.app.config;

import com.flowpilot.workflow.mapper.WorkflowMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowMetricsConfig {
  public WorkflowMetricsConfig(MeterRegistry registry, WorkflowMapper db) {
    Gauge.builder(
            "flowpilot.tasks.pending",
            db,
            m -> m.count("SELECT COUNT(*) FROM workflow_task WHERE status='PENDING'"))
        .register(registry);
    Gauge.builder(
            "flowpilot.operations.unknown",
            db,
            m -> m.count("SELECT COUNT(*) FROM operation_execution WHERE status='UNKNOWN'"))
        .register(registry);
    Gauge.builder(
            "flowpilot.tasks.failed",
            db,
            m -> m.count("SELECT COUNT(*) FROM failed_task WHERE resolved=FALSE"))
        .register(registry);
    Gauge.builder(
            "flowpilot.approvals.pending",
            db,
            m -> m.count("SELECT COUNT(*) FROM approval_request WHERE status='PENDING'"))
        .register(registry);
  }
}
