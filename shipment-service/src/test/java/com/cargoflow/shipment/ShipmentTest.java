package com.cargoflow.shipment;

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
      "spring.datasource.url=jdbc:h2:mem:shipmenttest;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    })
class ShipmentTest {
  @Autowired ShipmentService service;
  @Autowired JdbcTemplate db;
  Actor customer = new Actor("test", "customer", "CUSTOMER"),
      system = new Actor("test", "system", "SYSTEM");

  Map<String, Object> body() {
    return new HashMap<>(
        Map.of(
            "requestKey",
            UUID.randomUUID().toString(),
            "origin",
            "A",
            "destination",
            "B",
            "cargo",
            "配件",
            "weightKg",
            10,
            "distanceKm",
            20));
  }

  Map<String, Object> create(Map<String, Object> b) {
    return (Map<String, Object>) service.create(customer, b, "trace");
  }

  @Test
  void idempotencyAndMismatch() {
    var b = body();
    var first = create(b);
    assertEquals(first.get("id"), create(b).get("id"));
    b.put("weightKg", 11);
    assertThrows(Problem.class, () -> create(b));
  }

  @Test
  void tenantIsolation() {
    var s = create(body());
    assertThrows(
        org.springframework.dao.EmptyResultDataAccessException.class,
        () -> service.one(new Actor("other", "customer", "CUSTOMER"), (String) s.get("id")));
  }

  @Test
  void stateAndCompensation() {
    var s = create(body());
    String id = (String) s.get("id");
    assertThrows(
        Problem.class,
        () -> service.action(customer, id, "sign", Map.of("signature", "x"), "trace"));
    service.action(customer, id, "cancel", Map.of(), "trace");
    service.assigned(system, id, "vehicle", "trace");
    assertEquals("CANCELLED", service.one(customer, id).get("state"));
    assertEquals(
        2,
        db.queryForObject(
            "select count(*) from outbox where shipment_id=? and path='/internal/release'",
            Integer.class,
            id));
  }

  @Test
  void createAndEventsAreAtomic() {
    var s = create(body());
    assertEquals(
        2,
        db.queryForObject(
            "select count(*) from outbox where shipment_id=?", Integer.class, s.get("id")));
  }
}
