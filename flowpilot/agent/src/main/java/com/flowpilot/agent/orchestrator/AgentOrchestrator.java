package com.flowpilot.agent.orchestrator;

import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.agent.llm.LlmClient;
import com.flowpilot.agent.mapper.AgentMapper;
import com.flowpilot.common.util.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {
  private final LlmClient client;
  private final AgentMapper db;
  private final Crypto crypto;

  public AgentOrchestrator(LlmClient client, AgentMapper db, Crypto crypto) {
    this.client = client;
    this.db = db;
    this.crypto = crypto;
  }

  public LlmClient.Reply next(AgentContext c) {
    var session = db.rows("SELECT id FROM agent_session WHERE workflow_id=?", c.workflowId());
    if (session.isEmpty())
      db.update(
          "INSERT IGNORE INTO agent_session(id,workflow_id,provider,prompt_version) VALUES(?,?,?,?)",
          Json.id(),
          c.workflowId(),
          client.mode(c.teamId()),
          "v1");
    return client.call(c);
  }

  public String mode(String team) {
    return client.mode(team);
  }

  public String mode() {
    return client.mode();
  }
}
