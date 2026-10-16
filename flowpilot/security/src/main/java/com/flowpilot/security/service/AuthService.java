package com.flowpilot.security.service;

import com.flowpilot.security.dto.Actor;
import java.util.*;

public interface AuthService {
  Map<String, Object> login(String username, String password);

  Map<String, Object> issueAccess(Actor actor, String familyId);

  Actor current();

  Actor load(String id);

  void require(String permission);

  List<Map<String, Object>> users();

  List<Map<String, Object>> roles();

  void updateUser(String id, boolean enabled, List<String> roles);

  String createUser(String username, String password, String displayName, List<String> roles);
}
