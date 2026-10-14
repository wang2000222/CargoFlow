package com.cargoflow.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CryptoTest {
  @Test
  void changingTenantOrBodyInvalidatesSignature() {
    String secret = "test-secret", time = "123", nonce = "n", trace = "t";
    Actor a = new Actor("A", "u", "CUSTOMER");
    String sig =
        Crypto.sign(secret, Crypto.canonical("POST", "/shipments", "{}", a, time, nonce, trace));
    assertFalse(
        Crypto.equal(
            sig,
            Crypto.sign(
                secret,
                Crypto.canonical(
                    "POST",
                    "/shipments",
                    "{}",
                    new Actor("B", "u", "CUSTOMER"),
                    time,
                    nonce,
                    trace))));
    assertFalse(
        Crypto.equal(
            sig,
            Crypto.sign(
                secret,
                Crypto.canonical("POST", "/shipments", "{\"weight\":9}", a, time, nonce, trace))));
  }
}
