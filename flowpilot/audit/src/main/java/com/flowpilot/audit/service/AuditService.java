package com.flowpilot.audit.service;

import java.util.*;

public interface AuditService {
  void record(String team, String workflow, String actor, String event, String summary);

  List<Map<String, Object>> list(String team, int page);
}
