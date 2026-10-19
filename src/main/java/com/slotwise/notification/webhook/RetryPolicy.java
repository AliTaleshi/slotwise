package com.slotwise.notification.webhook;

import java.time.Duration;
import java.util.List;

/** Exponential-ish backoff between delivery attempts. */
public final class RetryPolicy {

  private static final List<Duration> DELAYS =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(30),
          Duration.ofHours(2),
          Duration.ofHours(12));

  /** First attempt plus one per delay. */
  public static final int MAX_ATTEMPTS = DELAYS.size() + 1;

  private RetryPolicy() {}

  /**
   * @param attemptsMade attempts completed so far (>= 1)
   * @return wait before the next attempt, or {@code null} when attempts are exhausted
   */
  public static Duration delayAfter(int attemptsMade) {
    return attemptsMade >= MAX_ATTEMPTS ? null : DELAYS.get(attemptsMade - 1);
  }
}
