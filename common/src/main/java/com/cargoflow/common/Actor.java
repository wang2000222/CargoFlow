package com.cargoflow.common;

public record Actor(String tenant, String user, String role) {
  public boolean staff() {
    return !"CUSTOMER".equals(role) && !"SYSTEM".equals(role);
  }

  public void roles(String... allowed) {
    for (String value : allowed) if (value.equals(role)) return;
    throw new Problem(403, "FORBIDDEN", "当前身份不能执行此操作");
  }
}
