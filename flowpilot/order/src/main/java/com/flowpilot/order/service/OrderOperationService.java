package com.flowpilot.order.service;

import com.flowpilot.common.exception.BusinessException;
import com.flowpilot.common.util.*;
import com.flowpilot.order.mapper.OrderMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderOperationService {
  private final OrderMapper db;
  private final Crypto crypto;

  public OrderOperationService(OrderMapper db, Crypto crypto) {
    this.db = db;
    this.crypto = crypto;
  }

  public Map<String, Object> validate(
      String action, String id, String team, String customer, int expectedVersion) {
    var o =
        db.one(
            "SELECT * FROM customer_order WHERE id=? AND team_id=? AND customer_id=? FOR UPDATE",
            id,
            team,
            customer);
    if (((Number) o.get("version")).intValue() != expectedVersion)
      throw BusinessException.conflict("订单已变化，请重新生成建议与审批");
    String status = o.get("status").toString();
    boolean valid =
        switch (action) {
          case "requestRefund" -> Set.of("PAID", "SHIPPED", "DELIVERED").contains(status);
          case "cancelOrder", "updateShippingAddress" -> Set.of("PENDING", "PAID").contains(status);
          default -> false;
        };
    if (!valid) throw BusinessException.conflict("订单状态不允许该操作");
    return Map.of("amount", o.get("total"), "currency", o.get("currency"));
  }

  public void apply(String action, String id, String execution, Map<String, Object> parameters) {
    if (db.count("SELECT COUNT(*) FROM order_operation WHERE execution_id=?", execution) > 0)
      return;
    if (action.equals("updateShippingAddress")) {
      db.update(
          "UPDATE customer_order SET address_cipher=?,version=version+1 WHERE id=?",
          crypto.encrypt(parameters.get("address").toString()),
          id);
    } else {
      db.update(
          "UPDATE customer_order SET status=?,version=version+1 WHERE id=?",
          action.equals("requestRefund") ? "REFUNDED" : "CANCELLED",
          id);
    }
    db.update(
        "INSERT INTO order_operation(id,order_id,operation_type,execution_id,result_status) VALUES(?,?,?,?,?)",
        Json.id(),
        id,
        action,
        execution,
        "SUCCESS");
  }
}
