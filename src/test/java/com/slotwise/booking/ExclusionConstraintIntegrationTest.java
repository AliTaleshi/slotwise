package com.slotwise.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.entity.Hold;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.booking.repository.HoldRepository;
import com.slotwise.common.error.ConstraintViolations;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Repository-level proof of the PostgreSQL exclusion constraints, independent of the API. */
class ExclusionConstraintIntegrationTest extends AbstractBookingTest {

  @Autowired BookingRepository bookings;
  @Autowired HoldRepository holds;

  static final Instant T9 = Instant.parse(NINE);

  Booking booking(UUID staff, Instant start, int minutes, int bufferAfter) {
    return new Booking(
        UUID.fromString(businessId),
        staff,
        UUID.fromString(serviceId),
        null,
        "G",
        "g@example.com",
        null,
        start,
        start.plus(Duration.ofMinutes(minutes)),
        Duration.ZERO,
        Duration.ofMinutes(bufferAfter),
        new BigDecimal("1.00"),
        "EUR",
        null,
        null,
        T9);
  }

  Hold hold(UUID staff, Instant start, int minutes) {
    return new Hold(
        UUID.fromString(businessId),
        staff,
        UUID.fromString(serviceId),
        start,
        start.plus(Duration.ofMinutes(minutes)),
        start,
        start.plus(Duration.ofMinutes(minutes)),
        start.plus(Duration.ofDays(1)),
        T9);
  }

  @Test
  void overlappingBookingsForOneStaffAreRejected() {
    UUID mia = UUID.fromString(miaId);
    bookings.saveAndFlush(booking(mia, T9, 60, 0));
    assertThatThrownBy(
            () -> bookings.saveAndFlush(booking(mia, T9.plus(Duration.ofMinutes(30)), 60, 0)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .matches(ConstraintViolations::isExclusionViolation);
  }

  @Test
  void bufferIsPartOfTheBlockedRange() {
    UUID mia = UUID.fromString(miaId);
    bookings.saveAndFlush(booking(mia, T9, 60, 15)); // blocks 09:00-10:15
    assertThatThrownBy(
            () -> bookings.saveAndFlush(booking(mia, T9.plus(Duration.ofMinutes(60)), 30, 0)))
        .matches(ConstraintViolations::isExclusionViolation);
    bookings.saveAndFlush(booking(mia, T9.plus(Duration.ofMinutes(75)), 30, 0)); // 10:15 is fine
  }

  @Test
  void adjacentRangesAreAllowedBecauseRangesAreHalfOpen() throws Exception {
    UUID mia = UUID.fromString(miaId);
    bookings.saveAndFlush(booking(mia, T9, 60, 0));
    bookings.saveAndFlush(booking(mia, T9.plus(Duration.ofMinutes(60)), 60, 0));
    bookings.saveAndFlush(booking(mia, T9.minus(Duration.ofMinutes(60)), 60, 0));
  }

  @Test
  void differentStaffMayOverlap() throws Exception {
    UUID mia = UUID.fromString(miaId);
    UUID tom = UUID.fromString(addStaff("Tom", null, null));
    bookings.saveAndFlush(booking(mia, T9, 60, 0));
    bookings.saveAndFlush(booking(tom, T9, 60, 0));
  }

  @Test
  void cancelledBookingsDoNotBlock() {
    UUID mia = UUID.fromString(miaId);
    Booking first = bookings.saveAndFlush(booking(mia, T9, 60, 0));
    first.cancel(T9);
    bookings.saveAndFlush(first);
    bookings.saveAndFlush(booking(mia, T9, 60, 0));
  }

  @Test
  void overlappingHoldsAreRejectedButNonOverlappingAreFine() {
    UUID mia = UUID.fromString(miaId);
    holds.saveAndFlush(hold(mia, T9, 60));
    assertThatThrownBy(() -> holds.saveAndFlush(hold(mia, T9.plus(Duration.ofMinutes(59)), 60)))
        .matches(ConstraintViolations::isExclusionViolation);
    holds.saveAndFlush(hold(mia, T9.plus(Duration.ofMinutes(60)), 60));
  }

  @Test
  void generatedRangeMatchesBlockedColumns() {
    UUID mia = UUID.fromString(miaId);
    Booking saved = bookings.saveAndFlush(booking(mia, T9, 60, 15));
    var jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(
            com.slotwise.support.PostgresTestSupport.dataSource());
    String range =
        jdbc.queryForObject(
            "select time_range::text from bookings where id = ?", String.class, saved.getId());
    assertThat(range).contains("2030-01-14 08:00:00+00").contains("2030-01-14 09:15:00+00");
  }
}
