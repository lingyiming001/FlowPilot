package com.flowpilot.approval.service;

import com.flowpilot.security.dto.Actor;
import java.util.*;

public interface ApprovalService {
  String create(
      String workflow,
      String team,
      String proposal,
      String action,
      String target,
      String digest,
      String requiredRole,
      String requestedBy,
      java.time.Instant expires);

  Map<String, Object> decide(String id, Actor actor, int version, String decision, String comment);

  List<Map<String, Object>> list(String team);

  Map<String, Object> approved(String id);
}
