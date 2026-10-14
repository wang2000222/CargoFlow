package com.cargoflow.billing;

import com.cargoflow.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class BillingController {
  final BillingService service;

  public BillingController(BillingService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "service", "billing");
  }

  @GetMapping("/invoices")
  public Object invoices(HttpServletRequest r) {
    return service.list(InternalFilter.actor(r));
  }

  @PostMapping("/invoices/{id}/pay")
  public Object pay(HttpServletRequest r, @PathVariable String id) {
    return service.pay(InternalFilter.actor(r), id);
  }

  @PostMapping("/internal/invoice")
  public Object create(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.create(InternalFilter.actor(r), b);
  }
}
