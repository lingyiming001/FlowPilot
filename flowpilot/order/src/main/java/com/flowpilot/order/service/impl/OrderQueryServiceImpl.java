package com.flowpilot.order.service.impl;

import com.flowpilot.order.mapper.OrderMapper;
import com.flowpilot.order.service.OrderQueryService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {
  private final OrderMapper db;
  private final com.flowpilot.order.mapper.CustomerOrderMapper entities;

  public OrderQueryServiceImpl(
      OrderMapper db, com.flowpilot.order.mapper.CustomerOrderMapper entities) {
    this.db = db;
    this.entities = entities;
  }

  private static final String SELECT =
      "SELECT id,customer_id,team_id,status,total,currency,product_name,version,created_at FROM customer_order";

  public Map<String, Object> get(String id, String team, String customer) {
    var e =
        entities.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                    com.flowpilot.order.entity.CustomerOrderEntity>()
                .eq("id", id)
                .eq("team_id", team)
                .eq("customer_id", customer));
    if (e == null) throw com.flowpilot.common.exception.BusinessException.missing();
    return Map.of(
        "id",
        e.id,
        "team_id",
        e.teamId,
        "customer_id",
        e.customerId,
        "status",
        e.status,
        "total",
        e.total,
        "currency",
        e.currency,
        "product_name",
        e.productName,
        "version",
        e.version);
  }

  public List<Map<String, Object>> list(String team) {
    return db.rows(SELECT + " WHERE team_id=? ORDER BY created_at DESC LIMIT 100", team);
  }

  public List<Map<String, Object>> byCustomer(String team, String customer) {
    return db.rows(SELECT + " WHERE team_id=? AND customer_id=? LIMIT 100", team, customer);
  }

  public Map<String, Object> eligibility(String id, String team, String customer) {
    var o = get(id, team, customer);
    boolean allowed = Set.of("PAID", "SHIPPED", "DELIVERED").contains(o.get("status").toString());
    return Map.of(
        "eligible",
        allowed,
        "amount",
        o.get("total"),
        "currency",
        o.get("currency"),
        "reason",
        allowed ? "订单状态允许退款，执行前需重新校验" : "当前订单状态不允许退款",
        "orderVersion",
        o.get("version"));
  }

  public Map<String, Object> findForTeam(String id, String team) {
    return db.one(SELECT + " WHERE id=? AND team_id=?", id, team);
  }
}
