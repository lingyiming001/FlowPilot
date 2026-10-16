package com.flowpilot.app;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.workflow.mapper.WorkflowMapper;
import com.flowpilot.workflow.scheduler.WorkflowTaskScheduler;
import com.flowpilot.workflow.service.WorkflowApplicationService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ApplicationRestartTest {
  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withCommand(
              "--log-bin-trust-function-creators=1",
              "--innodb-buffer-pool-size=32M",
              "--performance-schema=OFF",
              "--max-connections=30",
              "--mysqlx=0");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(FlowPilotApplication.class)
        .run(
            "--server.port=0",
            "--spring.datasource.url="
                + mysql.getJdbcUrl()
                + (mysql.getJdbcUrl().contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC",
            "--spring.datasource.username=" + mysql.getUsername(),
            "--spring.datasource.password=" + mysql.getPassword(),
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            "--flowpilot.encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]),
            "--flowpilot.jwt-secret=" + Base64.getEncoder().encodeToString(new byte[32]),
            "--flowpilot.demo-enabled=true",
            "--flowpilot.demo-password=restart-integration-test",
            "--flowpilot.llm-mode=fake",
            "--flowpilot.integration-mode=mock",
            "--flowpilot.scheduler-enabled=false");
  }

  void asOperator() {
    var jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("operator")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @Test
  void approvalSurvivesFullContextShutdownAndResumesOnce() {
    String workflow, approval;
    try (var first = start()) {
      asOperator();
      var service = first.getBean(WorkflowApplicationService.class);
      workflow = service.analyze("demo-query");
      var scheduler = first.getBean(WorkflowTaskScheduler.class);
      for (int i = 0; i < 8 && scheduler.runOne(); i++) {}
      var db = first.getBean(WorkflowMapper.class);
      assertEquals(
          "WAITING_APPROVAL",
          db.one("SELECT state FROM workflow_instance WHERE id=?", workflow).get("state"));
      approval =
          db.one("SELECT id FROM approval_request WHERE workflow_id=?", workflow)
              .get("id")
              .toString();
      assertEquals(0, db.count("SELECT COUNT(*) FROM operation_execution"));
    } finally {
      SecurityContextHolder.clearContext();
    }
    try (var second = start()) {
      asOperator();
      var db = second.getBean(WorkflowMapper.class);
      assertEquals(
          "WAITING_APPROVAL",
          db.one("SELECT state FROM workflow_instance WHERE id=?", workflow).get("state"));
      second.getBean(WorkflowApplicationService.class).decide(approval, 0, "APPROVE", "重启后确认原操作快照");
      var scheduler = second.getBean(WorkflowTaskScheduler.class);
      for (int i = 0; i < 8 && scheduler.runOne(); i++) {}
      assertEquals(
          "COMPLETED",
          db.one("SELECT state FROM workflow_instance WHERE id=?", workflow).get("state"));
      assertEquals(
          1, db.count("SELECT COUNT(*) FROM operation_execution WHERE approval_id=?", approval));
      assertEquals(
          2, db.count("SELECT COUNT(*) FROM agent_tool_call WHERE workflow_id=?", workflow));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
