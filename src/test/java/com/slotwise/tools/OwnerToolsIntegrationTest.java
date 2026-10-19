package com.slotwise.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.slotwise.booking.AbstractBookingTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Seeds: A 09:00 (completed), B 10:30 (no-show), C 13:00 (confirmed), D 14:30 (cancelled), E next
 * day.
 */
class OwnerToolsIntegrationTest extends AbstractBookingTest {

  String tomId;
  String tomEmail;
  String miaEmail;
  JsonNode a;
  JsonNode b;
  JsonNode c;
  JsonNode d;
  JsonNode e;
  String miaToken;
  String tomToken;

  @BeforeEach
  void seed() throws Exception {
    // Mia gets a login by recreating her as a second member with credentials
    miaEmail = "mia-" + UUID.randomUUID() + "@example.com";
    String miaLogin = addStaff("Mia Login", miaEmail, "password123");
    miaId = miaLogin;
    tomEmail = "tom-" + UUID.randomUUID() + "@example.com";
    tomId = addStaff("Tom Login", tomEmail, "password123");
    miaToken = api.login(miaEmail, "password123");
    tomToken = api.login(tomEmail, "password123");

    a = book(hold(NINE, miaId, 201).get("holdId").asText(), null, null, 201);
    b = book(hold("2030-01-14T09:30:00Z", miaId, 201).get("holdId").asText(), null, null, 201);
    c = book(hold("2030-01-14T12:00:00Z", miaId, 201).get("holdId").asText(), null, null, 201);
    d = book(hold("2030-01-14T13:30:00Z", miaId, 201).get("holdId").asText(), null, null, 201);
    e = book(hold("2030-01-15T09:00:00Z", tomId, 201).get("holdId").asText(), null, null, 201);

    api.send(post("/v1/bookings/" + d.get("id").asText() + "/cancel"), ownerToken, null, 200);
    clock.set(Instant.parse("2030-01-14T10:00:00Z"));
    api.send(
        patch("/v1/bookings/" + a.get("id").asText() + "/status"),
        miaToken,
        Map.of("status", "COMPLETED"),
        200);
    api.send(
        patch("/v1/bookings/" + b.get("id").asText() + "/status"),
        miaToken,
        Map.of("status", "NO_SHOW"),
        200);
  }

  List<String> ids(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.get("content").forEach(n -> out.add(n.get("id").asText()));
    return out;
  }

  String url(String suffix) {
    return "/v1/businesses/" + businessId + "/bookings" + suffix;
  }

  @Test
  void ownerListsAllBookingsPaginatedAndOrdered() throws Exception {
    JsonNode page = api.send(get(url("?size=3")), ownerToken, null, 200);
    assertThat(page.get("totalElements").asInt()).isEqualTo(5);
    assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    assertThat(ids(page))
        .containsExactly(a.get("id").asText(), b.get("id").asText(), c.get("id").asText());
    JsonNode second = api.send(get(url("?size=3&page=1")), ownerToken, null, 200);
    assertThat(ids(second)).containsExactly(d.get("id").asText(), e.get("id").asText());
  }

  @Test
  void filtersByStaffStatusAndLocalDateRange() throws Exception {
    assertThat(ids(api.send(get(url("?staffId=" + tomId)), ownerToken, null, 200)))
        .containsExactly(e.get("id").asText());
    assertThat(ids(api.send(get(url("?status=CANCELLED")), ownerToken, null, 200)))
        .containsExactly(d.get("id").asText());
    assertThat(ids(api.send(get(url("?status=NO_SHOW&staffId=" + miaId)), ownerToken, null, 200)))
        .containsExactly(b.get("id").asText());
    assertThat(
            api.send(get(url("?from=2030-01-15&to=2030-01-15")), ownerToken, null, 200)
                .get("totalElements")
                .asInt())
        .isEqualTo(1);
    assertThat(
            api.send(get(url("?from=2030-01-14&to=2030-01-14")), ownerToken, null, 200)
                .get("totalElements")
                .asInt())
        .isEqualTo(4);
    assertThat(
            api.send(get(url("?from=2030-01-16")), ownerToken, null, 200)
                .get("totalElements")
                .asInt())
        .isZero();
    api.send(get(url("?from=2030-02-01&to=2030-01-01")), ownerToken, null, 400);
    api.send(get(url("?status=BOGUS")), ownerToken, null, 400);
  }

