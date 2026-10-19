package com.slotwise.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.support.AbstractIntegrationTest;
import com.slotwise.support.ApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class AvailabilityIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  ApiTestSupport api;
  String owner;
  String slug;
  String businessId;
  String serviceId;
  String miaId;

  @BeforeEach
  void setUp() throws Exception {
    api = new ApiTestSupport(mvc, json);
    owner = api.register("OWNER");
    slug = "av-" + UUID.randomUUID().toString().substring(0, 8);
    businessId =
        api.send(
                post("/v1/businesses"),
                owner,
                Map.of(
                    "name",
                    "Berlin Clinic",
                    "slug",
                    slug,
                    "timeZone",
                    "Europe/Berlin",
                    "slotIntervalMinutes",
                    30,
                    "maxAdvanceDays",
                    200,
                    "minNoticeMinutes",
                    0),
                201)
            .get("id")
            .asText();
    serviceId =
        api.send(
                post("/v1/businesses/" + businessId + "/services"),
                owner,
                Map.of(
                    "name", "Check-up", "durationMinutes", 60, "price", "80.00", "currency", "EUR"),
                201)
            .get("id")
            .asText();
    miaId = addStaff("Mia", List.of(serviceId));
  }

  String addStaff(String name, List<String> serviceIds) throws Exception {
    return api.send(
            post("/v1/businesses/" + businessId + "/staff"),
            owner,
            Map.of("name", name, "serviceIds", serviceIds),
            201)
        .get("id")
        .asText();
  }

  void setHours(String staffId, String day, String start, String end) throws Exception {
    api.send(
        put("/v1/staff/" + staffId + "/working-hours"),
        owner,
        Map.of("entries", List.of(Map.of("dayOfWeek", day, "start", start, "end", end))),
        200);
  }

  JsonNode availability(String from, String to, String staffId) throws Exception {
    String url =
        "/v1/businesses/%s/availability?serviceId=%s&from=%s&to=%s"
                .formatted(slug, serviceId, from, to)
            + (staffId == null ? "" : "&staffId=" + staffId);
    return api.send(get(url), null, null, 200);
  }

  List<String> starts(JsonNode response, int dayIndex) {
    List<String> out = new ArrayList<>();
    response.at("/days/" + dayIndex + "/slots").forEach(s -> out.add(s.get("start").asText()));
    return out;
  }

  @Test
  void publicEndpointReturnsSlotsWithBusinessOffsets() throws Exception {
    setHours(miaId, "MONDAY", "09:00", "11:00");
    JsonNode res = availability("2030-01-14", "2030-01-14", null);
    assertThat(res.get("timeZone").asText()).isEqualTo("Europe/Berlin");
    assertThat(starts(res, 0))
        .containsExactly(
            "2030-01-14T09:00:00+01:00", "2030-01-14T09:30:00+01:00", "2030-01-14T10:00:00+01:00");
    assertThat(res.at("/days/0/slots/0/staffIds/0").asText()).isEqualTo(miaId);
  }

  @Test
  void springForwardDayHasNoNonexistentSlots() throws Exception {
    setHours(miaId, "SUNDAY", "01:00", "05:00");
    JsonNode res = availability("2030-03-31", "2030-03-31", null);
    assertThat(starts(res, 0))
        .containsExactly(
            "2030-03-31T01:00:00+01:00",
            "2030-03-31T01:30:00+01:00",
            "2030-03-31T03:00:00+02:00",
            "2030-03-31T03:30:00+02:00",
            "2030-03-31T04:00:00+02:00");
  }

  @Test
  void anyStaffModeUnionsAndStaffFilterNarrows() throws Exception {
    String tom = addStaff("Tom", List.of(serviceId));
    setHours(miaId, "MONDAY", "09:00", "10:00");
    setHours(tom, "MONDAY", "09:00", "11:00");

    JsonNode any = availability("2030-01-14", "2030-01-14", null);
    assertThat(starts(any, 0)).hasSize(3);
    assertThat(any.at("/days/0/slots/0/staffIds")).hasSize(2);

    JsonNode onlyMia = availability("2030-01-14", "2030-01-14", miaId);
    assertThat(starts(onlyMia, 0)).hasSize(1);
  }

  @Test
  void staffWhoDoNotPerformTheServiceAreExcluded() throws Exception {
    String other = addStaff("Other", List.of());
    setHours(other, "MONDAY", "09:00", "17:00");
    setHours(miaId, "MONDAY", "09:00", "10:00");
    JsonNode res = availability("2030-01-14", "2030-01-14", null);
    assertThat(starts(res, 0)).hasSize(1);
    api.send(
        get(
            "/v1/businesses/%s/availability?serviceId=%s&from=2030-01-14&to=2030-01-14&staffId=%s"
                .formatted(slug, serviceId, other)),
        null,
        null,
        404);
  }

  @Test
  void timeOffAndExtraWindowsAffectAvailability() throws Exception {
    setHours(miaId, "MONDAY", "09:00", "11:00");
    String off =
        api.send(
                post("/v1/staff/" + miaId + "/time-off"),
                owner,
                Map.of("startsAt", "2030-01-14T00:00:00Z", "endsAt", "2030-01-15T00:00:00Z"),
                201)
            .get("id")
            .asText();
    assertThat(starts(availability("2030-01-14", "2030-01-14", null), 0)).isEmpty();

    api.send(delete("/v1/staff/" + miaId + "/time-off/" + off), owner, null, 204);
    api.send(
        post("/v1/staff/" + miaId + "/time-off"),
        owner,
        Map.of(
            "kind", "EXTRA", "startsAt", "2030-01-15T14:00:00Z", "endsAt", "2030-01-15T16:00:00Z"),
        201);
    JsonNode res = availability("2030-01-14", "2030-01-15", null);
    assertThat(starts(res, 0)).hasSize(3);
    assertThat(starts(res, 1))
        .containsExactly(
            "2030-01-15T15:00:00+01:00", "2030-01-15T15:30:00+01:00", "2030-01-15T16:00:00+01:00")
        .hasSize(3);
  }

  @Test
  void inactiveServiceAndUnknownBusinessAre404() throws Exception {
    api.send(delete("/v1/businesses/" + businessId + "/services/" + serviceId), owner, null, 204);
    api.send(
        get(
            "/v1/businesses/%s/availability?serviceId=%s&from=2030-01-14&to=2030-01-14"
                .formatted(slug, serviceId)),
        null,
        null,
        404);
    api.send(
        get(
            "/v1/businesses/nope/availability?serviceId=%s&from=2030-01-14&to=2030-01-14"
                .formatted(serviceId)),
        null,
        null,
        404);
  }

  @Test
  void invalidRangesAreRejected() throws Exception {
    api.send(
        get(
            "/v1/businesses/%s/availability?serviceId=%s&from=2030-01-15&to=2030-01-14"
                .formatted(slug, serviceId)),
        null,
        null,
        400);
    api.send(
        get(
            "/v1/businesses/%s/availability?serviceId=%s&from=2030-01-01&to=2030-06-01"
                .formatted(slug, serviceId)),
        null,
        null,
        400);
    api.send(
        get(
            "/v1/businesses/%s/availability?serviceId=%s&from=garbage&to=2030-06-01"
                .formatted(slug, serviceId)),
        null,
        null,
        400);
  }
}
