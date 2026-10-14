package com.cargoflow.dispatch;

import com.cargoflow.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class DispatchController {
  final DispatchService service;

  public DispatchController(DispatchService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "service", "dispatch");
  }

  @GetMapping("/vehicles")
  public Object vehicles(HttpServletRequest r) {
    return service.vehicles(InternalFilter.actor(r));
  }

  @PostMapping("/vehicles")
  public Object add(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.add(InternalFilter.actor(r), b);
  }

  @GetMapping("/assignments")
  public Object assignments(HttpServletRequest r) {
    return service.assignments(InternalFilter.actor(r));
  }

  @PostMapping("/internal/reserve")
  public Object reserve(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.reserve(InternalFilter.actor(r), b, (String) r.getAttribute("trace"));
  }

  @PostMapping("/internal/release")
  public Object release(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.release(InternalFilter.actor(r), b);
  }
}
