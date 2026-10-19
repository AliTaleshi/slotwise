package com.slotwise.booking.service;

import java.time.Duration;
import java.time.Instant;

/** Pure cancel/reschedule cutoff rule. */
public final class CancellationPolicy {

  private CancellationPolicy() {}

  /** True while {@code now} is at or before {@code start - cutoff}. */
  public static boolean isWithinWindow(Instant now, Instant start, Duration cutoff) {
    return !now.isAfter(start.minus(cutoff));
  }
}
