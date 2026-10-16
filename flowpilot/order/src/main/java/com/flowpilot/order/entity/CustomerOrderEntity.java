package com.flowpilot.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;

@TableName("customer_order")
public class CustomerOrderEntity {
  @TableId(type = IdType.INPUT)
  public String id;

  public String teamId;
  public String customerId;
  public String status;
  public BigDecimal total;
  public String currency;
  public String productName;
  @Version public Integer version;
}