  @Test
  void onlyTheOwnerOfTheBusinessMayList() throws Exception {
    api.send(get(url("")), api.register("OWNER"), null, 403);
    api.send(get(url("")), api.register("CUSTOMER"), null, 403);
    api.send(get(url("")), miaToken, null, 403);
    api.send(get(url("")), null, null, 401);
  }

  @Test
  void statsCountRatesAndRevenue() throws Exception {
    JsonNode s =
        api.send(
            get("/v1/businesses/" + businessId + "/stats?from=2030-01-14&to=2030-01-16"),
            ownerToken,
            null,
            200);
    assertThat(s.get("totalBookings").asLong()).isEqualTo(5);
    assertThat(s.get("confirmed").asLong()).isEqualTo(2);
    assertThat(s.get("completed").asLong()).isEqualTo(1);
    assertThat(s.get("noShow").asLong()).isEqualTo(1);
    assertThat(s.get("cancelled").asLong()).isEqualTo(1);
    assertThat(s.get("cancellationRate").asDouble()).isEqualTo(0.2);
    assertThat(s.get("noShowRate").asDouble()).isEqualTo(0.2);
    // confirmed C + E and completed A; no-show and cancelled earn nothing
    assertThat(s.at("/revenueEstimate/EUR").decimalValue()).isEqualByComparingTo("240.00");
    assertThat(s.get("bookingsPerDay")).hasSize(3);
    assertThat(s.at("/bookingsPerDay/0/date").asText()).isEqualTo("2030-01-14");
    assertThat(s.at("/bookingsPerDay/0/bookings").asLong()).isEqualTo(3);
    assertThat(s.at("/bookingsPerDay/1/bookings").asLong()).isEqualTo(1);
    assertThat(s.at("/bookingsPerDay/2/bookings").asLong()).isZero();
  }

  @Test
  void statsForEmptyRangeAreZeroNotErrors() throws Exception {
    JsonNode s =
        api.send(
            get("/v1/businesses/" + businessId + "/stats?from=2031-01-01&to=2031-01-02"),
            ownerToken,
            null,
            200);
    assertThat(s.get("totalBookings").asLong()).isZero();
    assertThat(s.get("cancellationRate").asDouble()).isZero();
    assertThat(s.get("revenueEstimate")).isEmpty();
    api.send(
        get("/v1/businesses/" + businessId + "/stats?from=2030-01-01&to=2032-01-01"),
        ownerToken,
        null,
        400);
    api.send(
        get("/v1/businesses/" + businessId + "/stats?from=2030-01-14&to=2030-01-16"),
        api.register("OWNER"),
        null,
        403);
  }

  @Test
  void staffSeesOnlyTheirOwnNonCancelledSchedule() throws Exception {
    JsonNode mine =
        api.send(
            get("/v1/staff/" + miaId + "/schedule?from=2030-01-14&to=2030-01-15"),
            miaToken,
            null,
            200);
    assertThat(mine).hasSize(3); // A, B, C - D is cancelled
    assertThat(mine.get(0).get("id").asText()).isEqualTo(a.get("id").asText());
    api.send(
        get("/v1/staff/" + miaId + "/schedule?from=2030-01-14&to=2030-01-15"), tomToken, null, 403);
    assertThat(
            api.send(
                get("/v1/staff/" + tomId + "/schedule?from=2030-01-14&to=2030-01-15"),
                ownerToken,
                null,
                200))
        .hasSize(1);
    api.send(
        get("/v1/staff/" + miaId + "/schedule?from=2030-01-14&to=2030-01-15"),
        api.register("CUSTOMER"),
        null,
        403);
  }

