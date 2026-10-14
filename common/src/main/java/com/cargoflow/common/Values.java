package com.cargoflow.common;

import java.util.*;

public class Values {
  public static String text(Map<String, Object> b, String key, int max) {
    Object raw = b.get(key);
    if (!(raw instanceof String) || ((String) raw).isBlank() || ((String) raw).length() > max)
      throw new Problem(400, "INVALID_INPUT", key + " 无效");
    return ((String) raw).trim();
  }

  public static long number(Map<String, Object> b, String key, long min, long max) {
    try {
      long n = Long.parseLong(String.valueOf(b.get(key)));
      if (n < min || n > max) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_INPUT", key + " 超出范围");
    }
  }

  public static long n(Map<String, Object> b, String key) {
    return ((Number) b.get(key)).longValue();
  }
}
