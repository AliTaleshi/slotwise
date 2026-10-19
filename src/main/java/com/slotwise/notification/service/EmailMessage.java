package com.slotwise.notification.service;

/** What a booking email says, before rendering. */
public enum EmailMessage {
  CONFIRMATION("email/booking-confirmation", "Your booking is confirmed"),
  RESCHEDULED("email/booking-rescheduled", "Your booking was rescheduled"),
  CANCELLED("email/booking-cancelled", "Your booking was cancelled"),
  REMINDER("email/booking-reminder", "Reminder: your upcoming appointment");

  private final String template;
  private final String subject;

  EmailMessage(String template, String subject) {
    this.template = template;
    this.subject = subject;
  }

  public String template() {
    return template;
  }

  public String subject() {
    return subject;
  }
}
