package com.flowpilot.order.mapper;

@org.springframework.stereotype.Repository
public class OrderMapper extends com.flowpilot.common.mapper.SqlMapper {
  public OrderMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    super(jdbc);
  }
}
