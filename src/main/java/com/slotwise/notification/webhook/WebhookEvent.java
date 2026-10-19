package com.slotwise.notification.webhook;

public enum WebhookEvent {
  BOOKING_CREATED("booking.created"),
  BOOKING_RESCHEDULED("booking.rescheduled"),
  BOOKING_CANCELLED("booking.cancelled");

  private final String type;

  WebhookEvent(String type) {
    this.type = type;
  }

  public String type() {
    return type;
  }

  public static WebhookEvent fromType(String type) {
    for (WebhookEvent e : values()) {
      if (e.type.equals(type)) {
        return e;
      }
    }
    throw new IllegalArgumentException("Unknown webhook event: " + type);
  }
}
