package com.flowpilot.agent.context;

import java.util.*;

public record AgentContext(
    String workflowId,
    String mailId,
    String teamId,
    String customerId,
    String body,
    int round,
    List<Turn> history) {
  public record Call(String id, String name, String arguments) {}

  public record Turn(String role, String text, List<Call> calls, String callId, String toolName) {}
}
