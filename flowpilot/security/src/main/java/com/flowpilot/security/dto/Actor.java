package com.flowpilot.security.dto;

import java.util.Set;

public record Actor(
    String id,
    String username,
    String displayName,
    String teamId,
    Set<String> roles,
    Set<String> permissions) {
  public boolean has(String permission) {
    return permissions.contains(permission);
  }

  public boolean role(String role) {
    return roles.contains(role);
  }
}