  @Test
  void icalFeedWorksWithItsTokenOnly() throws Exception {
    String link =
        api.send(get("/v1/staff/" + miaId + "/calendar-link"), miaToken, null, 200)
            .get("url")
            .asText();
    assertThat(link).contains("/v1/staff/" + miaId + "/calendar.ics?token=");
    String path = link.substring(link.indexOf("/v1/"));

    String ics =
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/calendar"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(ics).startsWith("BEGIN:VCALENDAR");
    assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(4); // A, B, C
    assertThat(ics)
        .contains("UID:" + a.get("id").asText() + "@slotwise")
        .doesNotContain(d.get("id").asText());
    assertThat(ics).contains("DTSTART:20300114T080000Z");

    mvc.perform(get("/v1/staff/" + miaId + "/calendar.ics?token=wrong"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get(
                "/v1/staff/"
                    + tomId
                    + "/calendar.ics?token="
                    + path.substring(path.indexOf("token=") + 6)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/v1/staff/" + miaId + "/calendar.ics")).andExpect(status().isBadRequest());

    api.send(get("/v1/staff/" + miaId + "/calendar-link"), tomToken, null, 403);
    api.send(get("/v1/staff/" + miaId + "/calendar-link"), ownerToken, null, 200);
  }

  @Test
  void auditLogRecordsWhoDidWhatWithBeforeAndAfter() throws Exception {
    api.send(
        patch("/v1/businesses/" + businessId), ownerToken, Map.of("minNoticeMinutes", 30), 200);
    api.send(
        post("/v1/bookings/" + c.get("id").asText() + "/reschedule"),
        ownerToken,
        Map.of("newStart", "2030-01-14T14:00:00Z"),
        200);

    JsonNode log =
        api.send(
            get("/v1/businesses/" + businessId + "/audit-log?size=100"), ownerToken, null, 200);
    List<String> actions = new ArrayList<>();
    log.get("content").forEach(n -> actions.add(n.get("action").asText()));
    assertThat(actions)
        .contains(
            "BOOKING_CREATED",
            "BOOKING_CANCELLED_BY_OWNER",
            "BOOKING_STATUS_CHANGED",
            "BOOKING_RESCHEDULED",
            "BUSINESS_SETTINGS_CHANGED");
    assertThat(actions.stream().filter("BOOKING_CREATED"::equals).count()).isEqualTo(5);

    JsonNode settings = find(log, "BUSINESS_SETTINGS_CHANGED");
    assertThat(settings.at("/before/minNoticeMinutes").asInt()).isEqualTo(120);
    assertThat(settings.at("/after/minNoticeMinutes").asInt()).isEqualTo(30);
    assertThat(settings.get("actorId").asText()).isNotBlank();

    JsonNode moved = find(log, "BOOKING_RESCHEDULED");
    assertThat(moved.at("/before/start").asText()).isEqualTo("2030-01-14T12:00:00Z");
    assertThat(moved.at("/after/start").asText()).isEqualTo("2030-01-14T14:00:00Z");

    JsonNode statusChange = find(log, "BOOKING_STATUS_CHANGED");
    assertThat(statusChange.at("/before/status").asText()).isEqualTo("CONFIRMED");

    JsonNode cancelled = find(log, "BOOKING_CANCELLED_BY_OWNER");
    assertThat(cancelled.at("/after/status").asText()).isEqualTo("CANCELLED");

    assertThat(
            api.send(
                    get("/v1/businesses/" + businessId + "/audit-log?entityType=BUSINESS"),
                    ownerToken,
                    null,
                    200)
                .get("totalElements")
                .asInt())
        .isEqualTo(1);
    api.send(get("/v1/businesses/" + businessId + "/audit-log"), api.register("OWNER"), null, 403);
    api.send(get("/v1/businesses/" + businessId + "/audit-log"), miaToken, null, 403);
  }

  private static JsonNode find(JsonNode page, String action) {
    for (JsonNode n : page.get("content")) {
      if (n.get("action").asText().equals(action)) {
        return n;
      }
    }
    throw new AssertionError("no audit entry " + action);
  }
}
