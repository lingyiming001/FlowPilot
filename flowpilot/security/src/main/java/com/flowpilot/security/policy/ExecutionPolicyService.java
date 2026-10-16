package com.flowpilot.security.policy;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.security.dto.Actor;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ExecutionPolicyService {
  public void validate(
      Actor actor,
      String team,
      String requiredRole,
      String digest,
      String actualDigest,
      Instant expires,
      String status) {
    if (!actor.teamId().equals(team)
        || !(actor.role(requiredRole)
            || ("AGENT_OPERATOR".equals(requiredRole) && actor.role("SUPERVISOR")))
        || !actor.has("approvals:decide")
        || !digest.equals(actualDigest)
        || !"APPROVED".equals(status)
        || !expires.isAfter(Instant.now())) throw BusinessException.denied();
  }
}
