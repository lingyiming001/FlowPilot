package com.flowpilot.security.service;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.security.dto.Actor;
import org.springframework.stereotype.Service;

@Service
public class DataScopeService {
  public void check(Actor actor, String teamId) {
    if (!actor.teamId().equals(teamId)) throw BusinessException.denied();
  }
}
