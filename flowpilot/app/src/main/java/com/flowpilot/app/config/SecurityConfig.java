package com.flowpilot.app.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtEncoder encoder(@Value("${flowpilot.jwt-secret}") String key) {
    return new NimbusJwtEncoder(
        new ImmutableSecret<>(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256")));
  }

  @Bean
  JwtDecoder decoder(@Value("${flowpilot.jwt-secret}") String key) {
    var d =
        NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    d.setJwtValidator(JwtValidators.createDefaultWithIssuer("flowpilot"));
    return d;
  }

  @Bean
  TransactionTemplate tx(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, LoginRateLimitFilter rate) throws Exception {
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint(
                        (q, r, e) -> {
                          r.setStatus(401);
                          r.setContentType("application/json");
                          r.getWriter()
                              .write(
                                  "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"请登录或重新认证\"}");
                        }))
        .addFilterBefore(
            rate,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .build();
  }
}
