package com.slotwise.availability.service;

import com.slotwise.common.time.TimeInterval;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supplies time already taken per staff member (non-cancelled bookings and unexpired holds, buffers
 * included). Holds whose {@code expires_at} is not after {@code now} must be ignored immediately,
 * even if the cleanup job has not removed them yet.
 */
public interface BusyTimeSource {

  /**
   * @param excludeBookingId a booking to ignore (the one being rescheduled), or {@code null}
   */
  Map<UUID, List<TimeInterval>> busy(
      Collection<UUID> staffIds, TimeInterval window, Instant now, UUID excludeBookingId);
}
