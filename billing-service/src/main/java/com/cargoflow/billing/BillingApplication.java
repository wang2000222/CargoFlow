package com.cargoflow.billing;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(scanBasePackages = {"com.cargoflow.billing", "com.cargoflow.common"})
public class BillingApplication {
  public static void main(String[] args) {
    SpringApplication.run(BillingApplication.class, args);
  }
}
