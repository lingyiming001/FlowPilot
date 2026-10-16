package com.flowpilot.agent.service;

import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.agent.dto.ProposedAction;
import com.flowpilot.agent.mapper.AgentMapper;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.Json;
import com.flowpilot.customer.service.CustomerQueryService;
import com.flowpilot.order.service.OrderQueryService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProposalGenerator {
  private final OrderQueryService orders;
  private final CustomerQueryService customers;
  private final AgentMapper db;

  public ProposalGenerator(
      OrderQueryService orders, CustomerQueryService customers, AgentMapper db) {
    this.orders = orders;
    this.customers = customers;
    this.db = db;
  }

  @SuppressWarnings("unchecked")
  public ProposedAction generate(AgentContext c, String text, int version) {
    var m = Json.map(text);
    String action = String.valueOf(m.get("actionType"));
    if (!Set.of("requestRefund", "cancelOrder", "updateShippingAddress", "sendCustomerEmail")
        .contains(action)) throw BusinessException.invalid("需要人工处理该建议");
    String order = String.valueOf(m.get("orderId"));
    var o = orders.get(order, c.teamId(), c.customerId());
    if (db.count(
            "SELECT COUNT(*) FROM agent_tool_call WHERE workflow_id=? AND name='getOrderById' AND status='SUCCESS' AND JSON_UNQUOTE(JSON_EXTRACT(arguments_json,'$.orderId'))=?",
            c.workflowId(),
            order)
        == 0) throw BusinessException.invalid("缺少真实订单查询依据");
    if (action.equals("requestRefund")
        && !Boolean.TRUE.equals(
            orders.eligibility(order, c.teamId(), c.customerId()).get("eligible")))
      throw BusinessException.invalid("订单不满足退款规则");
    Map<String, Object> raw =
        m.get("parameters") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    Map<String, Object> params = new TreeMap<>();
    params.put("orderId", order);
    params.put("orderVersion", o.get("version"));
    if (action.equals("requestRefund")) {
      params.put("amount", o.get("total"));
      params.put("currency", o.get("currency"));
    }
    if (action.equals("updateShippingAddress")) {
      String address = String.valueOf(raw.getOrDefault("address", ""));
      if (address.isBlank() || address.length() > 500) throw BusinessException.invalid("地址无效");
      params.put("address", address);
    }
    if (action.equals("sendCustomerEmail")) {
      String body = String.valueOf(raw.getOrDefault("body", ""));
      if (body.isBlank() || body.length() > 10000) throw BusinessException.invalid("邮件草稿无效");
      params.put("to", customers.email(c.customerId(), c.teamId()));
      params.put("subject", String.valueOf(raw.getOrDefault("subject", "关于订单 " + order)));
      params.put("body", body);
    }
    String risk = action.equals("sendCustomerEmail") ? "LOW" : "HIGH";
    return new ProposedAction(
        action,
        action.equals("sendCustomerEmail") ? c.customerId() : order,
        params,
        risk,
        String.valueOf(m.getOrDefault("reason", "基于工具结果提出建议")),
        action.equals("sendCustomerEmail") ? "发送经审核的客户通知" : "更新订单业务状态",
        risk.equals("HIGH") ? "SUPERVISOR" : "AGENT_OPERATOR",
        Instant.now().plusSeconds(86400).toString(),
        version);
  }
}
