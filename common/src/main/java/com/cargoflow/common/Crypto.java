package com.cargoflow.common;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {
  public static String sign(String secret, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean equal(String a, String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  public static String canonical(
      String method,
      String path,
      String body,
      Actor actor,
      String time,
      String nonce,
      String trace) {
    return method
        + "\n"
        + path
        + "\n"
        + actor.tenant()
        + "\n"
        + actor.user()
        + "\n"
        + actor.role()
        + "\n"
        + time
        + "\n"
        + nonce
        + "\n"
        + trace
        + "\n"
        + body;
  }
}
