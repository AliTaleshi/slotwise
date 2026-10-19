package com.slotwise.booking.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published inside the booking transaction. Listeners that talk to the outside world
 * must use {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public final class BookingEvents {

  private BookingEvents() {}

  public record BookingCreated(UUID bookingId, UUID actorId) {}

  public record BookingRescheduled(UUID bookingId, Instant previousStart, UUID actorId) {}

  public record BookingCancelled(UUID bookingId, UUID actorId, boolean byOwner) {}

  public record BookingStatusChanged(
      UUID bookingId, String previousStatus, String status, UUID actorId) {}
}
