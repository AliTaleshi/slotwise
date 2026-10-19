package com.slotwise.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "working_hours")
public class WorkingHours {

  @Id private UUID id;

  @Column(name = "staff_id", nullable = false)
  private UUID staffId;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false)
  private DayOfWeek dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  protected WorkingHours() {}

  public WorkingHours(UUID staffId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    this.id = UUID.randomUUID();
    this.staffId = staffId;
    this.dayOfWeek = dayOfWeek;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public UUID getStaffId() {
    return staffId;
  }

  public DayOfWeek getDayOfWeek() {
    return dayOfWeek;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }
}
