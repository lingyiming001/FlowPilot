package com.flowpilot.integration.mapper;

@org.springframework.stereotype.Repository
public class IntegrationMapper extends com.flowpilot.common.mapper.SqlMapper {
  public IntegrationMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
