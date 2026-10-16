package com.flowpilot.security.mapper;

@org.springframework.stereotype.Repository
public class SecurityMapper extends com.flowpilot.common.mapper.SqlMapper {
  public SecurityMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
