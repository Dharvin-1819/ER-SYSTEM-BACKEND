package com.kiot.csrm.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> expected(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason(), "timestamp", java.time.Instant.now()));
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message", "Check required fields, email, dates and password (8–72 characters)."));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "That value already exists or conflicts with existing data."));
  }

  @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
  ResponseEntity<?> changed(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "This reservation changed. Refresh and try again."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed", e);
    return ResponseEntity.internalServerError()
        .body(Map.of("message", "The request could not be completed."));
  }
}
