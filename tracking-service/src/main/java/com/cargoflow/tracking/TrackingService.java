package com.cargoflow.tracking;

import static com.cargoflow.common.Values.*;

import com.cargoflow.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingService {
  final JdbcTemplate db;
  final OptionalCache cache;

  public TrackingService(JdbcTemplate db, OptionalCache cache) {
    this.db = db;
    this.cache = cache;
  }

  @Transactional
  public Object event(Actor a, Map<String, Object> b, String trace) {
    a.roles("SYSTEM");
    String event = text(b, "eventId", 36),
        shipment = text(b, "id", 36),
        state = text(b, "state", 30);
    long version = number(b, "state_version", 0, 1000);
    if (!Set.of("PENDING_DISPATCH", "ASSIGNED", "IN_TRANSIT", "DELIVERED", "SIGNED", "CANCELLED")
        .contains(state)) throw new Problem(400, "STATE_INVALID", "轨迹状态无效");
    List<Map<String, Object>> old =
        db.queryForList(
            "select * from tracking_event where tenant_id=? and shipment_id=? and state_version=?",
            a.tenant(),
            shipment,
            version);
    if (!old.isEmpty()) {
      if (!state.equals(old.get(0).get("state")))
        throw new Problem(409, "EVENT_CONFLICT", "同版本轨迹不一致");
      return Map.of("duplicate", true);
    }
    db.update(
        "insert into tracking_event values(?,?,?,?,?,?,?,?)",
        event,
        a.tenant(),
        shipment,
        text(b, "user_id", 80),
        state,
        version,
        trace,
        System.currentTimeMillis());
    return Map.of("ok", true);
  }

  public Object timeline(Actor a, String id) {
    a.roles("CUSTOMER", "OWNER", "DISPATCHER", "FINANCE");
    List<Map<String, Object>> last =
        db.queryForList(
            "select * from tracking_event where tenant_id=? and shipment_id=? order by"
                + " state_version desc limit 1",
            a.tenant(),
            id);
    if (last.isEmpty()) return List.of();
    if ("CUSTOMER".equals(a.role()) && !a.user().equals(last.get(0).get("user_id")))
      throw new Problem(403, "FORBIDDEN", "无权查看此运单轨迹");
    long count =
        db.queryForObject(
            "select count(*) from tracking_event where tenant_id=? and shipment_id=?",
            Long.class,
            a.tenant(),
            id);
    String key =
        "cargoflow:tracking:"
            + a.tenant()
            + ":"
            + id
            + ":"
            + last.get(0).get("state_version")
            + ":"
            + count;
    String cached = cache.get(key);
    if (cached != null)
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached, Object.class);
      } catch (Exception ignored) {
      }
    List<Map<String, Object>> rows =
        db.queryForList(
            "select state,state_version,received_at,trace_id from tracking_event where tenant_id=?"
                + " and shipment_id=? order by state_version",
            a.tenant(),
            id);
    cache.put(key, rows);
    return rows;
  }
}
