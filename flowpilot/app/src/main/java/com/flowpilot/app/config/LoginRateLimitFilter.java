package com.flowpilot.app.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
  private final StringRedisTemplate redis;

  public LoginRateLimitFilter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().equals("/api/auth/login") && request.getMethod().equals("POST")) {
      try {
        String key =
            "flowpilot:login:"
                + request.getRemoteAddr()
                + ":"
                + (System.currentTimeMillis() / 60000);
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) redis.expire(key, Duration.ofMinutes(2));
        if (n != null && n > 30) {
          response.setStatus(429);
          response.setContentType("application/json");
          response
              .getWriter()
              .write("{\"success\":false,\"code\":\"RATE_LIMITED\",\"message\":\"登录尝试过多，请稍后再试\"}");
          return;
        }
      } catch (org.springframework.data.redis.RedisConnectionFailureException unavailable) {
        response.setStatus(503);
        response.getWriter().write("{\"success\":false,\"code\":\"RATE_LIMIT_UNAVAILABLE\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
