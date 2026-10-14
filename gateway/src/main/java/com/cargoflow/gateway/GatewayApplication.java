package com.cargoflow.gateway;

import com.cargoflow.common.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({Peers.class, Errors.class, CloudInfrastructure.class})
public class GatewayApplication {
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
