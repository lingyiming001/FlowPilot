package com.flowpilot.app.config;

import com.flowpilot.common.util.*;
import com.flowpilot.security.mapper.SecurityMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "flowpilot.demo-enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
  private final SecurityMapper db;
  private final PasswordEncoder passwords;
  private final Crypto crypto;
  private final String password;

  public DemoDataInitializer(
      SecurityMapper db,
      PasswordEncoder passwords,
      Crypto crypto,
      @Value("${flowpilot.demo-password}") String password) {
    this.db = db;
    this.passwords = passwords;
    this.crypto = crypto;
    this.password = password;
  }

  @Transactional
  public void run(ApplicationArguments ignored) {
    if (password.length() < 12)
      throw new IllegalArgumentException("DEMO_PASSWORD must be at least 12 characters");
    String[] roles = {"ADMIN", "SUPERVISOR", "AGENT_OPERATOR", "AUDITOR"};
    Map<String, List<String>> perms =
        Map.of(
            "ADMIN",
            List.of("users:manage"),
            "SUPERVISOR",
            List.of(
                "mails:read",
                "mails:analyze",
                "orders:read",
                "workflows:read",
                "approvals:decide",
                "workflows:retry",
                "audit:read"),
            "AGENT_OPERATOR",
            List.of(
                "mails:read",
                "mails:analyze",
                "orders:read",
                "workflows:read",
                "approvals:decide",
                "workflows:retry"),
            "AUDITOR",
            List.of("mails:read", "orders:read", "workflows:read", "audit:read"));
    for (String role : roles) {
      db.update("INSERT IGNORE INTO sys_role(id,code,name) VALUES(?,?,?)", role, role, role);
      for (String permission : perms.get(role)) {
        db.update(
            "INSERT IGNORE INTO sys_permission(id,code,name) VALUES(?,?,?)",
            permission,
            permission,
            permission);
        db.update(
            "INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES(?,?,?)",
            Json.hash(role + permission).substring(0, 36),
            role,
            permission);
      }
      String username =
          switch (role) {
            case "ADMIN" -> "admin";
            case "SUPERVISOR" -> "supervisor";
            case "AGENT_OPERATOR" -> "operator";
            default -> "auditor";
          };
      db.update(
          "INSERT IGNORE INTO sys_user(id,username,password_hash,display_name,team_id) VALUES(?,?,?,?,?)",
          username,
          username,
          passwords.encode(password),
          switch (role) {
            case "ADMIN" -> "系统管理员";
            case "SUPERVISOR" -> "陈主管";
            case "AGENT_OPERATOR" -> "林客服";
            default -> "审计专员";
          },
          "team-demo");
      db.update(
          "INSERT IGNORE INTO sys_user_role(id,user_id,role_id) VALUES(?,?,?)",
          username + "-role",
          username,
          role);
    }
    db.update(
        "INSERT IGNORE INTO sys_user_role(id,user_id,role_id) VALUES('admin-business','admin','SUPERVISOR')");
    db.update(
        "INSERT IGNORE INTO mail_account(id,team_id,name,credentials_ref) VALUES('demo-account','team-demo','客户服务邮箱','env:MAIL_PASSWORD')");
    String[][] cs = {
      {"customer-chen", "陈思远", "chen@example.test"}, {"customer-zhou", "周雨桐", "zhou@example.test"}
    };
    for (String[] c : cs) {
      db.update(
          "INSERT IGNORE INTO customer(id,team_id,name,email_cipher,email_hash) VALUES(?,'team-demo',?,?,?)",
          c[0],
          c[1],
          crypto.encrypt(c[2]),
          crypto.identityHash(c[2]));
      db.update(
          "INSERT IGNORE INTO customer_identity(id,customer_id,identity_hash,verified) VALUES(?,?,?,TRUE)",
          c[0] + "-identity",
          c[0],
          crypto.identityHash(c[2]));
    }
    String[][] orders = {
      {"ORD20260001", "customer-chen", "SHIPPED", "899.00", "无线降噪耳机 Pro"},
      {"ORD20260002", "customer-zhou", "DELIVERED", "1299.00", "智能桌面显示器"},
      {"ORD20260003", "customer-chen", "PAID", "259.00", "人体工学键盘"}
    };
    for (String[] o : orders) {
      db.update(
          "INSERT IGNORE INTO customer_order(id,team_id,customer_id,status,total,address_cipher,product_name) VALUES(?,'team-demo',?,?,?,?,?)",
          o[0],
          o[1],
          o[2],
          new java.math.BigDecimal(o[3]),
          crypto.encrypt("上海市示例路 100 号"),
          o[4]);
      db.update(
          "INSERT IGNORE INTO order_item(id,order_id,product_name,quantity,unit_price) VALUES(?,?,?,1,?)",
          o[0] + "-item",
          o[0],
          o[4],
          new java.math.BigDecimal(o[3]));
    }
    seedMail(
        "demo-query",
        "customer-chen",
        "chen@example.test",
        "想了解我的订单物流进度",
        "你好，我想查询订单 ORD20260001 的物流状态。谢谢！");
    seedMail(
        "demo-refund",
        "customer-zhou",
        "zhou@example.test",
        "商品有问题，希望申请退款",
        "订单 ORD20260002 的商品有问题，我想申请退款。请帮忙处理。");
    seedMail(
        "demo-other", "customer-chen", "chen@example.test", "需要进一步了解售后政策", "你好，想咨询购买商品的保修政策和售后流程。");
  }

  private void seedMail(String id, String customer, String email, String subject, String body) {
    db.update(
        "INSERT IGNORE INTO mail_message(id,team_id,customer_id,account_id,message_id,subject,body_cipher,sender_hash,identity_verified) VALUES(?,'team-demo',?,'demo-account',?,?,?,?,TRUE)",
        id,
        customer,
        "fixture-" + id,
        subject,
        crypto.encrypt(body),
        crypto.identityHash(email));
  }
}
