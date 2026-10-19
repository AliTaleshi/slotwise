package com.slotwise.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Temporary reservation of a slot while the customer fills in their details. */
@Entity
@Table(name = "holds")
public class Hold {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "staff_id", nullable = false)
  private UUID staffId;

  @Column(name = "offering_id", nullable = false)
  private UUID offeringId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "blocked_from", nullable = false)
  private Instant blockedFrom;

  @Column(name = "blocked_to", nullable = false)
  private Instant blockedTo;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Hold() {}

  public Hold(
      UUID businessId,
      UUID staffId,
      UUID offeringId,
      Instant startsAt,
      Instant endsAt,
      Instant blockedFrom,
      Instant blockedTo,
      Instant expiresAt,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.businessId = businessId;
    this.staffId = staffId;
    this.offeringId = offeringId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.blockedFrom = blockedFrom;
    this.blockedTo = blockedTo;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
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

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
