package com.slotwise.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "businesses")
public class Business {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;

  @Column(name = "time_zone", nullable = false)
  private String timeZone;

  @Column(name = "contact_email")
  private String contactEmail;

  @Column(name = "contact_phone")
  private String contactPhone;

  @Column(name = "min_notice_minutes", nullable = false)
  private int minNoticeMinutes;

  @Column(name = "max_advance_days", nullable = false)
  private int maxAdvanceDays;

  @Column(name = "cancel_cutoff_minutes", nullable = false)
  private int cancelCutoffMinutes;

  @Column(name = "slot_interval_minutes", nullable = false)
  private int slotIntervalMinutes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Business() {}

  public Business(UUID ownerId, String name, String slug, String timeZone, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.name = name;
    this.slug = slug;
    this.timeZone = timeZone;
    this.createdAt = createdAt;
    this.minNoticeMinutes = 120;
    this.maxAdvanceDays = 60;
    this.cancelCutoffMinutes = 1440;
    this.slotIntervalMinutes = 15;
  }

  public ZoneId zone() {
    return ZoneId.of(timeZone);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  public String getContactPhone() {
    return contactPhone;
  }

  public void setContactPhone(String contactPhone) {
    this.contactPhone = contactPhone;
  }

  public int getMinNoticeMinutes() {
    return minNoticeMinutes;
  }

  public void setMinNoticeMinutes(int minNoticeMinutes) {
    this.minNoticeMinutes = minNoticeMinutes;
  }

  public int getMaxAdvanceDays() {
    return maxAdvanceDays;
  }

  public void setMaxAdvanceDays(int maxAdvanceDays) {
    this.maxAdvanceDays = maxAdvanceDays;
  }

  public int getCancelCutoffMinutes() {
    return cancelCutoffMinutes;
  }

  public void setCancelCutoffMinutes(int cancelCutoffMinutes) {
    this.cancelCutoffMinutes = cancelCutoffMinutes;
  }

  public int getSlotIntervalMinutes() {
    return slotIntervalMinutes;
  }

  public void setSlotIntervalMinutes(int slotIntervalMinutes) {
    this.slotIntervalMinutes = slotIntervalMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
