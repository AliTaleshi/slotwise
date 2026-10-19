package com.slotwise.availability.service;

import com.slotwise.common.time.TimeInterval;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Placeholder until bookings exist (milestone 5 replaces it). */
@Component
class NoBusyTimeSource implements BusyTimeSource {

  @Override
  public Map<UUID, List<TimeInterval>> busy(
      Collection<UUID> staffIds, TimeInterval window, Instant now) {
    return Map.of();
  }
}
