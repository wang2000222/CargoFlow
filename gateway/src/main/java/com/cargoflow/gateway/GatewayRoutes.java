package com.cargoflow.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.*;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.function.*;

@Configuration
public class GatewayRoutes {
  @Bean
  public RouterFunction<ServerResponse> logisticsRoutes(
      @Value("${cf.shipment-url}") String shipment,
      @Value("${cf.dispatch-url}") String dispatch,
      @Value("${cf.tracking-url}") String tracking,
      @Value("${cf.billing-url}") String billing) {
    return make("shipments", shipment)
        .and(make("outbox", shipment))
        .and(make("vehicles", dispatch))
        .and(make("assignments", dispatch))
        .and(make("tracking", tracking))
        .and(make("invoices", billing));
  }

  RouterFunction<ServerResponse> make(String name, String base) {
    String target = base.startsWith("service://") ? "lb://" + base.substring(10) : base;
    var builder =
        route(name)
            .route(
                RequestPredicates.path("/api/" + name + "/**")
                    .or(RequestPredicates.path("/api/" + name)),
                http())
            .before(uri(target))
            .before(stripPrefix(1));
    if (base.startsWith("service://")) builder.filter(lb(base.substring(10)));
    return builder.build();
  }
}
