package com.vivek.web;

import com.vivek.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user, per-endpoint rate limiting using Bucket4j (in-memory).
 *
 * <ul>
 *   <li>{@code POST /api/execute} — 60 requests/minute</li>
 *   <li>{@code POST|PUT|DELETE /api/row/**} — 30 requests/minute</li>
 * </ul>
 *
 * <p>Buckets are keyed by {@code "<endpoint>:<username>"}. Unauthenticated
 * requests use {@code "anonymous"} as the username key.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int EXECUTE_LIMIT = 60;
  private static final int ROW_LIMIT = 30;

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    String path = request.getRequestURI();
    String method = request.getMethod();

    Integer limit = resolveLimit(path, method);
    if (limit == null) {
      chain.doFilter(request, response);
      return;
    }

    String user = currentUser();
    String bucketKey = limit + ":" + user;
    Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(limit));

    if (bucket.tryConsume(1)) {
      chain.doFilter(request, response);
    } else {
      long waitSeconds = bucket.getAvailableTokens() <= 0 ? 60 : 1;
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setHeader("Retry-After", String.valueOf(waitSeconds));
      ErrorResponse body = new ErrorResponse(
          "RATE_LIMIT_EXCEEDED",
          "Too many requests. Please slow down and retry after " + waitSeconds + "s.",
          MDC.get("requestId"));
      objectMapper.writeValue(response.getWriter(), body);
    }
  }

  /**
   * Returns the rate limit for the given path+method, or null if no limit applies.
   */
  private Integer resolveLimit(String path, String method) {
    if ("POST".equals(method) && path.equals("/api/execute")) {
      return EXECUTE_LIMIT;
    }
    if (path.startsWith("/api/row") &&
        ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
      return ROW_LIMIT;
    }
    return null;
  }

  private static Bucket buildBucket(int requestsPerMinute) {
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(requestsPerMinute)
            .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
            .build())
        .build();
  }

  private static String currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
  }
}
