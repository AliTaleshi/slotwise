package com.slotwise.offering.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/** A bookable service (named "offering" to avoid confusion with Spring's {@code @Service}). */
@Entity
@Table(name = "offerings")
public class Offering {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String name;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(nullable = false)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "buffer_before", nullable = false)
  private int bufferBefore;

  @Column(name = "buffer_after", nullable = false)
  private int bufferAfter;

  @Column(nullable = false)
  private boolean active = true;

  protected Offering() {}

  public Offering(
      UUID businessId,
      String name,
      int durationMinutes,
      BigDecimal price,
      String currency,
      int bufferBefore,
      int bufferAfter) {
    this.id = UUID.randomUUID();
    this.businessId = businessId;
    this.name = name;
    this.durationMinutes = durationMinutes;
    this.price = price;
    this.currency = currency;
    this.bufferBefore = bufferBefore;
    this.bufferAfter = bufferAfter;
  }

  public Duration duration() {
    return Duration.ofMinutes(durationMinutes);
  }

  public UUID getId() {
    return id;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(int durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getBufferBefore() {
    return bufferBefore;
  }

  public void setBufferBefore(int bufferBefore) {
    this.bufferBefore = bufferBefore;
  }

  public int getBufferAfter() {
    return bufferAfter;
  }

  public void setBufferAfter(int bufferAfter) {
    this.bufferAfter = bufferAfter;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
