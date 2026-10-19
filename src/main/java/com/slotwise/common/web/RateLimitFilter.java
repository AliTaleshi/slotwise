package com.slotwise.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.common.error.GlobalExceptionHandler;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiting per client address for the public (unauthenticated) endpoints and the
 * login/registration endpoints. Buckets live in memory, which is enough for a single instance; put
 * Bucket4j's distributed backends (Redis/JDBC) behind the same code to share limits across nodes.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final int MAX_TRACKED_CLIENTS = 50_000;

  private enum Group {
    PUBLIC,
    AUTH
  }

  private final RateLimitProperties props;
  private final ObjectMapper mapper;
  private final AntPathMatcher matcher = new AntPathMatcher();
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(RateLimitProperties props, ObjectMapper mapper) {
    this.props = props;
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !props.enabled() || classify(request) == null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Group group = classify(request);
    int limit = group == Group.AUTH ? props.authPerMinute() : props.publicPerMinute();
    if (buckets.size() > MAX_TRACKED_CLIENTS) {
      buckets.clear(); // crude eviction; limits simply restart
    }
    Bucket bucket =
        buckets.computeIfAbsent(
            group + ":" + request.getRemoteAddr(),
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(limit)
                            .refillIntervally(limit, Duration.ofMinutes(1))
                            .build())
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    long retryAfter = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(retryAfter));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(
        response.getOutputStream(),
        GlobalExceptionHandler.problem(
            ErrorCode.RATE_LIMITED, "Too many requests; retry in " + retryAfter + " seconds"));
  }

  private Group classify(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if (method.equals("POST")
        && (path.equals("/v1/auth/login") || path.equals("/v1/auth/register"))) {
      return Group.AUTH;
    }
    boolean publicRead =
        method.equals("GET")
            && (matcher.match("/v1/businesses/*/availability", path)
                || matcher.match("/v1/businesses/*/public", path)
                || matcher.match("/v1/bookings/*", path)
                || matcher.match("/v1/staff/*/calendar.ics", path));
    boolean publicWrite =
        method.equals("POST")
            && (path.equals("/v1/holds")
                || path.equals("/v1/bookings")
                || matcher.match("/v1/bookings/*/reschedule", path)
                || matcher.match("/v1/bookings/*/cancel", path));
    return publicRead || publicWrite ? Group.PUBLIC : null;
  }
}
