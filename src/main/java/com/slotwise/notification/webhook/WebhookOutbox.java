package com.slotwise.notification.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.event.BookingEvents.BookingCancelled;
import com.slotwise.booking.event.BookingEvents.BookingCreated;
import com.slotwise.booking.event.BookingEvents.BookingRescheduled;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.notification.webhook.entity.WebhookDelivery;
import com.slotwise.notification.webhook.entity.WebhookEndpoint;
import com.slotwise.notification.webhook.repository.WebhookDeliveryRepository;
import com.slotwise.notification.webhook.repository.WebhookEndpointRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transactional outbox for webhooks. Delivery rows are written <em>inside</em> the booking
 * transaction (so an event can never be lost or announced for a rolled-back booking), and the
 * actual HTTP calls happen after commit on another thread.
 */
@Component
public class WebhookOutbox {

  /** Published in the booking transaction once delivery rows exist. */
  public record DeliveriesQueued(List<UUID> deliveryIds) {}

  private final BookingRepository bookings;
  private final WebhookEndpointRepository endpoints;
  private final WebhookDeliveryRepository deliveries;
  private final WebhookDispatcher dispatcher;
  private final ApplicationEventPublisher events;
  private final ObjectMapper mapper;
  private final Clock clock;

  public WebhookOutbox(
      BookingRepository bookings,
      WebhookEndpointRepository endpoints,
      WebhookDeliveryRepository deliveries,
      WebhookDispatcher dispatcher,
      ApplicationEventPublisher events,
      ObjectMapper mapper,
      Clock clock) {
    this.bookings = bookings;
    this.endpoints = endpoints;
    this.deliveries = deliveries;
    this.dispatcher = dispatcher;
    this.events = events;
    this.mapper = mapper;
    this.clock = clock;
  }

  @EventListener
  public void onCreated(BookingCreated e) {
    enqueue(WebhookEvent.BOOKING_CREATED, e.bookingId(), null);
  }

  @EventListener
  public void onRescheduled(BookingRescheduled e) {
    enqueue(WebhookEvent.BOOKING_RESCHEDULED, e.bookingId(), e.previousStart());
  }

  @EventListener
  public void onCancelled(BookingCancelled e) {
    enqueue(WebhookEvent.BOOKING_CANCELLED, e.bookingId(), null);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void dispatchAfterCommit(DeliveriesQueued queued) {
    queued.deliveryIds().forEach(dispatcher::deliver);
  }

  private void enqueue(WebhookEvent event, UUID bookingId, Instant previousStart) {
    Booking booking = bookings.findById(bookingId).orElseThrow();
    List<WebhookEndpoint> targets =
        endpoints.findByBusinessIdAndActiveTrueOrderByCreatedAtAsc(booking.getBusinessId()).stream()
            .filter(ep -> ep.subscribesTo(event))
            .toList();
    if (targets.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    List<UUID> ids = new ArrayList<>();
    for (WebhookEndpoint endpoint : targets) {
      UUID id = UUID.randomUUID();
      deliveries.save(
          new WebhookDelivery(
              id,
              endpoint.getId(),
              event.type(),
              payload(id, event, booking, previousStart, now),
              now));
      ids.add(id);
    }
    events.publishEvent(new DeliveriesQueued(ids));
  }

  private String payload(
      UUID deliveryId, WebhookEvent event, Booking b, Instant previousStart, Instant now) {
    Map<String, Object> booking = new LinkedHashMap<>();
    booking.put("id", b.getId());
    booking.put("businessId", b.getBusinessId());
    booking.put("status", b.getStatus());
    booking.put("staffId", b.getStaffId());
    booking.put("serviceId", b.getOfferingId());
    booking.put("start", b.getStartsAt());
    booking.put("end", b.getEndsAt());
    booking.put("price", b.getPrice());
    booking.put("currency", b.getCurrency());
    booking.put("guestName", b.getGuestName());
    booking.put("guestEmail", b.getGuestEmail());
    booking.put("guestPhone", b.getGuestPhone());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("booking", booking);
    if (previousStart != null) {
      data.put("previousStart", previousStart);
    }
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("id", deliveryId);
    envelope.put("type", event.type());
    envelope.put("createdAt", now);
    envelope.put("data", data);
    try {
      return mapper.writeValueAsString(envelope);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
