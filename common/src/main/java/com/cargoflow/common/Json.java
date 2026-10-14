package com.cargoflow.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class Json {
  private static final ObjectMapper mapper = new ObjectMapper();

  public static String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("无法序列化数据", e);
    }
  }

  public static Map<String, Object> read(String value) {
    try {
      return mapper.readValue(value, Map.class);
    } catch (Exception e) {
      throw new Problem(400, "INVALID_JSON", "请求格式无效");
    }
  }
}
