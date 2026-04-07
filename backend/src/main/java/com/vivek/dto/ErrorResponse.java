package com.vivek.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Standardised error body for all 4xx/5xx API responses.
 *
 * <pre>
 * {
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     "message": "username is required",
 *     "requestId": "a1b2c3d4e5f6",
 *     "details": [...]   // only present for validation errors
 *   }
 * }
 * </pre>
 */
public class ErrorResponse {

  private final Error error;

  public ErrorResponse(String code, String message, String requestId) {
    this.error = new Error(code, message, requestId, null);
  }

  public ErrorResponse(String code, String message, String requestId, List<Detail> details) {
    this.error = new Error(code, message, requestId, details);
  }

  public Error getError() {
    return error;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Error(String code, String message, String requestId, List<Detail> details) {}

  public record Detail(String field, String code, String message) {}
}
