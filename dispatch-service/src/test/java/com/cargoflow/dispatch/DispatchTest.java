package com.cargoflow.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.cargoflow.common.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "cf.secret=test-secret-at-least-thirty-two-characters",
      "cf.jobs=false",
      "spring.datasource.url=jdbc:h2:mem:dispatchtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    })
class DispatchTest {
  @Autowired DispatchService service;
  @Autowired JdbcTemplate db;
  Actor sys = new Actor("tenant-a", "system", "SYSTEM");

  Map<String, Object> body(long weight) {
    return Map.of("id", UUID.randomUUID().toString(), "user_id", "customer", "weight_kg", weight);
  }

  @Test
  void reserveAndReleaseAreIdempotent() {
    var b = body(100);
    var first = (Map<?, ?>) service.reserve(sys, b, "trace");
    assertEquals(first.get("id"), ((Map<?, ?>) service.reserve(sys, b, "trace")).get("id"));
    service.release(sys, b);
    service.release(sys, b);
    assertEquals(
        0L,
        db.queryForObject(
            "select used_kg from vehicle where id=?", Long.class, first.get("vehicle_id")));
  }

  @Test
  void releasedBeforeReserveDoesNotAllocate() {
    var b = body(200);
    service.release(sys, b);
    assertEquals("RELEASED", ((Map<?, ?>) service.reserve(sys, b, "trace")).get("state"));
  }

  @Test
  void capacityIsNotOversold() {
    assertThrows(Problem.class, () -> service.reserve(sys, body(100000), "trace"));
  }

  @Test
  void customerCannotAddVehicle() {
    assertThrows(
        Problem.class, () -> service.add(new Actor("tenant-a", "customer", "CUSTOMER"), Map.of()));
  }
}
