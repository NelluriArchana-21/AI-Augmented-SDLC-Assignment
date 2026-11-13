package com.assignment.smarttodo.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
    LOGGER.warn("Validation failed: {}", ex.getMessage());
    Map<String, String> body = new LinkedHashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(TaskNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(TaskNotFoundException ex) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handlePersistenceFailure(IllegalStateException ex) {
    LOGGER.error("Persistence failure", ex);
    Map<String, String> body = new LinkedHashMap<>();
    body.put("error", "Persistence operation failed");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
