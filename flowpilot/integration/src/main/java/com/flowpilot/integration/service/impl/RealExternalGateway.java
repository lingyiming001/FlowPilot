package com.flowpilot.integration.service.impl;

import com.flowpilot.common.util.Json;
import com.flowpilot.integration.mapper.IntegrationMapper;
import com.flowpilot.integration.service.ExternalGateway;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "flowpilot.integration-mode", havingValue = "real")
public class RealExternalGateway implements ExternalGateway {
  private final IntegrationMapper db;
  private final RestClient http;
  private final JavaMailSender sender;
  private final String token, from;

  public RealExternalGateway(
      IntegrationMapper db,
      JavaMailSender sender,
      @Value("${flowpilot.gateway-url:}") String url,
      @Value("${flowpilot.gateway-token:}") String token,
      @Value("${spring.mail.username:}") String from) {
    this.db = db;
    this.sender = sender;
    this.token = token;
    this.from = from;
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(15));
    http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  public Result write(String action, String key, Map<String, Object> params) {
    int inserted =
        db.update(
            "INSERT IGNORE INTO integration_request_log(id,idempotency_key,adapter,operation,status,result_json) VALUES(?,?, 'real',?,'DISPATCHING','{}')",
            Json.id(),
            key,
            action);
    if (inserted == 0) return lookup(action, key);
    Result result;
    try {
      if (action.equals("sendCustomerEmail")) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(params.get("to").toString());
        msg.setSubject(params.get("subject").toString());
        msg.setText(params.get("body").toString());
        sender.send(msg);
        result = new Result("SUCCESS", key, Map.of("acceptedBySmtp", true));
      } else {
        result =
            safe(
                http.post()
                    .uri("/operations/" + action)
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .body(params)
                    .retrieve()
                    .body(Result.class),
                key);
      }
    } catch (Exception e) {
      result = new Result("UNKNOWN", key, Map.of("code", "EXTERNAL_RESULT_UNKNOWN"));
    }
    db.update(
        "UPDATE integration_request_log SET status=?,result_json=? WHERE adapter='real' AND idempotency_key=?",
        result.status(),
        Json.write(result.data()),
        key);
    return result;
  }

  private Result safe(Result result, String key) {
    if (result == null) return new Result("UNKNOWN", key, Map.of());
    String status =
        Set.of("SUCCESS", "FAILED").contains(result.status()) ? result.status() : "UNKNOWN";
    return new Result(status, key, Map.of("status", status, "mode", "REAL"));
  }

  public Result lookup(String action, String key) {
    if (action.equals("sendCustomerEmail")) {
      var rows =
          db.rows(
              "SELECT status FROM integration_request_log WHERE adapter='real' AND idempotency_key=?",
              key);
      if (!rows.isEmpty() && "SUCCESS".equals(rows.getFirst().get("status")))
        return new Result("SUCCESS", key, Map.of("acceptedBySmtp", true));
      return new Result("UNKNOWN", key, Map.of("code", "SMTP_REQUIRES_MANUAL_RECONCILIATION"));
    }
    try {
      return safe(
          http.get()
              .uri("/operations/{key}", key)
              .header("Authorization", "Bearer " + token)
              .retrieve()
              .body(Result.class),
          key);
    } catch (Exception e) {
      return new Result("UNKNOWN", key, Map.of());
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> logistics(String id) {
    var raw =
        http.get()
            .uri("/logistics/{id}", id)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(Map.class);
    if (raw == null) throw new IllegalStateException("Logistics response unavailable");
    return Map.of(
        "orderId",
        id,
        "status",
        String.valueOf(raw.getOrDefault("status", "UNKNOWN")),
        "carrier",
        String.valueOf(raw.getOrDefault("carrier", "UNKNOWN")),
        "mode",
        "REAL");
  }
}
