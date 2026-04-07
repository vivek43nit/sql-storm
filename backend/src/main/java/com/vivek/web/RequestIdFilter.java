package com.vivek.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Injects a short {@code requestId} into the SLF4J MDC before each request.
 *
 * <p>This makes the ID available in every log line emitted during the request,
 * which is essential for correlating log entries in the structured JSON output
 * produced by the {@code prod} Logback profile.</p>
 *
 * <p>The ID is also echoed in the {@code X-Request-Id} response header so
 * clients can include it in support tickets.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
    }
  }
}
