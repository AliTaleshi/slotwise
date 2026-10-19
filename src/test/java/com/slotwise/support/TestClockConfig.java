package com.slotwise.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

  public static final Instant START = Instant.parse("2030-01-07T08:00:00Z");

  @Bean
  @Primary
  public MutableClock testClock() {
    return new MutableClock(START);
  }
}
