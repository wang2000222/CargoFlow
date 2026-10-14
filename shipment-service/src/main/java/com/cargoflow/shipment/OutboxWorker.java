package com.cargoflow.shipment;

import com.cargoflow.common.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxWorker {
  final JdbcTemplate db;
  final EventBus peers;
  final boolean jobs;
  final Map<String, String> urls;

  public OutboxWorker(
      JdbcTemplate db,
      EventBus peers,
      @Value("${cf.jobs}") boolean jobs,
      @Value("${cf.dispatch-url}") String dispatch,
      @Value("${cf.tracking-url}") String tracking,
      @Value("${cf.billing-url}") String billing) {
    this.db = db;
    this.peers = peers;
    this.jobs = jobs;
    urls = Map.of("dispatch", dispatch, "tracking", tracking, "billing", billing);
  }

  @Scheduled(fixedDelay = 3000, initialDelay = 5000)
  public void run() {
    if (!jobs) return;
    for (Map<String, Object> m :
        db.queryForList(
            "select * from outbox where state in ('PENDING','FAILED') and next_at<=? order by"
                + " next_at limit 8",
            System.currentTimeMillis())) {
      String id = (String) m.get("id");
      try {
        peers.send(
            m.get("target") + "-service",
            urls.get(m.get("target")),
            (String) m.get("path"),
            (String) m.get("payload"),
            new Actor((String) m.get("tenant_id"), "shipment-service", "SYSTEM"),
            (String) m.get("trace_id"),
            id);
        db.update("update outbox set state='SENT',attempts=attempts+1,error=null where id=?", id);
      } catch (Exception e) {
        int attempts = ((Number) m.get("attempts")).intValue() + 1;
        db.update(
            "update outbox set state='FAILED',attempts=?,next_at=?,error=? where id=?",
            attempts,
            System.currentTimeMillis() + Math.min(60000, attempts * 5000),
            "下游投递失败，待重试",
            id);
      }
    }
  }
}
