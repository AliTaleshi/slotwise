package com.slotwise.booking.service;

import com.slotwise.availability.service.BusyTimeSource;
import com.slotwise.common.time.TimeInterval;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Busy time = non-cancelled bookings plus holds that have not expired yet (as of {@code now}). */
@Component
class JdbcBusyTimeSource implements BusyTimeSource {

  private static final UUID NIL = new UUID(0, 0);

  private static final String SQL =
      """
      SELECT staff_id, blocked_from, blocked_to FROM bookings
       WHERE staff_id IN (:staffIds) AND status <> 'CANCELLED'
         AND time_range && tstzrange(:from, :to) AND id <> :exclude
      UNION ALL
      SELECT staff_id, blocked_from, blocked_to FROM holds
       WHERE staff_id IN (:staffIds) AND expires_at > :now
         AND time_range && tstzrange(:from, :to)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  JdbcBusyTimeSource(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<UUID, List<TimeInterval>> busy(
      Collection<UUID> staffIds, TimeInterval window, Instant now, UUID excludeBookingId) {
    if (staffIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<TimeInterval>> result = new HashMap<>();
    jdbc.query(
        SQL,
        new MapSqlParameterSource()
            .addValue("staffIds", staffIds)
            .addValue("from", utc(window.start()))
            .addValue("to", utc(window.end()))
            .addValue("now", utc(now))
            .addValue("exclude", excludeBookingId == null ? NIL : excludeBookingId),
        rs -> {
          UUID staffId = rs.getObject("staff_id", UUID.class);
          Instant from = rs.getObject("blocked_from", OffsetDateTime.class).toInstant();
          Instant to = rs.getObject("blocked_to", OffsetDateTime.class).toInstant();
          result.computeIfAbsent(staffId, k -> new ArrayList<>()).add(new TimeInterval(from, to));
        });
    return result;
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
