package com.slotwise.notification.service;

import com.slotwise.booking.event.BookingEvents.BookingCancelled;
import com.slotwise.booking.event.BookingEvents.BookingCreated;
import com.slotwise.booking.event.BookingEvents.BookingRescheduled;
import com.slotwise.booking.repository.BookingRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends emails only after the booking transaction has committed, and on another thread, so a slow
 * or failing mail server can never block or roll back a booking.
 */
@Component
public class BookingNotificationListener {

  private static final Logger log = LoggerFactory.getLogger(BookingNotificationListener.class);

  private final BookingEmailService emails;
  private final BookingRepository bookings;

  public BookingNotificationListener(BookingEmailService emails, BookingRepository bookings) {
    this.emails = emails;
    this.bookings = bookings;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreated(BookingCreated event) {
    send(EmailMessage.CONFIRMATION, event.bookingId());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRescheduled(BookingRescheduled event) {
    send(EmailMessage.RESCHEDULED, event.bookingId());
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCancelled(BookingCancelled event) {
    send(EmailMessage.CANCELLED, event.bookingId());
  }

  private void send(EmailMessage kind, UUID bookingId) {
    try {
      bookings.findById(bookingId).ifPresent(b -> emails.send(kind, b));
    } catch (RuntimeException e) {
      log.error("Failed to send {} email for booking {}", kind, bookingId, e);
    }
  }
}
