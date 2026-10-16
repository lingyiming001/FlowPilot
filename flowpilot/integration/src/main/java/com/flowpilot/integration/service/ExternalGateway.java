package com.flowpilot.integration.service;

import java.util.Map;

public interface ExternalGateway {
  record Result(String status, String requestId, Map<String, Object> data) {}

  Result write(String action, String key, Map<String, Object> parameters);

  Result lookup(String action, String key);

  Map<String, Object> logistics(String orderId);
}
