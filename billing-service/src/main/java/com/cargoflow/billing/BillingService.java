package com.cargoflow.billing;

import static com.cargoflow.common.Values.*;

import com.cargoflow.common.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
  final JdbcTemplate db;
  final boolean demo;

  public BillingService(JdbcTemplate db, @Value("${cf.demo}") boolean demo) {
    this.db = db;
    this.demo = demo;
  }

  @Transactional
  public Object create(Actor a, Map<String, Object> b) {
    a.roles("SYSTEM");
    if (!"SIGNED".equals(b.get("state"))) throw new Problem(409, "NOT_SIGNED", "未签收不能生成结算单");
    String shipment = text(b, "id", 36);
    long fee = number(b, "fee_cents", 1, 100000000);
    List<Map<String, Object>> old =
        db.queryForList(
            "select * from invoice where tenant_id=? and shipment_id=?", a.tenant(), shipment);
    if (!old.isEmpty()) {
      if (n(old.get(0), "amount_cents") != fee)
        throw new Problem(409, "AMOUNT_CONFLICT", "重复结算金额不一致");
      return old.get(0);
    }
    String id = UUID.randomUUID().toString();
    db.update(
        "insert into invoice values(?,?,?,?,?,?,?)",
        id,
        a.tenant(),
        shipment,
        text(b, "user_id", 80),
        fee,
        "OPEN",
        System.currentTimeMillis());
    return db.queryForMap("select * from invoice where id=?", id);
  }

  public Object list(Actor a) {
    a.roles("CUSTOMER", "FINANCE", "OWNER");
    return "CUSTOMER".equals(a.role())
        ? db.queryForList(
            "select * from invoice where tenant_id=? and user_id=? order by created_at desc",
            a.tenant(),
            a.user())
        : db.queryForList(
            "select * from invoice where tenant_id=? order by created_at desc", a.tenant());
  }

  @Transactional
  public Object pay(Actor a, String id) {
    a.roles("CUSTOMER", "FINANCE", "OWNER");
    if (!demo) throw new Problem(503, "PAYMENT_NOT_CONFIGURED", "真实资金渠道尚未接入");
    Map<String, Object> r =
        db.queryForMap(
            "select * from invoice where tenant_id=? and id=? for update", a.tenant(), id);
    if ("CUSTOMER".equals(a.role()) && !a.user().equals(r.get("user_id")))
      throw new Problem(403, "FORBIDDEN", "无权支付此结算单");
    if ("PAID".equals(r.get("state"))) return r;
    db.update(
        "insert into payment_receipt values(?,?,?,?,?)",
        id,
        a.tenant(),
        r.get("amount_cents"),
        System.currentTimeMillis(),
        "SIMULATED");
    db.update("update invoice set state='PAID' where id=?", id);
    return db.queryForMap("select * from invoice where id=?", id);
  }
}
