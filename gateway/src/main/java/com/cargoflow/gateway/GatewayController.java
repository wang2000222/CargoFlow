package com.cargoflow.gateway;

import com.cargoflow.common.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class GatewayController {
  final Peers peers;
  final String secret, shipment, dispatch, tracking, billing;
  final boolean demo;

  public GatewayController(
      Peers peers,
      @Value("${cf.secret}") String secret,
      @Value("${cf.demo}") boolean demo,
      @Value("${cf.shipment-url}") String shipment,
      @Value("${cf.dispatch-url}") String dispatch,
      @Value("${cf.tracking-url}") String tracking,
      @Value("${cf.billing-url}") String billing) {
    if (secret.length() < 32) throw new IllegalStateException("CF_SECRET 至少32字符");
    this.peers = peers;
    this.secret = secret;
    this.demo = demo;
    this.shipment = shipment;
    this.dispatch = dispatch;
    this.tracking = tracking;
    this.billing = billing;
  }

  @PostMapping("/api/demo/login")
  public Object login(@RequestBody Map<String, String> b) {
    if (!demo) throw new Problem(503, "DEMO_DISABLED", "演示登录已关闭，正式身份源尚未接入");
    Actor a =
        switch (b.getOrDefault("account", "")) {
          case "customer-a" -> new Actor("tenant-a", "customer-a", "CUSTOMER");
          case "operator-a" -> new Actor("tenant-a", "operator-a", "DISPATCHER");
          case "finance-a" -> new Actor("tenant-a", "finance-a", "FINANCE");
          case "owner-a" -> new Actor("tenant-a", "owner-a", "OWNER");
          case "customer-b" -> new Actor("tenant-b", "customer-b", "CUSTOMER");
          default -> throw new Problem(400, "ACCOUNT_INVALID", "请选择演示身份");
        };
    String value =
        a.tenant()
            + "|"
            + a.user()
            + "|"
            + a.role()
            + "|"
            + (System.currentTimeMillis() + 7200000)
            + "|DEMO";
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    return Map.of(
        "token",
        encoded + "." + Crypto.sign(secret, "session\n" + encoded),
        "actor",
        a,
        "demo",
        true);
  }

  Actor actor(String header) {
    try {
      String[] token = header.substring(7).split("\\.");
      if (!header.startsWith("Bearer ")
          || token.length != 2
          || !Crypto.equal(token[1], Crypto.sign(secret, "session\n" + token[0])))
        throw new IllegalArgumentException();
      String[] fields =
          new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8).split("\\|");
      if (fields.length != 5
          || Long.parseLong(fields[3]) < System.currentTimeMillis()
          || !demo
          || !fields[4].equals("DEMO")) throw new IllegalArgumentException();
      return new Actor(fields[0], fields[1], fields[2]);
    } catch (Exception e) {
      throw new Problem(401, "UNAUTHORIZED", "请重新登录");
    }
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "UP", "service", "gateway");
  }

  @GetMapping("/api/system")
  public Object system(@RequestHeader(value = "Authorization", required = false) String auth) {
    Actor a = actor(auth);
    String trace = UUID.randomUUID().toString();
    List<Object> states = new ArrayList<>();
    for (String[] entry :
        new String[][] {
          {"运单服务", shipment}, {"调度服务", dispatch}, {"轨迹服务", tracking}, {"结算服务", billing}
        }) {
      try {
        states.add(
            Map.of(
                "name",
                entry[0],
                "status",
                "UP",
                "details",
                peers.get(entry[1], "/health", a, trace)));
      } catch (Exception e) {
        states.add(Map.of("name", entry[0], "status", "DOWN"));
      }
    }
    return Map.of("traceId", trace, "services", states, "demo", demo);
  }
}
