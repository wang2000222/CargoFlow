package com.cargoflow.dispatch;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.cargoflow.dispatch", "com.cargoflow.common"})
@EnableScheduling
public class DispatchApplication {
  public static void main(String[] args) {
    SpringApplication.run(DispatchApplication.class, args);
  }
}
