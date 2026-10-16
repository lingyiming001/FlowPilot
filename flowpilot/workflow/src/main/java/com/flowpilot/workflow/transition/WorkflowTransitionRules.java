package com.flowpilot.workflow.transition;

import static com.flowpilot.workflow.enums.WorkflowState.*;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.workflow.enums.WorkflowState;
import java.util.*;

public final class WorkflowTransitionRules {
  private static final Map<WorkflowState, Set<WorkflowState>> RULES =
      Map.ofEntries(
          Map.entry(RECEIVED, Set.of(ANALYZING, MANUAL_REVIEW, CANCELLED)),
          Map.entry(
              ANALYZING, Set.of(QUERYING_TOOLS, GENERATING_PROPOSAL, RETRY_PENDING, MANUAL_REVIEW)),
          Map.entry(
              QUERYING_TOOLS, Set.of(ANALYZING, GENERATING_PROPOSAL, RETRY_PENDING, MANUAL_REVIEW)),
          Map.entry(GENERATING_PROPOSAL, Set.of(WAITING_APPROVAL, MANUAL_REVIEW)),
          Map.entry(WAITING_APPROVAL, Set.of(APPROVED, REJECTED, MANUAL_REVIEW, CANCELLED)),
          Map.entry(APPROVED, Set.of(EXECUTING, MANUAL_REVIEW)),
          Map.entry(
              EXECUTING, Set.of(COMPLETED, GENERATING_PROPOSAL, RETRY_PENDING, MANUAL_REVIEW)),
          Map.entry(RETRY_PENDING, Set.of(ANALYZING, EXECUTING, FAILED, MANUAL_REVIEW, CANCELLED)),
          Map.entry(MANUAL_REVIEW, Set.of(GENERATING_PROPOSAL, EXECUTING, CANCELLED)));

  public static void check(String from, String to) {
    if (!RULES.getOrDefault(valueOf(from), Set.of()).contains(valueOf(to)))
      throw BusinessException.conflict("非法状态迁移：" + from + " → " + to);
  }

  private static WorkflowState valueOf(String v) {
    return WorkflowState.valueOf(v);
  }
}
