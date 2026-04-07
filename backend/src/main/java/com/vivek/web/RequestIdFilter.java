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
import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Injects {@code requestId}, {@code trace_id}, and {@code span_id} into the SLF4J MDC
 * before each request, making them available on every log line emitted during the request.
 *
 * <p>Trace context follows the W3C Trace Context spec (traceparent header). If an
 * incoming {@code traceparent} header is present and valid, its {@code traceId} segment
 * is propagated unchanged. Otherwise a new trace ID is generated. A fresh span ID is
 * always generated for this hop.</p>
 *
 * <p>The outbound {@code traceparent} response header and {@code X-Request-Id} header
 * allow clients to correlate requests across service boundaries and in support tickets.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  // W3C traceparent: 00-{traceId:32hex}-{parentId:16hex}-{flags:2hex}
  private static final Pattern TRACEPARENT =
      Pattern.compile("^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String traceId = extractOrGenerateTraceId(request.getHeader("traceparent"));
    String spanId = generateSpanId();

    MDC.put("requestId", requestId);
    MDC.put("trace_id", traceId);
    MDC.put("span_id", spanId);

    response.setHeader("X-Request-Id", requestId);
    response.setHeader("traceparent", "00-" + traceId + "-" + spanId + "-00");

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
      MDC.remove("trace_id");
      MDC.remove("span_id");
    }
  }

  private String extractOrGenerateTraceId(String traceparent) {
    if (traceparent != null) {
      java.util.regex.Matcher m = TRACEPARENT.matcher(traceparent.trim());
      if (m.matches()) {
        return m.group(1);
      }
    }
    return UUID.randomUUID().toString().replace("-", "");
  }

  private String generateSpanId() {
    byte[] bytes = new byte[8];
    RANDOM.nextBytes(bytes);
    StringBuilder sb = new StringBuilder(16);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
