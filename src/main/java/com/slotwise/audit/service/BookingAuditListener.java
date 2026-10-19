package com.slotwise.audit.service;

import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.event.BookingEvents.BookingCancelled;
import com.slotwise.booking.event.BookingEvents.BookingCreated;
import com.slotwise.booking.event.BookingEvents.BookingRescheduled;
import com.slotwise.booking.event.BookingEvents.BookingStatusChanged;
import com.slotwise.booking.repository.BookingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronous listeners: audit rows are written in the same transaction as the booking change. */
@Component
public class BookingAuditListener {

  private final AuditService audit;
  private final BookingRepository bookings;

  public BookingAuditListener(AuditService audit, BookingRepository bookings) {
    this.audit = audit;
    this.bookings = bookings;
  }

  @EventListener
  public void onCreated(BookingCreated e) {
    Booking b = load(e.bookingId());
    audit.record(
        e.actorId(), b.getBusinessId(), "BOOKING_CREATED", "BOOKING", b.getId(), null, snapshot(b));
  }

  @EventListener
  public void onRescheduled(BookingRescheduled e) {
    Booking b = load(e.bookingId());
    Map<String, Object> before = new LinkedHashMap<>(snapshot(b));
    before.put("start", e.previousStart());
    before.remove("end");
    before.remove("staffId");
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("start", b.getStartsAt());
    after.put("end", b.getEndsAt());
    after.put("staffId", b.getStaffId());
    audit.record(
        e.actorId(), b.getBusinessId(), "BOOKING_RESCHEDULED", "BOOKING", b.getId(), before, after);
  }

  @EventListener
  public void onCancelled(BookingCancelled e) {
    Booking b = load(e.bookingId());
    audit.record(
        e.actorId(),
        b.getBusinessId(),
        e.byOwner() ? "BOOKING_CANCELLED_BY_OWNER" : "BOOKING_CANCELLED",
        "BOOKING",
        b.getId(),
        Map.of("status", "CONFIRMED"),
        Map.of("status", "CANCELLED"));
  }

  @EventListener
  public void onStatusChanged(BookingStatusChanged e) {
    Booking b = load(e.bookingId());
    audit.record(
        e.actorId(),
        b.getBusinessId(),
        "BOOKING_STATUS_CHANGED",
        "BOOKING",
        b.getId(),
        Map.of("status", e.previousStatus()),
        Map.of("status", e.status()));
  }

  private Booking load(UUID id) {
    return bookings.findById(id).orElseThrow();
  }

  private static Map<String, Object> snapshot(Booking b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("status", b.getStatus());
    m.put("staffId", b.getStaffId());
    m.put("serviceId", b.getOfferingId());
    m.put("start", b.getStartsAt());
    m.put("end", b.getEndsAt());
    m.put("guestEmail", b.getGuestEmail());
    m.put("price", b.getPrice());
    return m;
  }
}
