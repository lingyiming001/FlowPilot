package com.flowpilot.app.controller;

import com.flowpilot.audit.service.AuditService;
import com.flowpilot.common.dto.ApiResponse;
import com.flowpilot.mail.service.MailService;
import com.flowpilot.order.service.OrderQueryService;
import com.flowpilot.security.service.AuthService;
import com.flowpilot.workflow.engine.WorkflowEngine;
import com.flowpilot.workflow.service.WorkflowApplicationService;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final AuthService auth;
  private final WorkflowApplicationService workflows;
  private final WorkflowEngine engine;
  private final MailService mails;
  private final OrderQueryService orders;
  private final AuditService audit;
  private final String integrationMode;

  public ApiController(
      AuthService auth,
      WorkflowApplicationService workflows,
      WorkflowEngine engine,
      MailService mails,
      OrderQueryService orders,
      AuditService audit,
      @Value("${flowpilot.integration-mode}") String integrationMode) {
    this.auth = auth;
    this.workflows = workflows;
    this.engine = engine;
    this.mails = mails;
    this.orders = orders;
    this.audit = audit;
    this.integrationMode = integrationMode;
  }

  @GetMapping("/system/info")
  public ApiResponse<?> info() {
    auth.current();
    return ApiResponse.ok(
        Map.of(
            "name",
            "FlowPilot",
            "version",
            "1.0.0",
            "llmMode",
            engine.mode(auth.current().teamId()),
            "integrationMode",
            integrationMode));
  }

  @GetMapping("/dashboard")
  public ApiResponse<?> dashboard() {
    return ApiResponse.ok(workflows.dashboard());
  }

  @GetMapping("/mails")
  public ApiResponse<?> mails() {
    auth.require("mails:read");
    return ApiResponse.ok(mails.list(auth.current().teamId()));
  }

  @GetMapping("/mails/{id}")
  public ApiResponse<?> mail(@PathVariable String id) {
    auth.require("mails:read");
    return ApiResponse.ok(mails.detail(id, auth.current().teamId()));
  }

  @PostMapping("/mails/{id}/analyze")
  public ApiResponse<?> analyze(@PathVariable String id) {
    return ApiResponse.ok(Map.of("workflowId", workflows.analyze(id)));
  }

  @GetMapping("/orders")
  public ApiResponse<?> orders() {
    auth.require("orders:read");
    return ApiResponse.ok(orders.list(auth.current().teamId()));
  }

  @GetMapping("/orders/{id}")
  public ApiResponse<?> order(@PathVariable String id) {
    auth.require("orders:read");
    return ApiResponse.ok(orders.findForTeam(id, auth.current().teamId()));
  }

  @GetMapping("/workflows")
  public ApiResponse<?> workflows() {
    return ApiResponse.ok(workflows.list());
  }

  @GetMapping("/workflows/{id}")
  public ApiResponse<?> workflow(@PathVariable String id) {
    return ApiResponse.ok(workflows.detail(id));
  }

  @GetMapping("/workflows/{id}/tool-calls")
  public ApiResponse<?> tools(@PathVariable String id) {
    return ApiResponse.ok(workflows.tools(id));
  }

  @PostMapping("/workflows/{id}/retry")
  public ApiResponse<?> retry(@PathVariable String id) {
    workflows.retry(id);
    return ApiResponse.ok(null);
  }

  public record Revision(
      @Pattern(regexp = "requestRefund|cancelOrder|updateShippingAddress|sendCustomerEmail")
          String actionType,
      @NotBlank String orderId,
      @NotNull Map<String, Object> parameters,
      @NotBlank @Size(max = 1000) String reason) {}

  @PostMapping("/workflows/{id}/revise")
  public ApiResponse<?> revise(@PathVariable String id, @Valid @RequestBody Revision r) {
    workflows.revise(id, r.actionType(), r.orderId(), r.parameters(), r.reason());
    return ApiResponse.ok(null);
  }

  @GetMapping("/approvals")
  public ApiResponse<?> approvals() {
    return ApiResponse.ok(workflows.approvals());
  }

  public record Decision(@Min(0) int expectedVersion, @NotBlank @Size(max = 1000) String comment) {}

  @PostMapping("/approvals/{id}/approve")
  public ApiResponse<?> approve(@PathVariable String id, @Valid @RequestBody Decision r) {
    workflows.decide(id, r.expectedVersion(), "APPROVE", r.comment());
    return ApiResponse.ok(null);
  }

  @PostMapping("/approvals/{id}/reject")
  public ApiResponse<?> reject(@PathVariable String id, @Valid @RequestBody Decision r) {
    workflows.decide(id, r.expectedVersion(), "REJECT", r.comment());
    return ApiResponse.ok(null);
  }

  @PostMapping("/approvals/{id}/return")
  public ApiResponse<?> back(@PathVariable String id, @Valid @RequestBody Decision r) {
    workflows.decide(id, r.expectedVersion(), "RETURN", r.comment());
    return ApiResponse.ok(null);
  }

  @GetMapping("/audit-logs")
  public ApiResponse<?> audits(@RequestParam(defaultValue = "1") @Min(1) int page) {
    auth.require("audit:read");
    return ApiResponse.ok(audit.list(auth.current().teamId(), page));
  }

  @GetMapping("/failed-tasks")
  public ApiResponse<?> failed() {
    return ApiResponse.ok(workflows.failures());
  }

  @GetMapping("/proposals/{id}")
  public ApiResponse<?> proposal(@PathVariable String id) {
    return ApiResponse.ok(Map.of("workflowId", workflows.proposalWorkflow(id)));
  }

  public record Reconciliation(
      @Min(0) int expectedVersion,
      @NotBlank @Pattern(regexp = "SUCCESS|FAILED") String outcome,
      @NotBlank @Size(min = 12, max = 1000) String evidence) {}

  @PostMapping("/workflows/{id}/executions/{executionId}/reconcile")
  public ApiResponse<?> reconcile(
      @PathVariable String id,
      @PathVariable String executionId,
      @Valid @RequestBody Reconciliation request) {
    workflows.reconcile(
        id, executionId, request.expectedVersion(), request.outcome(), request.evidence());
    return ApiResponse.ok(null);
  }

  @PostMapping("/workflows/{id}/cancel")
  public ApiResponse<?> cancel(@PathVariable String id, @Valid @RequestBody Decision request) {
    workflows.cancel(id, request.expectedVersion(), request.comment());
    return ApiResponse.ok(null);
  }
}
