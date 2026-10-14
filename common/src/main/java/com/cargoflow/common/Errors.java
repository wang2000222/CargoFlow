package com.cargoflow.common;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(Problem.class)
  public ResponseEntity<?> problem(Problem e) {
    return ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
  }

  @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class)
  public ResponseEntity<?> missing(Exception e) {
    return ResponseEntity.status(404).body(Map.of("code", "NOT_FOUND", "message", "记录不存在"));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409).body(Map.of("code", "CONFLICT", "message", "记录冲突，请重试"));
  }
}
