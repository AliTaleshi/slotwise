package com.slotwise.notification.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Minimal RFC 5545 writer for single-event calendars (invites, cancellations, staff feeds). */
public final class IcsBuilder {

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  public record Event(
      UUID id,
      Instant start,
      Instant end,
      String summary,
      String description,
      String location,
      boolean cancelled,
      int sequence) {}

  private IcsBuilder() {}

  public static String calendar(List<Event> events, String method, String name, Instant now) {
    StringBuilder sb = new StringBuilder();
    line(sb, "BEGIN:VCALENDAR");
    line(sb, "VERSION:2.0");
    line(sb, "PRODID:-//Slotwise//Booking//EN");
    line(sb, "CALSCALE:GREGORIAN");
    if (method != null) {
      line(sb, "METHOD:" + method);
    }
    if (name != null) {
      line(sb, "X-WR-CALNAME:" + escape(name));
    }
    for (Event e : events) {
      line(sb, "BEGIN:VEVENT");
      line(sb, "UID:" + e.id() + "@slotwise");
      line(sb, "DTSTAMP:" + STAMP.format(now));
      line(sb, "DTSTART:" + STAMP.format(e.start()));
      line(sb, "DTEND:" + STAMP.format(e.end()));
      line(sb, "SEQUENCE:" + e.sequence());
      line(sb, "SUMMARY:" + escape(e.summary()));
      if (e.description() != null) {
        line(sb, "DESCRIPTION:" + escape(e.description()));
      }
      if (e.location() != null) {
        line(sb, "LOCATION:" + escape(e.location()));
      }
      line(sb, "STATUS:" + (e.cancelled() ? "CANCELLED" : "CONFIRMED"));
      line(sb, "END:VEVENT");
    }
    line(sb, "END:VCALENDAR");
    return sb.toString();
  }

  public static String single(Event event, boolean cancel, Instant now) {
    return calendar(List.of(event), cancel ? "CANCEL" : "REQUEST", null, now);
  }

  static String escape(String text) {
    return text.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n");
  }

  /** Appends a content line folded at 75 octets, CRLF-terminated. */
  private static void line(StringBuilder out, String content) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= 75) {
      out.append(content).append("\r\n");
      return;
    }
    StringBuilder current = new StringBuilder();
    int octets = 0;
    int limit = 75;
    for (int i = 0; i < content.length(); ) {
      int cp = content.codePointAt(i);
      String ch = new String(Character.toChars(cp));
      int size = ch.getBytes(StandardCharsets.UTF_8).length;
      if (octets + size > limit) {
        out.append(current).append("\r\n ");
        current.setLength(0);
        octets = 0;
        limit = 74; // continuation lines start with a space
      }
      current.append(ch);
      octets += size;
      i += Character.charCount(cp);
    }
    out.append(current).append("\r\n");
  }
}
