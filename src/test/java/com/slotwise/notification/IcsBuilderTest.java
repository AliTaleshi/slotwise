package com.slotwise.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotwise.notification.service.IcsBuilder;
import com.slotwise.notification.service.IcsBuilder.Event;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IcsBuilderTest {

  static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");
  static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  Event event(String summary, boolean cancelled) {
    return new Event(
        ID,
        Instant.parse("2030-01-14T08:00:00Z"),
        Instant.parse("2030-01-14T09:00:00Z"),
        summary,
        "With Mia",
        "Berlin Clinic",
        cancelled,
        2);
  }

  @Test
  void writesUtcTimesStableUidAndCrlf() {
    String ics = IcsBuilder.single(event("Check-up", false), false, NOW);
    assertThat(ics)
        .startsWith("BEGIN:VCALENDAR\r\n")
        .contains("METHOD:REQUEST\r\n")
        .contains("UID:11111111-1111-1111-1111-111111111111@slotwise\r\n")
        .contains("DTSTART:20300114T080000Z\r\n")
        .contains("DTEND:20300114T090000Z\r\n")
        .contains("DTSTAMP:20300107T080000Z\r\n")
        .contains("SEQUENCE:2\r\n")
        .contains("STATUS:CONFIRMED\r\n")
        .endsWith("END:VCALENDAR\r\n");
  }

  @Test
  void cancellationUsesCancelMethodAndStatus() {
    String ics = IcsBuilder.single(event("Check-up", true), true, NOW);
    assertThat(ics).contains("METHOD:CANCEL\r\n").contains("STATUS:CANCELLED\r\n");
  }

  @Test
  void escapesSpecialCharacters() {
    String ics = IcsBuilder.single(event("Cut, color; wash\\dry", false), false, NOW);
    assertThat(ics).contains("SUMMARY:Cut\\, color\\; wash\\\\dry\r\n");
  }

  @Test
  void foldsLongLinesAtSeventyFiveOctets() {
    String ics = IcsBuilder.single(event("x".repeat(200), false), false, NOW);
    for (String line : ics.split("\r\n")) {
      assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
          .isLessThanOrEqualTo(75);
    }
    // unfolding restores the original
    assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + "x".repeat(200));
  }

  @Test
  void foldingNeverSplitsAMultiByteCharacter() {
    String ics = IcsBuilder.single(event("é".repeat(100), false), false, NOW);
    assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + "é".repeat(100));
  }
}
