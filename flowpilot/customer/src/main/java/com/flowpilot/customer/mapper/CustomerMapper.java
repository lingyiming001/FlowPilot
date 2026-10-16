package com.flowpilot.customer.mapper;

@org.springframework.stereotype.Repository
public class CustomerMapper extends com.flowpilot.common.mapper.SqlMapper {
  public CustomerMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
