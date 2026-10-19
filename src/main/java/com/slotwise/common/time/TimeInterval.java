package com.slotwise.common.time;

import java.time.Instant;

/** Half-open interval [start, end) on the UTC timeline. */
public record TimeInterval(Instant start, Instant end) {

  public TimeInterval {
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("start must be before end: " + start + " / " + end);
    }
  }

  public boolean overlaps(TimeInterval other) {
    return start.isBefore(other.end) && other.start.isBefore(end);
  }

  public boolean contains(TimeInterval other) {
    return !other.start.isBefore(start) && !other.end.isAfter(end);
  }
}
