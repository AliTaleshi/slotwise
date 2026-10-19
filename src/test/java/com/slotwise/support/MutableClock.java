package com.slotwise.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that only moves when told to. */
public class MutableClock extends Clock {

  private volatile Instant now;
  private final ZoneId zone;

  public MutableClock(Instant start) {
    this(start, ZoneOffset.UTC);
  }

  private MutableClock(Instant start, ZoneId zone) {
    this.now = start;
    this.zone = zone;
  }

  public void set(Instant instant) {
    this.now = instant;
  }

  public void advance(Duration d) {
    this.now = now.plus(d);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(now, zone);
  }

  @Override
  public Instant instant() {
    return now;
  }
}
