package com.flowpilot.mail.mapper;

@org.springframework.stereotype.Repository
public class MailMapper extends com.flowpilot.common.mapper.SqlMapper {
  public MailMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
