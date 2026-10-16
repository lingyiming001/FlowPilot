package com.flowpilot.approval.mapper;

@org.springframework.stereotype.Repository
public class ApprovalMapper extends com.flowpilot.common.mapper.SqlMapper {
  public ApprovalMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
