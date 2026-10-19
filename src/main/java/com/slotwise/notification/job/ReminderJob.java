package com.slotwise.notification.job;

import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.entity.BookingStatus;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.notification.repository.ReminderRepository;
import com.slotwise.notification.service.BookingEmailService;
import com.slotwise.notification.service.EmailMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the 24 hour and 1 hour reminders, each exactly once per booking.
 *
 * <p>A reminder is due while the appointment starts within {@code (lead - grace, lead]} from now.
 * The window keeps a booking made two hours ahead from receiving a pointless "24 hours" reminder,
 * and the {@code reminders_sent} primary key makes repeated or concurrent runs harmless.
 */
@Component
public class ReminderJob {

  private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

  static final Duration LEAD_24H = Duration.ofHours(24);
  static final Duration GRACE_24H = Duration.ofHours(2);
  static final Duration LEAD_1H = Duration.ofHours(1);
  static final Duration GRACE_1H = Duration.ofMinutes(30);

  private final ReminderRepository reminders;
  private final BookingRepository bookings;
  private final BookingEmailService emails;
  private final Clock clock;

  public ReminderJob(
      ReminderRepository reminders,
      BookingRepository bookings,
      BookingEmailService emails,
      Clock clock) {
    this.reminders = reminders;
    this.bookings = bookings;
    this.emails = emails;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${slotwise.jobs.reminders-interval:PT1M}")
  @SchedulerLock(name = "send-reminders", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
  public void run() {
    sendDue("REMINDER_24H", LEAD_24H, GRACE_24H);
    sendDue("REMINDER_1H", LEAD_1H, GRACE_1H);
  }

  int sendDue(String type, Duration lead, Duration grace) {
    Instant now = clock.instant();
    int sent = 0;
    // start in (now + lead - grace, now + lead]  <=>  start in [now + lead - grace, now + lead +
    // 1ns)
    for (UUID id :
        reminders.dueBookingIds(type, now.plus(lead).minus(grace), now.plus(lead).plusMillis(1))) {
      if (!reminders.claim(id, type, now)) {
        continue; // another instance or run already handled it
      }
      try {
        Booking booking = bookings.findById(id).orElseThrow();
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
          continue;
        }
        emails.send(EmailMessage.REMINDER, booking);
        sent++;
      } catch (RuntimeException e) {
        log.error("Reminder {} for booking {} failed; will retry", type, id, e);
        reminders.release(id, type);
      }
    }
    return sent;
  }
}
