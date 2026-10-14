package com.cargoflow.shipment;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.cargoflow.shipment", "com.cargoflow.common"})
@EnableScheduling
public class ShipmentApplication {
  public static void main(String[] args) {
    SpringApplication.run(ShipmentApplication.class, args);
  }
}
