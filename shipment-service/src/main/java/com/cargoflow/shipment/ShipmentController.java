package com.cargoflow.shipment;

import com.cargoflow.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShipmentController {
  final ShipmentService service;

  public ShipmentController(ShipmentService service) {
    this.service = service;
  }

  Actor actor(HttpServletRequest r) {
    return InternalFilter.actor(r);
  }

  String trace(HttpServletRequest r) {
    return (String) r.getAttribute("trace");
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "service", "shipment");
  }

  @GetMapping("/shipments")
  public Object list(HttpServletRequest r) {
    return service.list(actor(r));
  }

  @PostMapping("/shipments")
  public Object create(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.create(actor(r), b, trace(r));
  }

  @GetMapping("/shipments/{id}")
  public Object one(HttpServletRequest r, @PathVariable String id) {
    return service.one(actor(r), id);
  }

  @PostMapping("/shipments/{id}/{action}")
  public Object action(
      HttpServletRequest r,
      @PathVariable String id,
      @PathVariable String action,
      @RequestBody Map<String, Object> b) {
    return service.action(actor(r), id, action, b, trace(r));
  }

  @PostMapping("/internal/assigned")
  public Object assigned(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.assigned(
        actor(r), Values.text(b, "shipmentId", 36), Values.text(b, "vehicleId", 36), trace(r));
  }

  @GetMapping("/outbox")
  public Object outbox(HttpServletRequest r) {
    return service.outbox(actor(r));
  }

  @PostMapping("/outbox/{id}/retry")
  public Object retry(HttpServletRequest r, @PathVariable String id) {
    service.retry(actor(r), id);
    return Map.of("ok", true);
  }
}
