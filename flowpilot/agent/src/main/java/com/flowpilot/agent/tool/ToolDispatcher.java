package com.flowpilot.agent.tool;

import com.flowpilot.agent.context.AgentContext;
import com.flowpilot.agent.mapper.AgentMapper;
import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.customer.service.CustomerQueryService;
import com.flowpilot.integration.service.ExternalGateway;
import com.flowpilot.order.service.OrderQueryService;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

@Service
public class ToolDispatcher {
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

  @jakarta.annotation.PreDestroy
  public void shutdown() {
    workers.shutdownNow();
  }

  private final ToolRegistry registry;
  private final AgentMapper db;
  private final OrderQueryService orders;
  private final CustomerQueryService customers;
  private final ExternalGateway gateway;
  private final Crypto crypto;

  public ToolDispatcher(
      ToolRegistry registry,
      AgentMapper db,
      OrderQueryService orders,
      CustomerQueryService customers,
      ExternalGateway gateway,
      Crypto crypto) {
    this.registry = registry;
    this.db = db;
    this.orders = orders;
    this.customers = customers;
    this.gateway = gateway;
    this.crypto = crypto;
  }

  public String dispatch(AgentContext c, AgentContext.Call call) {
    var d = registry.read(call.name());
    var args = Json.map(call.arguments());
    if (args.size() != 1
        || !args.containsKey(d.parameter())
        || !(args.get(d.parameter()) instanceof String s)
        || s.isBlank()
        || s.length() > 200) throw BusinessException.invalid("工具参数不符合 Schema");
    var cached =
        db.rows(
            "SELECT result_json,status FROM agent_tool_call WHERE workflow_id=? AND call_id=?",
            c.workflowId(),
            call.id());
    if (!cached.isEmpty() && "SUCCESS".equals(cached.getFirst().get("status")))
      return cached.getFirst().get("result_json").toString();
    if (cached.isEmpty()) {
      if (db.count("SELECT COUNT(*) FROM agent_tool_call WHERE workflow_id=?", c.workflowId())
          >= 20) throw BusinessException.invalid("工具调用预算超限");
      db.update(
          "INSERT INTO agent_tool_call(id,workflow_id,call_id,name,arguments_json,status) VALUES(?,?,?,?,?,?)",
          Json.id(),
          c.workflowId(),
          call.id(),
          call.name(),
          Json.write(d.parameter().equals("email") ? Map.of("email", "***") : args),
          "RUNNING");
    }
    long start = System.nanoTime();
    try {
      Object data;
      String value = args.get(d.parameter()).toString();
      switch (call.name()) {
        case "getOrderById" -> data = orders.get(value, c.teamId(), c.customerId());
        case "getOrderByEmail" -> {
          if (!customers.resolve(value, c.teamId()).equals(c.customerId()))
            throw BusinessException.denied();
          data = orders.byCustomer(c.teamId(), c.customerId());
        }
        case "getCustomerInfo" -> {
          if (!value.equals(c.customerId())) throw BusinessException.denied();
          data = customers.find(value, c.teamId());
        }
        case "checkRefundEligibility" ->
            data = orders.eligibility(value, c.teamId(), c.customerId());
        case "getLogisticsInfo" -> {
          orders.get(value, c.teamId(), c.customerId());
          var future = workers.submit(() -> gateway.logistics(value));
          try {
            data = future.get(10, TimeUnit.SECONDS);
          } catch (Exception e) {
            future.cancel(true);
            throw e;
          }
        }
        default -> throw BusinessException.denied();
      }
      String result =
          Json.write(
              Map.of(
                  "callId",
                  call.id(),
                  "success",
                  true,
                  "code",
                  "OK",
                  "data",
                  data,
                  "retryable",
                  false));
      db.update(
          "UPDATE agent_tool_call SET result_json=?,status='SUCCESS',duration_ms=? WHERE workflow_id=? AND call_id=?",
          result,
          (System.nanoTime() - start) / 1000000,
          c.workflowId(),
          call.id());
      return result;
    } catch (Exception e) {
      db.update(
          "UPDATE agent_tool_call SET status='FAILED',error_code=?,duration_ms=? WHERE workflow_id=? AND call_id=?",
          e instanceof BusinessException b ? b.code : "TOOL_UNAVAILABLE",
          (System.nanoTime() - start) / 1000000,
          c.workflowId(),
          call.id());
      if (e instanceof BusinessException b) throw b;
      throw new BusinessException(
          "TOOL_UNAVAILABLE",
          "工具暂时不可用",
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          true);
    }
  }
}
