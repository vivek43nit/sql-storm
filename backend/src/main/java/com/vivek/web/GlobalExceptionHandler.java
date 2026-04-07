package com.vivek.web;

import com.vivek.dto.ErrorResponse;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * Converts unhandled exceptions to the standard
 * {@code {"error": {"code": ..., "message": ..., "requestId": ...}}} shape.
 *
 * <p>Controllers that already return {@link ResponseEntity} keep their explicit
 * responses; only exceptions that bubble up are caught here.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ConnectionDetailNotFound.class)
  public ResponseEntity<ErrorResponse> handleConnectionNotFound(ConnectionDetailNotFound ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(error("CONNECTION_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(error("VALIDATION_ERROR", ex.getMessage()));
  }

  @ExceptionHandler(SQLException.class)
  public ResponseEntity<ErrorResponse> handleSqlException(SQLException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(error("DATABASE_ERROR", "A database error occurred: " + ex.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    // Re-throw so Spring Security's AccessDeniedHandler handles 403 JSON response.
    // Catching here prevents Spring Security from writing its response first.
    throw ex;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(error("INTERNAL_ERROR", "An unexpected error occurred"));
  }

  private ErrorResponse error(String code, String message) {
    return new ErrorResponse(code, message, MDC.get("requestId"));
  }
}
