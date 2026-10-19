package com.slotwise.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotwise.booking.service.CancellationPolicy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CancellationPolicyTest {

  static final Instant START = Instant.parse("2030-01-14T09:00:00Z");
  static final Duration CUTOFF = Duration.ofHours(24);

  @Test
  void allowedWellBeforeTheCutoff() {
    assertThat(
            CancellationPolicy.isWithinWindow(Instant.parse("2030-01-10T00:00:00Z"), START, CUTOFF))
        .isTrue();
  }

  @Test
  void allowedExactlyAtTheCutoff() {
    assertThat(CancellationPolicy.isWithinWindow(START.minus(CUTOFF), START, CUTOFF)).isTrue();
  }

  @Test
  void deniedOneSecondAfterTheCutoff() {
    assertThat(CancellationPolicy.isWithinWindow(START.minus(CUTOFF).plusSeconds(1), START, CUTOFF))
        .isFalse();
  }

  @Test
  void deniedAfterTheStart() {
    assertThat(CancellationPolicy.isWithinWindow(START.plusSeconds(1), START, CUTOFF)).isFalse();
  }

  @Test
  void zeroCutoffAllowsUntilStart() {
    assertThat(CancellationPolicy.isWithinWindow(START, START, Duration.ZERO)).isTrue();
    assertThat(CancellationPolicy.isWithinWindow(START.plusMillis(1), START, Duration.ZERO))
        .isFalse();
  }
}
