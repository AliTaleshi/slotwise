package com.slotwise.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.slotwise.booking.service.HoldCleanupJob;
import com.slotwise.notification.job.ReminderJob;
import com.slotwise.support.CapturingMailSender.Sent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Emails after commit, .ics attachments, reminders exactly once, hold cleanup. */
class NotificationIntegrationTest extends com.slotwise.booking.AbstractBookingTest {

  @Autowired ReminderJob reminderJob;
  @Autowired HoldCleanupJob holdCleanup;
  @Autowired javax.sql.DataSource dataSource;

  /** Jobs look at every business, so start each test without other tests' bookings. */
  @org.junit.jupiter.api.BeforeEach
  void cleanBookings() {
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.update("delete from bookings");
    jdbc.update("delete from holds");
  }

  List<Sent> awaitMails(int count) {
    await().atMost(Duration.ofSeconds(10)).until(() -> mail.outbox().size() >= count);
    return mail.outbox();
  }

  @Test
  void confirmationIsSentAfterCommitWithLocalTimesAndIcs() throws Exception {
    JsonNode booking = bookAsGuest(NINE);

    Sent sent = awaitMails(1).getFirst();
    assertThat(sent.to()).isEqualTo("sam@example.com");
    assertThat(sent.subject()).contains("confirmed").contains("Berlin Clinic");
    assertThat(sent.html())
        .contains("Monday, January 14, 2030")
        .contains("09:00")
        .contains("Europe/Berlin")
        .contains("Check-up")
        .contains(booking.get("manageToken").asText());
    assertThat(sent.attachments()).containsExactly("appointment.ics");
    assertThat(sent.ics())
        .contains("METHOD:REQUEST")
        .contains("DTSTART:20300114T080000Z")
        .contains("UID:" + booking.get("id").asText() + "@slotwise");
  }

  @Test
  void rescheduleAndCancelSendEmailsToo() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    String id = booking.get("id").asText();
    String token = booking.get("manageToken").asText();
    awaitMails(1);

    manage(
        post("/v1/bookings/" + id + "/reschedule"),
        token,
        Map.of("newStart", "2030-01-14T13:00:00Z"),
        200);
    Sent moved = awaitMails(2).get(1);
    assertThat(moved.subject()).contains("rescheduled");
    assertThat(moved.html()).contains("14:00");
    assertThat(moved.ics()).contains("DTSTART:20300114T130000Z");

    manage(post("/v1/bookings/" + id + "/cancel"), token, null, 200);
    Sent cancelled = awaitMails(3).get(2);
    assertThat(cancelled.subject()).contains("cancelled");
    assertThat(cancelled.ics()).contains("METHOD:CANCEL").contains("STATUS:CANCELLED");
  }

  @Test
  void failingMailServerNeverBreaksTheBooking() throws Exception {
    mail.setFailing(true);
    JsonNode booking = bookAsGuest(NINE);
    assertThat(booking.get("status").asText()).isEqualTo("CONFIRMED");
    Thread.sleep(300);
    assertThat(mail.outbox()).isEmpty();
    // booking is really there
    manage(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/v1/bookings/" + booking.get("id").asText()),
        booking.get("manageToken").asText(),
        null,
        200);
  }

  @Test
  void remindersGoOutExactlyOnceEach() throws Exception {
    bookAsGuest(NINE); // starts 2030-01-14T08:00Z
    awaitMails(1);
    mail.clear();

    clock.set(Instant.parse("2030-01-13T07:00:00Z")); // 25 h before: nothing due
    reminderJob.run();
    assertThat(mail.outbox()).isEmpty();

    clock.set(Instant.parse("2030-01-13T08:00:00Z")); // exactly 24 h before
    reminderJob.run();
    reminderJob.run(); // a second run, or a second instance, must not resend
    assertThat(mail.outbox()).hasSize(1);
    assertThat(mail.outbox().getFirst().subject()).contains("Reminder");
    assertThat(mail.outbox().getFirst().attachments()).isEmpty();

    clock.set(Instant.parse("2030-01-13T08:30:00Z"));
    reminderJob.run();
    assertThat(mail.outbox()).hasSize(1);

    clock.set(Instant.parse("2030-01-14T07:00:00Z")); // 1 h before
    reminderJob.run();
    reminderJob.run();
    assertThat(mail.outbox()).hasSize(2);

    Integer rows =
        new JdbcTemplate(dataSource)
            .queryForObject("select count(*) from reminders_sent", Integer.class);
    assertThat(rows).isGreaterThanOrEqualTo(2);
  }

  @Test
  void bookingMadeShortlyBeforeStartSkipsTheDayReminder() throws Exception {
    clock.set(Instant.parse("2030-01-14T04:00:00Z")); // 4 h before the 08:00Z start
    bookAsGuest(NINE);
    awaitMails(1);
    mail.clear();
    reminderJob.run();
    assertThat(mail.outbox()).isEmpty();
  }

  @Test
  void cancelledBookingsGetNoReminder() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    api.send(post("/v1/bookings/" + booking.get("id").asText() + "/cancel"), ownerToken, null, 200);
    awaitMails(2);
    mail.clear();
    clock.set(Instant.parse("2030-01-13T08:00:00Z"));
    reminderJob.run();
    assertThat(mail.outbox()).isEmpty();
  }

  @Test
  void failedReminderIsRetriedOnTheNextRun() throws Exception {
    bookAsGuest(NINE);
    awaitMails(1);
    mail.clear();
    clock.set(Instant.parse("2030-01-13T08:00:00Z"));
    mail.setFailing(true);
    reminderJob.run();
    assertThat(mail.outbox()).isEmpty();
    mail.setFailing(false);
    reminderJob.run();
    assertThat(mail.outbox()).hasSize(1);
  }

  @Test
  void cleanupJobRemovesOnlyExpiredHolds() throws Exception {
    hold(NINE, null, 201);
    clock.advance(Duration.ofMinutes(5));
    hold("2030-01-14T13:00:00Z", null, 201);
    var jdbc = new JdbcTemplate(dataSource);
    Integer before =
        jdbc.queryForObject(
            "select count(*) from holds where business_id = ?::uuid", Integer.class, businessId);
    assertThat(before).isEqualTo(2);

    clock.advance(Duration.ofMinutes(6)); // first hold is 11 min old, second 6 min
    assertThat(holdCleanup.run()).isEqualTo(1);
    Integer after =
        jdbc.queryForObject(
            "select count(*) from holds where business_id = ?::uuid", Integer.class, businessId);
    assertThat(after).isEqualTo(1);
    assertThat(holdCleanup.run()).isZero();
  }
}
