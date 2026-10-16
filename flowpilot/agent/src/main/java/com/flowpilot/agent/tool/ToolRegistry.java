package com.flowpilot.agent.tool;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.Json;
import java.util.*;
import org.springframework.ai.tool.*;
import org.springframework.ai.tool.definition.*;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
  public record Definition(String name, boolean readOnly, String parameter, String description) {}

  private final List<Definition> definitions =
      List.of(
          new Definition("getOrderById", true, "orderId", "查询当前已验证客户订单"),
          new Definition("getOrderByEmail", true, "email", "根据已验证客户邮箱查询其订单"),
          new Definition("getLogisticsInfo", true, "orderId", "查询当前客户订单物流"),
          new Definition("getCustomerInfo", true, "customerId", "查询当前已验证客户信息"),
          new Definition("checkRefundEligibility", true, "orderId", "检查当前客户订单退款资格"),
          new Definition("requestRefund", false, "orderId", "退款，需要主管审批"),
          new Definition("cancelOrder", false, "orderId", "取消，需要主管审批"),
          new Definition("updateShippingAddress", false, "orderId", "修改地址，需要主管审批"),
          new Definition("sendCustomerEmail", false, "customerId", "发送邮件，需要单独审批"));

  public List<Definition> all() {
    return definitions;
  }

  public Definition read(String name) {
    return definitions.stream()
        .filter(d -> d.name().equals(name) && d.readOnly())
        .findFirst()
        .orElseThrow(() -> BusinessException.invalid("模型请求未授权工具"));
  }

  public List<ToolCallback> callbacks() {
    return definitions.stream()
        .filter(Definition::readOnly)
        .map(
            d ->
                (ToolCallback)
                    new ToolCallback() {
                      public ToolDefinition getToolDefinition() {
                        return ToolDefinition.builder()
                            .name(d.name())
                            .description(d.description())
                            .inputSchema(
                                Json.write(
                                    Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of(
                                            d.parameter(),
                                            Map.of("type", "string", "maxLength", 200)),
                                        "required",
                                        List.of(d.parameter()),
                                        "additionalProperties",
                                        false)))
                            .build();
                      }

                      public String call(String input) {
                        throw BusinessException.denied();
                      }
                    })
        .toList();
  }
}
