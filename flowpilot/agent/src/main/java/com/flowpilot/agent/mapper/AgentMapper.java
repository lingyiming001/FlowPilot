package com.flowpilot.agent.mapper;

@org.springframework.stereotype.Repository
public class AgentMapper extends com.flowpilot.common.mapper.SqlMapper {
  public AgentMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
