package com.cargoflow.dispatch;

import static com.cargoflow.common.Values.*;

import com.cargoflow.common.*;
import jakarta.annotation.PostConstruct;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchService {
  final JdbcTemplate db;
  final boolean demo;

  public DispatchService(JdbcTemplate db, @Value("${cf.demo}") boolean demo) {
    this.db = db;
    this.demo = demo;
  }

  @PostConstruct
  void seed() {
    if (demo && db.queryForObject("select count(*) from vehicle", Integer.class) == 0) {
      db.update(
          "insert into vehicle(id,tenant_id,plate,driver,capacity_kg) values(?,?,?,?,?)",
          UUID.randomUUID().toString(),
          "tenant-a",
          "演示 · 苏A物流01",
          "演示司机张师傅",
          10000);
      db.update(
          "insert into vehicle(id,tenant_id,plate,driver,capacity_kg) values(?,?,?,?,?)",
          UUID.randomUUID().toString(),
          "tenant-b",
          "演示 · 粤B物流01",
          "演示司机李师傅",
          5000);
    }
  }

  public Object vehicles(Actor a) {
    a.roles("OWNER", "DISPATCHER");
    return db.queryForList("select * from vehicle where tenant_id=? order by plate", a.tenant());
  }

  public Object assignments(Actor a) {
    return "CUSTOMER".equals(a.role())
        ? db.queryForList(
            "select * from assignment where tenant_id=? and user_id=?", a.tenant(), a.user())
        : db.queryForList("select * from assignment where tenant_id=?", a.tenant());
  }

  @Transactional
  public Object add(Actor a, Map<String, Object> b) {
    a.roles("OWNER", "DISPATCHER");
    String id = UUID.randomUUID().toString();
    db.update(
        "insert into vehicle(id,tenant_id,plate,driver,capacity_kg) values(?,?,?,?,?)",
        id,
        a.tenant(),
        text(b, "plate", 30),
        text(b, "driver", 80),
        number(b, "capacityKg", 1, 100000));
    return db.queryForMap("select * from vehicle where id=?", id);
  }

  @Transactional
  public Object reserve(Actor a, Map<String, Object> b, String trace) {
    a.roles("SYSTEM");
    String id = text(b, "id", 36);
    if (db.queryForObject(
            "select count(*) from release_marker where tenant_id=? and shipment_id=?",
            Integer.class,
            a.tenant(),
            id)
        > 0) return Map.of("state", "RELEASED");
    List<Map<String, Object>> old =
        db.queryForList(
            "select * from assignment where tenant_id=? and shipment_id=?", a.tenant(), id);
    if (!old.isEmpty()) return old.get(0);
    long weight = number(b, "weight_kg", 1, 100000);
    List<Map<String, Object>> vehicles =
        db.queryForList(
            "select * from vehicle where tenant_id=? and enabled=true and capacity_kg-used_kg>=?"
                + " order by plate for update",
            a.tenant(),
            weight);
    if (vehicles.isEmpty()) throw new Problem(409, "CAPACITY_UNAVAILABLE", "暂无可用运力，将保留运单等待重试");
    Map<String, Object> v = vehicles.get(0);
    if (db.update(
            "update vehicle set used_kg=used_kg+? where id=? and capacity_kg-used_kg>=?",
            weight,
            v.get("id"),
            weight)
        != 1) throw new Problem(409, "CAPACITY_CONFLICT", "运力已被占用");
    String aid = UUID.randomUUID().toString();
    db.update(
        "insert into assignment values(?,?,?,?,?,?,?)",
        aid,
        a.tenant(),
        id,
        text(b, "user_id", 80),
        v.get("id"),
        weight,
        "RESERVED");
    db.update(
        "insert into callbacks(id,tenant_id,shipment_id,vehicle_id,trace_id,state,next_at)"
            + " values(?,?,?,?,?,'PENDING',?)",
        UUID.randomUUID().toString(),
        a.tenant(),
        id,
        v.get("id"),
        trace,
        System.currentTimeMillis());
    return db.queryForMap("select * from assignment where id=?", aid);
  }

  @Transactional
  public Object release(Actor a, Map<String, Object> b) {
    a.roles("SYSTEM");
    String id = text(b, "id", 36);
    try {
      db.update("insert into release_marker values(?,?)", a.tenant(), id);
    } catch (org.springframework.dao.DuplicateKeyException ignored) {
    }
    List<Map<String, Object>> rows =
        db.queryForList(
            "select * from assignment where tenant_id=? and shipment_id=? for update",
            a.tenant(),
            id);
    if (!rows.isEmpty()) {
      Map<String, Object> r = rows.get(0);
      if (!"RELEASED".equals(r.get("state"))) {
        db.update(
            "update vehicle set used_kg=used_kg-? where id=?",
            r.get("weight_kg"),
            r.get("vehicle_id"));
        db.update("update assignment set state='RELEASED' where id=?", r.get("id"));
      }
    }
    return Map.of("ok", true);
  }
}
