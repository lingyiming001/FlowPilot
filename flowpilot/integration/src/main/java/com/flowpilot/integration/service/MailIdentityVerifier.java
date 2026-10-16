package com.flowpilot.integration.service;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Verification is delegated to a trusted mail gateway, never inferred from mail body or From alone.
 */
@Service
public class MailIdentityVerifier {
  private final String url, token;
  private final RestClient client;

  public MailIdentityVerifier(
      @Value("${flowpilot.identity-verifier-url:}") String url,
      @Value("${flowpilot.gateway-token:}") String token) {
    this.url = url;
    this.token = token;
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    client = RestClient.builder().requestFactory(factory).build();
  }

  public boolean verify(String account, String messageId, String sender) {
    if (url.isBlank()) return false;
    try {
      var response =
          client
              .post()
              .uri(url)
              .header("Authorization", "Bearer " + token)
              .body(Map.of("accountId", account, "messageId", messageId, "sender", sender))
              .retrieve()
              .body(Verification.class);
      return response != null && response.verified();
    } catch (Exception e) {
      return false;
    }
  }

  public record Verification(boolean verified) {}
}
