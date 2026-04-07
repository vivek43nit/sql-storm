package com.vivek.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security response headers to every HTTP response.
 *
 * <p>Headers applied:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME-type sniffing</li>
 *   <li>{@code X-Frame-Options: DENY} — blocks clickjacking</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}</li>
 *   <li>{@code Content-Security-Policy} — restricts resource loading</li>
 *   <li>{@code Strict-Transport-Security} — forces HTTPS (1 year)</li>
 * </ul>
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private static final String CSP =
      "default-src 'self'; " +
      "script-src 'self' 'unsafe-inline'; " +  // 'unsafe-inline' required for React in prod
      "style-src 'self' 'unsafe-inline'; " +
      "img-src 'self' data:; " +
      "font-src 'self'; " +
      "connect-src 'self'; " +
      "frame-ancestors 'none'";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    response.setHeader("Content-Security-Policy", CSP);
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    chain.doFilter(request, response);
  }
}
