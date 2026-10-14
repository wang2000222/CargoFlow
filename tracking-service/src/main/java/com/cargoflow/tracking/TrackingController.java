package com.cargoflow.tracking;

import com.cargoflow.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class TrackingController {
  final TrackingService service;

  public TrackingController(TrackingService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "service", "tracking");
  }

  @GetMapping("/tracking/{id}")
  public Object tracking(HttpServletRequest r, @PathVariable String id) {
    return service.timeline(InternalFilter.actor(r), id);
  }

  @PostMapping("/internal/events")
  public Object event(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return service.event(InternalFilter.actor(r), b, (String) r.getAttribute("trace"));
  }
}
