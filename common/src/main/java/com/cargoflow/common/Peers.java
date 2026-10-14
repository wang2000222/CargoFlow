package com.cargoflow.common;

import io.github.resilience4j.circuitbreaker.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

@Component
public class Peers {
  final String secret;
  final Wire wire;
  final DiscoveryClient discovery;
  final AtomicInteger cursor = new AtomicInteger();
  final CircuitBreakerRegistry circuits =
      CircuitBreakerRegistry.of(
          CircuitBreakerConfig.custom()
              .slidingWindowSize(3)
              .minimumNumberOfCalls(3)
              .failureRateThreshold(50)
              .waitDurationInOpenState(Duration.ofSeconds(10))
              .recordException(e -> !(e instanceof Problem) || ((Problem) e).status >= 500)
              .build());

  public Peers(@Value("${cf.secret}") String secret, Wire wire, DiscoveryClient discovery) {
    this.secret = secret;
    this.wire = wire;
    this.discovery = discovery;
  }

  String resolve(String base) {
    if (!base.startsWith("service://")) return base;
    String name = base.substring(10);
    var instances = discovery.getInstances(name);
    if (instances.isEmpty()) throw new Problem(503, "SERVICE_NOT_DISCOVERED", "服务尚未注册");
    return instances
        .get(Math.floorMod(cursor.getAndIncrement(), instances.size()))
        .getUri()
        .toString();
  }

  public String call(
      String base, String path, String method, String body, Actor actor, String trace) {
    String actual = resolve(base);
    String time = String.valueOf(System.currentTimeMillis()), nonce = UUID.randomUUID().toString();
    Map<String, String> headers =
        Map.of(
            "Content-Type",
            "application/json",
            "X-CF-Tenant",
            actor.tenant(),
            "X-CF-User",
            actor.user(),
            "X-CF-Role",
            actor.role(),
            "X-CF-Time",
            time,
            "X-CF-Nonce",
            nonce,
            "X-Trace-Id",
            trace,
            "X-CF-Signature",
            Crypto.sign(secret, Crypto.canonical(method, path, body, actor, time, nonce, trace)));
    try {
      return circuits
          .circuitBreaker(base)
          .executeSupplier(
              () -> {
                try (feign.Response response =
                    switch (method) {
                      case "GET" -> wire.get(URI.create(actual + path), headers);
                      case "POST" -> wire.post(URI.create(actual + path), headers, body);
                      case "PUT" -> wire.put(URI.create(actual + path), headers, body);
                      case "PATCH" -> wire.patch(URI.create(actual + path), headers, body);
                      default -> throw new Problem(405, "METHOD_INVALID", "不支持的方法");
                    }) {
                  String data =
                      response.body() == null
                          ? "{}"
                          : new String(
                              response.body().asInputStream().readAllBytes(),
                              StandardCharsets.UTF_8);
                  if (response.status() >= 500)
                    throw new Problem(503, "PEER_UNAVAILABLE", "下游服务暂不可用");
                  if (response.status() >= 400) {
                    Map<String, Object> error = Json.read(data);
                    throw new Problem(
                        response.status(),
                        String.valueOf(error.getOrDefault("code", "PEER_REJECTED")),
                        String.valueOf(error.getOrDefault("message", "请求被拒绝")));
                  }
                  return data;
                } catch (Problem e) {
                  throw e;
                } catch (Exception e) {
                  throw new Problem(503, "PEER_UNAVAILABLE", "下游连接失败，操作尚未确认");
                }
              });
    } catch (CallNotPermittedException e) {
      throw new Problem(503, "CIRCUIT_OPEN", "服务熔断中，稍后重试");
    }
  }

  public Object get(String base, String path, Actor a, String trace) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .readValue(call(base, path, "GET", "", a, trace), Object.class);
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(502, "BAD_RESPONSE", "下游响应无效");
    }
  }
}
