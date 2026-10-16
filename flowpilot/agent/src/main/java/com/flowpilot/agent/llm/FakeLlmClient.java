package com.flowpilot.agent.llm;

import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.common.util.Json;
import java.util.*;
import org.springframework.stereotype.Service;

/** Deterministic fixture provider. No natural-language intent classification. */
@Service
public class FakeLlmClient implements LlmClient {
  public String mode() {
    return "FAKE";
  }

  public Reply call(AgentContext c) {
    boolean refund = c.mailId().equals("demo-refund");
    boolean query = c.mailId().equals("demo-query");
    if (!refund && !query)
      return new Reply(
          Json.write(
              Map.of(
                  "intent",
                  "OTHER",
                  "actionType",
                  "MANUAL_REVIEW",
                  "orderId",
                  "",
                  "parameters",
                  Map.of(),
                  "reason",
                  "Fake LLM 仅支持预置演示邮件；请启用真实模型")),
          List.of());
    String id = refund ? "ORD20260002" : "ORD20260001";
    if (c.round() == 0)
      return new Reply(
          "查询订单归属与状态",
          List.of(
              new AgentContext.Call(
                  "fixture-order", "getOrderById", Json.write(Map.of("orderId", id)))));
    if (c.round() == 1)
      return new Reply(
          refund ? "校验退款资格" : "查询物流数据",
          List.of(
              new AgentContext.Call(
                  "fixture-detail",
                  refund ? "checkRefundEligibility" : "getLogisticsInfo",
                  Json.write(Map.of("orderId", id)))));
    return new Reply(
        Json.write(
            Map.of(
                "intent",
                refund ? "REFUND_REQUEST" : "ORDER_STATUS_QUERY",
                "orderId",
                id,
                "actionType",
                refund ? "requestRefund" : "sendCustomerEmail",
                "parameters",
                Map.of(
                    "body",
                    refund ? "已核对订单与退款资格，建议按实付金额退款。" : "您好，订单物流正在运输中，已到达上海转运中心。",
                    "subject",
                    "关于订单 " + id),
                "reason",
                refund ? "订单状态与退款资格已由工具核验；执行前重新校验" : "订单归属及物流信息已由工具核验")),
        List.of());
  }
}
