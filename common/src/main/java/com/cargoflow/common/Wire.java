package com.cargoflow.common;

import feign.Response;
import java.net.URI;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "signed-peer", url = "http://127.0.0.1:9")
public interface Wire {
  @GetMapping
  Response get(URI uri, @RequestHeader Map<String, String> headers);

  @PostMapping
  Response post(URI uri, @RequestHeader Map<String, String> headers, @RequestBody String body);

  @PutMapping
  Response put(URI uri, @RequestHeader Map<String, String> headers, @RequestBody String body);

  @PatchMapping
  Response patch(URI uri, @RequestHeader Map<String, String> headers, @RequestBody String body);
}
