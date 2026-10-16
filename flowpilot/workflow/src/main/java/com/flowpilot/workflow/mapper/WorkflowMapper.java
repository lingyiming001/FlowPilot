package com.flowpilot.workflow.mapper;

@org.springframework.stereotype.Repository
public class WorkflowMapper extends com.flowpilot.common.mapper.SqlMapper {
  public WorkflowMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
