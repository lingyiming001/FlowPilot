package com.flowpilot.common.mapper;

import com.flowpilot.common.exception.BusinessException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class SqlMapper {
  protected final JdbcTemplate jdbc;

  protected SqlMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    // MySQL Connector/J exposes DATETIME as LocalDateTime. Stored values are UTC,
    // so normalize explicitly rather than interpreting them in the host time zone.
    rows.forEach(
        row ->
            row.replaceAll(
                (key, value) ->
                    value instanceof java.time.LocalDateTime date
                        ? java.sql.Timestamp.from(date.toInstant(java.time.ZoneOffset.UTC))
                        : value));
    return rows;
  }

  public Map<String, Object> one(String sql, Object... args) {
    var r = rows(sql, args);
    if (r.isEmpty()) throw BusinessException.missing();
    return r.getFirst();
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }
}
