package com.flowpilot.agent.llm;

import com.flowpilot.agent.context.AgentContext;
import java.util.*;

public interface LlmClient {
  record Reply(String text, List<AgentContext.Call> calls) {}

  Reply call(AgentContext context);

  String mode();

  default String mode(String team) {
    return mode();
  }
}
