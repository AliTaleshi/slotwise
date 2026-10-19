package com.slotwise.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {

  private final JdbcTemplate jdbc;

  public ReminderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Claims the reminder: true only for the single caller whose insert wins. The primary key makes
   * this safe across threads and app instances.
   */
  public boolean claim(UUID bookingId, String type, Instant now) {
    return jdbc.update(
            "INSERT INTO reminders_sent (booking_id, type, sent_at) VALUES (?, ?, ?)"
                + " ON CONFLICT DO NOTHING",
            bookingId,
            type,
            java.sql.Timestamp.from(now))
        == 1;
  }

  /** Gives the claim back after a failed send so the next run retries. */
  public void release(UUID bookingId, String type) {
    jdbc.update("DELETE FROM reminders_sent WHERE booking_id = ? AND type = ?", bookingId, type);
  }

  public List<UUID> dueBookingIds(String type, Instant startFrom, Instant startTo) {
    return jdbc.queryForList(
        "SELECT b.id FROM bookings b WHERE b.status = 'CONFIRMED'"
            + " AND b.starts_at >= ? AND b.starts_at < ?"
            + " AND NOT EXISTS (SELECT 1 FROM reminders_sent r WHERE r.booking_id = b.id AND r.type = ?)",
        UUID.class,
        java.sql.Timestamp.from(startFrom),
        java.sql.Timestamp.from(startTo),
        type);
  }
}
