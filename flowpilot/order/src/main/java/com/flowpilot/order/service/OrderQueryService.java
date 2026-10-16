package com.flowpilot.order.service;

import java.util.*;

public interface OrderQueryService {
  Map<String, Object> get(String id, String team, String customer);

  List<Map<String, Object>> list(String team);

  List<Map<String, Object>> byCustomer(String team, String customer);

  Map<String, Object> eligibility(String id, String team, String customer);

  Map<String, Object> findForTeam(String id, String team);
}
