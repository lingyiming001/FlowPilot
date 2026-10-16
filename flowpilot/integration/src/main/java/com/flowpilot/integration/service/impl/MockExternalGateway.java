package com.flowpilot.integration.service.impl;

import com.flowpilot.common.util.Json;
import com.flowpilot.integration.mapper.IntegrationMapper;
import com.flowpilot.integration.service.ExternalGateway;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "flowpilot.integration-mode",
    havingValue = "mock",
    matchIfMissing = true)
public class MockExternalGateway implements ExternalGateway {
  private final IntegrationMapper db;

  public MockExternalGateway(IntegrationMapper db) {
    this.db = db;
  }

  public Result write(String action, String key, Map<String, Object> p) {
    String status = String.valueOf(p.getOrDefault("simulate", "SUCCESS"));
    if (!Set.of("SUCCESS", "FAILED", "UNKNOWN").contains(status)) status = "SUCCESS";
    var data =
        Map.<String, Object>of(
            "mode", "MOCK", "action", action, "confirmed", status.equals("SUCCESS"));
    db.update(
        "INSERT IGNORE INTO integration_request_log(id,idempotency_key,adapter,operation,status,result_json) VALUES(?,?,?,?,?,?)",
        Json.id(),
        key,
        "mock",
        action,
        status,
        Json.write(data));
    return lookup(action, key);
  }

  public Result lookup(String action, String key) {
    var r =
        db.rows(
            "SELECT * FROM integration_request_log WHERE adapter='mock' AND idempotency_key=?",
            key);
    if (r.isEmpty()) return new Result("NOT_FOUND", key, Map.of());
    var v = r.getFirst();
    return new Result(v.get("status").toString(), key, Json.map(v.get("result_json").toString()));
  }

  public Map<String, Object> logistics(String id) {
    return Map.of(
        "orderId",
        id,
        "carrier",
        "顺丰速运 · Mock",
        "trackingNo",
        "SF-DEMO-2026",
        "status",
        "运输中",
        "events",
        List.of("已揽收", "到达上海转运中心"),
        "mode",
        "MOCK");
  }
}
