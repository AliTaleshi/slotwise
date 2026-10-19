package com.slotwise.staff.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "staff")
public class Staff {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private String name;

  private String email;

  @Column(nullable = false)
  private boolean active = true;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "staff_offerings", joinColumns = @JoinColumn(name = "staff_id"))
  @Column(name = "offering_id")
  private Set<UUID> offeringIds = new HashSet<>();

  protected Staff() {}

  public Staff(UUID businessId, UUID userId, String name, String email) {
    this.id = UUID.randomUUID();
    this.businessId = businessId;
    this.userId = userId;
    this.name = name;
    this.email = email;
  }

  public UUID getId() {
    return id;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Set<UUID> getOfferingIds() {
    return offeringIds;
  }
}
