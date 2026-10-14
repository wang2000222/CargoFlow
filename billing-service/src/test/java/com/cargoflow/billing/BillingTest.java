package com.cargoflow.billing;

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
      "spring.datasource.url=jdbc:h2:mem:billingtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    })
class BillingTest {
  @Autowired BillingService service;
  @Autowired JdbcTemplate db;
  Actor sys = new Actor("test", "system", "SYSTEM"),
      customer = new Actor("test", "customer", "CUSTOMER");

  Map<String, Object> body() {
    return new HashMap<>(
        Map.of(
            "id",
            UUID.randomUUID().toString(),
            "user_id",
            "customer",
            "state",
            "SIGNED",
            "fee_cents",
            5000));
  }

  @Test
  void duplicateInvoiceAndConflict() {
    var b = body();
    var invoice = (Map<?, ?>) service.create(sys, b);
    assertEquals(invoice.get("id"), ((Map<?, ?>) service.create(sys, b)).get("id"));
    b.put("fee_cents", 6000);
    assertThrows(Problem.class, () -> service.create(sys, b));
  }

  @Test
  void repeatedPaymentHasOneReceipt() {
    var invoice = (Map<?, ?>) service.create(sys, body());
    String id = (String) invoice.get("id");
    service.pay(customer, id);
    service.pay(customer, id);
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from payment_receipt where invoice_id=?", Integer.class, id));
  }

  @Test
  void mustBeSigned() {
    var b = body();
    b.put("state", "DELIVERED");
    assertThrows(Problem.class, () -> service.create(sys, b));
  }

  @Test
  void cannotPayOthersInvoice() {
    String id = (String) ((Map<?, ?>) service.create(sys, body())).get("id");
    assertThrows(Problem.class, () -> service.pay(new Actor("test", "other", "CUSTOMER"), id));
  }
}
