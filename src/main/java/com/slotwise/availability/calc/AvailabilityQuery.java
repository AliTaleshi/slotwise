package com.slotwise.availability.calc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Parameters of one availability calculation. {@code from} and {@code to} are inclusive local dates
 * in {@code zone}.
 */
public record AvailabilityQuery(
    ZoneId zone,
    Instant now,
    Duration minNotice,
    int maxAdvanceDays,
    int slotIntervalMinutes,
    Duration duration,
    Duration bufferBefore,
    Duration bufferAfter,
    LocalDate from,
    LocalDate to,
    List<StaffSchedule> staff) {

  public AvailabilityQuery {
    if (slotIntervalMinutes <= 0) {
      throw new IllegalArgumentException("slotIntervalMinutes must be positive");
    }
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("duration must be positive");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must not be before from");
    }
  }
}
