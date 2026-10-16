package com.flowpilot.agent.dto;

import java.util.Map;

public record ProposedAction(
    String actionType,
    String targetId,
    Map<String, Object> parameters,
    String riskLevel,
    String reason,
    String expectedEffect,
    String approvalPolicy,
    String expirationTime,
    int proposalVersion) {}
