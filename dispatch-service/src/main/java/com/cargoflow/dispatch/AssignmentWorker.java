package com.cargoflow.dispatch;

import com.cargoflow.common.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssignmentWorker {
  final JdbcTemplate db;
  final EventBus peers;
  final boolean jobs;
  final String shipment;

  public AssignmentWorker(
      JdbcTemplate db,
      EventBus peers,
      @Value("${cf.jobs}") boolean jobs,
      @Value("${cf.shipment-url}") String shipment) {
    this.db = db;
    this.peers = peers;
    this.jobs = jobs;
    this.shipment = shipment;
  }

  @Scheduled(fixedDelay = 3000, initialDelay = 5000)
  public void run() {
    if (!jobs) return;
    for (Map<String, Object> r :
        db.queryForList(
            "select * from callbacks where state='PENDING' and next_at<=? order by next_at limit 8",
            System.currentTimeMillis())) {
      try {
        peers.send(
            "shipment-service",
            shipment,
            "/internal/assigned",
            Json.write(
                Map.of("shipmentId", r.get("shipment_id"), "vehicleId", r.get("vehicle_id"))),
            new Actor((String) r.get("tenant_id"), "dispatch-service", "SYSTEM"),
            (String) r.get("trace_id"),
            (String) r.get("id"));
        db.update("update callbacks set state='SENT',attempts=attempts+1 where id=?", r.get("id"));
      } catch (Exception e) {
        db.update(
            "update callbacks set attempts=attempts+1,next_at=? where id=?",
            System.currentTimeMillis() + 10000,
            r.get("id"));
      }
    }
  }
}
