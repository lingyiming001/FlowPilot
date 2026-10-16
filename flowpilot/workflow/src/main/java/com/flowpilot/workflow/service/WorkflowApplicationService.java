package com.flowpilot.workflow.service;

import java.util.*;

public interface WorkflowApplicationService {
  String analyze(String mail);

  List<Map<String, Object>> list();

  Map<String, Object> detail(String id);

  List<Map<String, Object>> tools(String id);

  List<Map<String, Object>> events(String id);

  List<Map<String, Object>> approvals();

  void decide(String approval, int version, String decision, String comment);

  void retry(String id);

  void revise(String id, String action, String orderId, Map<String, Object> params, String reason);

  List<Map<String, Object>> failures();

  Map<String, Object> dashboard();

  String proposalWorkflow(String proposalId);

  void reconcile(
      String workflow, String execution, int expectedVersion, String outcome, String evidence);

  void cancel(String id, int expectedVersion, String reason);
}
