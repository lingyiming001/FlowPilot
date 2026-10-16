package com.flowpilot.audit.mapper;

@org.springframework.stereotype.Repository
public class AuditMapper extends com.flowpilot.common.mapper.SqlMapper {
  public AuditMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
