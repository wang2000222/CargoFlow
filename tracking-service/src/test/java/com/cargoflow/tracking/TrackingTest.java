package com.cargoflow.tracking;

import static org.junit.jupiter.api.Assertions.*;

import com.cargoflow.common.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "cf.secret=test-secret-at-least-thirty-two-characters",
      "cf.jobs=false",
      "spring.datasource.url=jdbc:h2:mem:trackingtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    })
class TrackingTest {
  @Autowired TrackingService service;
  Actor sys = new Actor("test", "system", "SYSTEM"),
      customer = new Actor("test", "customer", "CUSTOMER");

  Map<String, Object> body(String id, String state, long version) {
    return Map.of(
        "eventId",
        UUID.randomUUID().toString(),
        "id",
        id,
        "user_id",
        "customer",
        "state",
        state,
        "state_version",
        version);
  }

  @Test
  void duplicateAndVersionConflict() {
    String id = UUID.randomUUID().toString();
    var b = body(id, "ASSIGNED", 1);
    service.event(sys, b, "trace");
    assertEquals(true, ((Map<?, ?>) service.event(sys, b, "trace")).get("duplicate"));
    assertThrows(Problem.class, () -> service.event(sys, body(id, "CANCELLED", 1), "trace"));
  }

  @Test
  void outOfOrderIsSorted() {
    String id = UUID.randomUUID().toString();
    service.event(sys, body(id, "ASSIGNED", 1), "trace");
    service.event(sys, body(id, "PENDING_DISPATCH", 0), "trace");
    var rows = (List<Map<String, Object>>) service.timeline(customer, id);
    assertEquals(0L, ((Number) rows.get(0).get("state_version")).longValue());
    assertEquals(2, rows.size());
  }

  @Test
  void ownerAuthorizationBeforeCache() {
    String id = UUID.randomUUID().toString();
    service.event(sys, body(id, "SIGNED", 4), "trace");
    assertThrows(Problem.class, () -> service.timeline(new Actor("test", "other", "CUSTOMER"), id));
    assertEquals(
        0, ((List<?>) service.timeline(new Actor("other", "customer", "CUSTOMER"), id)).size());
  }
}
