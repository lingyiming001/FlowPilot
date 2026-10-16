package com.flowpilot.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.flowpilot")
@org.mybatis.spring.annotation.MapperScan("com.flowpilot.order.mapper")
@EnableScheduling
public class FlowPilotApplication {
  public static void main(String[] args) {
    SpringApplication.run(FlowPilotApplication.class, args);
  }
}
