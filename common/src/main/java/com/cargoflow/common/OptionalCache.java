package com.cargoflow.common;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OptionalCache {
  final boolean enabled;
  final StringRedisTemplate redis;

  public OptionalCache(@Value("${cf.redis:false}") boolean enabled, StringRedisTemplate redis) {
    this.enabled = enabled;
    this.redis = redis;
  }

  public String get(String key) {
    if (!enabled) return null;
    try {
      return redis.opsForValue().get(key);
    } catch (Exception e) {
      return null;
    }
  }

  public void put(String key, Object value) {
    if (enabled)
      try {
        redis.opsForValue().set(key, Json.write(value), Duration.ofSeconds(30));
      } catch (Exception ignored) {
      }
  }
}
