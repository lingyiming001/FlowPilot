package com.flowpilot.app;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.agent.tool.ToolRegistry;
import com.flowpilot.common.util.Crypto;
import com.flowpilot.security.dto.Actor;
import com.flowpilot.security.policy.ExecutionPolicyService;
import com.flowpilot.workflow.transition.WorkflowTransitionRules;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PolicyAndStateTest {
  @Test
  void cannotSkipApproval() {
    assertThrows(
        RuntimeException.class, () -> WorkflowTransitionRules.check("ANALYZING", "EXECUTING"));
    assertThrows(
        RuntimeException.class,
        () -> WorkflowTransitionRules.check("WAITING_APPROVAL", "COMPLETED"));
    WorkflowTransitionRules.check("WAITING_APPROVAL", "APPROVED");
  }

  @Test
  void terminalStateCannotRestart() {
    for (String s : List.of("COMPLETED", "REJECTED", "FAILED", "CANCELLED"))
      assertThrows(RuntimeException.class, () -> WorkflowTransitionRules.check(s, "EXECUTING"));
  }

  @Test
  void llmCannotDiscoverOrInvokeWrites() {
    var registry = new ToolRegistry();
    assertEquals(9, registry.all().size());
    assertEquals(5, registry.callbacks().size());
    for (String n :
        List.of("requestRefund", "cancelOrder", "updateShippingAddress", "sendCustomerEmail"))
      assertThrows(RuntimeException.class, () -> registry.read(n));
    assertThrows(RuntimeException.class, () -> registry.callbacks().getFirst().call("{}"));
  }

  @Test
  void authorizationSnapshotAndExpiryAreRechecked() {
    var p = new ExecutionPolicyService();
    Actor a = new Actor("a", "a", "a", "team", Set.of("SUPERVISOR"), Set.of("approvals:decide"));
    p.validate(a, "team", "SUPERVISOR", "hash", "hash", Instant.now().plusSeconds(100), "APPROVED");
    assertThrows(
        RuntimeException.class,
        () ->
            p.validate(
                a,
                "other",
                "SUPERVISOR",
                "hash",
                "hash",
                Instant.now().plusSeconds(100),
                "APPROVED"));
    assertThrows(
        RuntimeException.class,
        () ->
            p.validate(
                a,
                "team",
                "SUPERVISOR",
                "hash",
                "tampered",
                Instant.now().plusSeconds(100),
                "APPROVED"));
    assertThrows(
        RuntimeException.class,
        () ->
            p.validate(
                a,
                "team",
                "SUPERVISOR",
                "hash",
                "hash",
                Instant.now().minusSeconds(100),
                "APPROVED"));
    assertThrows(
        RuntimeException.class,
        () ->
            p.validate(
                a,
                "team",
                "SUPERVISOR",
                "hash",
                "hash",
                Instant.now().plusSeconds(100),
                "PENDING"));
  }

  @Test
  void sensitiveDataEncryptedAndAuthenticated() {
    Crypto c = new Crypto(Base64.getEncoder().encodeToString(new byte[32]));
    String cipher = c.encrypt("private@example.test");
    assertFalse(cipher.contains("private"));
    assertEquals("private@example.test", c.decrypt(cipher));
    assertEquals(c.identityHash("A@example.test"), c.identityHash("a@EXAMPLE.test"));
    assertThrows(RuntimeException.class, () -> c.decrypt(cipher + "tamper"));
  }
}
