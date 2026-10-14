package com.cargoflow.tracking;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

@SpringBootApplication(scanBasePackages = {"com.cargoflow.tracking", "com.cargoflow.common"})
public class TrackingApplication {
  public static void main(String[] args) {
    SpringApplication.run(TrackingApplication.class, args);
  }
}
