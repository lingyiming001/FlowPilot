package com.flowpilot.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.util.*;

public final class Json {
  public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  public static Map<String, Object> map(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<TreeMap<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  public static <T> T read(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String canonical(Object value) {
    return write(MAPPER.convertValue(value, new TypeReference<TreeMap<String, Object>>() {}));
  }
}
