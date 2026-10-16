package com.flowpilot.security.controller;

import com.flowpilot.common.dto.ApiResponse;
import com.flowpilot.security.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService service;
  private final com.flowpilot.security.service.RememberMeService remembered;
  private final boolean secureCookie;

  public AuthController(
      AuthService service,
      com.flowpilot.security.service.RememberMeService remembered,
      @org.springframework.beans.factory.annotation.Value("${flowpilot.auth.cookie-secure:false}")
          boolean secureCookie) {
    this.service = service;
    this.remembered = remembered;
    this.secureCookie = secureCookie;
  }

  public record Login(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 200) String password,
      boolean rememberMe) {}

  public record UserChange(
      boolean enabled,
      @NotNull @Size(max = 4)
          List<@Pattern(regexp = "ADMIN|SUPERVISOR|AGENT_OPERATOR|AUDITOR") String> roles) {}

  @PostMapping("/auth/login")
  public ApiResponse<?> login(
      @Valid @RequestBody Login r,
      @CookieValue(name = "flowpilot_refresh", required = false) String cookie,
      jakarta.servlet.http.HttpServletResponse response) {
    var access = service.login(r.username(), r.password());
    var session =
        remembered.create(
            (com.flowpilot.security.dto.Actor) access.get("user"), r.rememberMe(), cookie);
    setCookie(response, session);
    return ApiResponse.ok(session.access());
  }

  private void setCookie(
      jakarta.servlet.http.HttpServletResponse response,
      com.flowpilot.security.service.RememberMeService.Session session) {
    var builder =
        org.springframework.http.ResponseCookie.from("flowpilot_refresh", session.refreshToken())
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Strict")
            .path("/api/auth");
    if (session.remembered())
      builder.maxAge(java.time.Duration.between(java.time.Instant.now(), session.expires()));
    response.addHeader("Set-Cookie", builder.build().toString());
    response.setHeader("Cache-Control", "no-store");
  }

  private void clearCookie(jakarta.servlet.http.HttpServletResponse response) {
    response.addHeader(
        "Set-Cookie",
        org.springframework.http.ResponseCookie.from("flowpilot_refresh", "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(0)
            .build()
            .toString());
  }

  private void checkSessionRequest(jakarta.servlet.http.HttpServletRequest request) {
    if (!"1".equals(request.getHeader("X-FlowPilot-Session")))
      throw com.flowpilot.common.exception.BusinessException.denied();
    String origin = request.getHeader("Origin");
    if (origin != null) {
      try {
        var uri = java.net.URI.create(origin);
        int port = uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
        if (!request.getScheme().equals(uri.getScheme())
            || !request.getServerName().equalsIgnoreCase(uri.getHost())
            || request.getServerPort() != port)
          throw com.flowpilot.common.exception.BusinessException.denied();
      } catch (IllegalArgumentException e) {
        throw com.flowpilot.common.exception.BusinessException.denied();
      }
    }
  }

  @PostMapping("/auth/refresh")
  public ApiResponse<?> refresh(
      @CookieValue(name = "flowpilot_refresh", required = false) String cookie,
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    checkSessionRequest(request);
    try {
      var session = remembered.refresh(cookie);
      setCookie(response, session);
      return ApiResponse.ok(session.access());
    } catch (com.flowpilot.common.exception.BusinessException e) {
      clearCookie(response);
      throw e;
    }
  }

  @PostMapping("/auth/logout")
  public ApiResponse<?> logout(
      @CookieValue(name = "flowpilot_refresh", required = false) String cookie,
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    checkSessionRequest(request);
    remembered.logout(cookie);
    clearCookie(response);
    return ApiResponse.ok(null);
  }

  @GetMapping("/auth/me")
  public ApiResponse<?> me() {
    return ApiResponse.ok(service.current());
  }

  @GetMapping("/users")
  public ApiResponse<?> users() {
    return ApiResponse.ok(service.users());
  }

  @GetMapping("/roles")
  public ApiResponse<?> roles() {
    return ApiResponse.ok(service.roles());
  }

  @PutMapping("/users/{id}")
  public ApiResponse<?> update(@PathVariable String id, @Valid @RequestBody UserChange r) {
    service.updateUser(id, r.enabled(), r.roles());
    return ApiResponse.ok(null);
  }

  public record CreateUser(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}") String username,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 100) String displayName,
      @NotNull @Size(min = 1, max = 4)
          List<@Pattern(regexp = "ADMIN|SUPERVISOR|AGENT_OPERATOR|AUDITOR") String> roles) {}

  @PostMapping("/users")
  public ApiResponse<?> create(@Valid @RequestBody CreateUser request) {
    return ApiResponse.ok(
        Map.of(
            "id",
            service.createUser(
                request.username(), request.password(), request.displayName(), request.roles())));
  }
}
