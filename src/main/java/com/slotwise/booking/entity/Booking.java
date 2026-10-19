package com.slotwise.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "staff_id", nullable = false)
  private UUID staffId;

  @Column(name = "offering_id", nullable = false)
  private UUID offeringId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "guest_name", nullable = false)
  private String guestName;

  @Column(name = "guest_email", nullable = false)
  private String guestEmail;

  @Column(name = "guest_phone")
  private String guestPhone;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "blocked_from", nullable = false)
  private Instant blockedFrom;

  @Column(name = "blocked_to", nullable = false)
  private Instant blockedTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BookingStatus status;

  @Column(nullable = false)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Version private long version;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "hold_id")
  private UUID holdId;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Booking() {}

  public Booking(
      UUID businessId,
      UUID staffId,
      UUID offeringId,
      UUID customerId,
      String guestName,
      String guestEmail,
      String guestPhone,
      Instant startsAt,
      Instant endsAt,
      Duration bufferBefore,
      Duration bufferAfter,
      BigDecimal price,
      String currency,
      String idempotencyKey,
      UUID holdId,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.businessId = businessId;
    this.staffId = staffId;
    this.offeringId = offeringId;
    this.customerId = customerId;
    this.guestName = guestName;
    this.guestEmail = guestEmail;
    this.guestPhone = guestPhone;
    this.price = price;
    this.currency = currency;
    this.idempotencyKey = idempotencyKey;
    this.holdId = holdId;
    this.createdAt = createdAt;
    this.status = BookingStatus.CONFIRMED;
    moveTo(staffId, startsAt, endsAt, bufferBefore, bufferAfter);
  }

  /** Places the booking at a new time (and staff member), recomputing the blocked range. */
  public void moveTo(
      UUID staffId, Instant startsAt, Instant endsAt, Duration bufferBefore, Duration bufferAfter) {
    this.staffId = staffId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.blockedFrom = startsAt.minus(bufferBefore);
    this.blockedTo = endsAt.plus(bufferAfter);
  }

  public void cancel(Instant now) {
    this.status = BookingStatus.CANCELLED;
    this.cancelledAt = now;
  }

  public void setStatus(BookingStatus status) {
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public UUID getStaffId() {
    return staffId;
  }

  public UUID getOfferingId() {
    return offeringId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getGuestName() {
    return guestName;
  }

  public String getGuestEmail() {
    return guestEmail;
  }

  public String getGuestPhone() {
    return guestPhone;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Instant getBlockedFrom() {
    return blockedFrom;
  }

  public Instant getBlockedTo() {
    return blockedTo;
  }

  public BookingStatus getStatus() {
    return status;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getCurrency() {
    return currency;
  }

  public long getVersion() {
    return version;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getHoldId() {
    return holdId;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
