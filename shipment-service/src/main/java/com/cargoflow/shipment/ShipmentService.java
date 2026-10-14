package com.cargoflow.shipment;

import static com.cargoflow.common.Values.*;

import com.cargoflow.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {
  final JdbcTemplate db;

  public ShipmentService(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> one(Actor a, String id) {
    Map<String, Object> s =
        db.queryForMap("select * from shipment where tenant_id=? and id=?", a.tenant(), id);
    if ("CUSTOMER".equals(a.role()) && !a.user().equals(s.get("user_id")))
      throw new Problem(403, "FORBIDDEN", "只能访问自己的运单");
    return s;
  }

  public Object list(Actor a) {
    return "CUSTOMER".equals(a.role())
        ? db.queryForList(
            "select * from shipment where tenant_id=? and user_id=? order by created_at desc",
            a.tenant(),
            a.user())
        : db.queryForList(
            "select * from shipment where tenant_id=? order by created_at desc", a.tenant());
  }

  @Transactional
  public Object create(Actor a, Map<String, Object> b, String trace) {
    a.roles("CUSTOMER", "OWNER");
    String key = text(b, "requestKey", 80),
        origin = text(b, "origin", 150),
        destination = text(b, "destination", 150),
        cargo = text(b, "cargo", 100);
    long weight = number(b, "weightKg", 1, 100000), distance = number(b, "distanceKm", 1, 10000);
    List<Map<String, Object>> existing =
        db.queryForList(
            "select * from shipment where tenant_id=? and user_id=? and request_key=?",
            a.tenant(),
            a.user(),
            key);
    if (!existing.isEmpty()) {
      Map<String, Object> old = existing.get(0);
      if (!origin.equals(old.get("origin"))
          || !destination.equals(old.get("destination"))
          || !cargo.equals(old.get("cargo"))
          || weight != n(old, "weight_kg")
          || distance != n(old, "distance_km")) throw new Problem(409, "KEY_REUSED", "请求标识已用于不同运单");
      return old;
    }
    String id = UUID.randomUUID().toString();
    long fee = 3000 + weight * 50 + distance * 30;
    db.update(
        "insert into"
            + " shipment(id,tenant_id,user_id,request_key,origin,destination,cargo,weight_kg,distance_km,fee_cents,state,created_at)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        a.tenant(),
        a.user(),
        key,
        origin,
        destination,
        cargo,
        weight,
        distance,
        fee,
        "PENDING_DISPATCH",
        System.currentTimeMillis());
    Map<String, Object> s = one(a, id);
    enqueue(s, "dispatch", "/internal/reserve", trace);
    enqueue(s, "tracking", "/internal/events", trace);
    return s;
  }

  void enqueue(Map<String, Object> s, String target, String path, String trace) {
    Map<String, Object> payload = new LinkedHashMap<>(s);
    String event = UUID.randomUUID().toString();
    payload.put("eventId", event);
    db.update(
        "insert into outbox(id,tenant_id,shipment_id,target,path,payload,trace_id,state,next_at)"
            + " values(?,?,?,?,?,?,?,'PENDING',?)",
        event,
        s.get("tenant_id"),
        s.get("id"),
        target,
        path,
        Json.write(payload),
        trace,
        System.currentTimeMillis());
  }

  @Transactional
  public Object assigned(Actor a, String id, String vehicle, String trace) {
    a.roles("SYSTEM");
    db.queryForMap("select id from shipment where tenant_id=? and id=? for update", a.tenant(), id);
    Map<String, Object> s = one(a, id);
    if ("ASSIGNED".equals(s.get("state"))) {
      if (!vehicle.equals(s.get("vehicle_id")))
        throw new Problem(409, "ASSIGNMENT_CONFLICT", "已有不同调度结果");
      return s;
    }
    if ("CANCELLED".equals(s.get("state"))) {
      enqueue(s, "dispatch", "/internal/release", trace);
      return s;
    }
    if (!"PENDING_DISPATCH".equals(s.get("state"))) return s;
    db.update(
        "update shipment set state='ASSIGNED',state_version=state_version+1,vehicle_id=? where id=?"
            + " and tenant_id=?",
        vehicle,
        id,
        a.tenant());
    s = one(a, id);
    enqueue(s, "tracking", "/internal/events", trace);
    return s;
  }

  @Transactional
  public Object action(Actor a, String id, String action, Map<String, Object> b, String trace) {
    db.queryForMap("select id from shipment where tenant_id=? and id=? for update", a.tenant(), id);
    Map<String, Object> s = one(a, id);
    String state = (String) s.get("state"), next;
    switch (action) {
      case "pickup":
        a.roles("DISPATCHER", "OWNER");
        if (state.equals("IN_TRANSIT")) return s;
        require(state, "ASSIGNED");
        next = "IN_TRANSIT";
        break;
      case "deliver":
        a.roles("DISPATCHER", "OWNER");
        if (state.equals("DELIVERED")) return s;
        require(state, "IN_TRANSIT");
        next = "DELIVERED";
        break;
      case "sign":
        a.roles("CUSTOMER", "OWNER");
        if (state.equals("SIGNED")) return s;
        require(state, "DELIVERED");
        db.update("update shipment set signature=? where id=?", text(b, "signature", 150), id);
        next = "SIGNED";
        break;
      case "cancel":
        a.roles("CUSTOMER", "OWNER");
        if (state.equals("CANCELLED")) return s;
        if (!Set.of("PENDING_DISPATCH", "ASSIGNED").contains(state))
          throw new Problem(409, "STATE_INVALID", "运输开始后不能自助取消");
        next = "CANCELLED";
        break;
      default:
        throw new Problem(400, "ACTION_INVALID", "操作不存在");
    }
    db.update("update shipment set state=?,state_version=state_version+1 where id=?", next, id);
    s = one(a, id);
    enqueue(s, "tracking", "/internal/events", trace);
    if (next.equals("SIGNED")) enqueue(s, "billing", "/internal/invoice", trace);
    if (Set.of("SIGNED", "CANCELLED").contains(next))
      enqueue(s, "dispatch", "/internal/release", trace);
    return s;
  }

  void require(String actual, String expected) {
    if (!actual.equals(expected)) throw new Problem(409, "STATE_INVALID", "运单状态不允许该操作");
  }

  public Object outbox(Actor a) {
    a.roles("OWNER", "DISPATCHER");
    return db.queryForList(
        "select id,shipment_id,target,state,attempts,error,next_at,trace_id from outbox where"
            + " tenant_id=? order by next_at desc",
        a.tenant());
  }

  @Transactional
  public void retry(Actor a, String id) {
    a.roles("OWNER", "DISPATCHER");
    if (db.update(
            "update outbox set state='PENDING',next_at=?,error=null where id=? and tenant_id=? and"
                + " state<>'SENT'",
            System.currentTimeMillis(),
            id,
            a.tenant())
        == 0) throw new Problem(409, "RETRY_INVALID", "消息不存在或已经送达");
  }
}
