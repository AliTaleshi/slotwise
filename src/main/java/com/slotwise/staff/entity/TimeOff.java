package com.slotwise.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_off")
public class TimeOff {

  @Id private UUID id;

  @Column(name = "staff_id", nullable = false)
  private UUID staffId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TimeOffKind kind;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  private String reason;

  protected TimeOff() {}

  public TimeOff(UUID staffId, TimeOffKind kind, Instant startsAt, Instant endsAt, String reason) {
    this.id = UUID.randomUUID();
    this.staffId = staffId;
    this.kind = kind;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.reason = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getStaffId() {
    return staffId;
  }

  public TimeOffKind getKind() {
    return kind;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getReason() {
    return reason;
  }
}
