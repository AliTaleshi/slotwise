package com.slotwise.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.support.AbstractIntegrationTest;
import com.slotwise.support.ApiTestSupport;
import com.slotwise.support.MutableClock;
import com.slotwise.support.TestClockConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A Berlin business with one service (60 min, 15 min buffer after) and staff "Mia" working Mon-Fri
 * 09:00-17:00. The clock starts Monday 2030-01-07 08:00Z; the bookable Monday is 2030-01-14.
 */
public abstract class AbstractBookingTest extends AbstractIntegrationTest {

  /** 09:00 Berlin on 2030-01-14. */
  public static final String NINE = "2030-01-14T08:00:00Z";

  static final String TEN = "2030-01-14T09:00:00Z";
  static final String ELEVEN = "2030-01-14T10:00:00Z";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected MutableClock clock;
  @Autowired protected com.slotwise.support.CapturingMailSender mail;

  protected ApiTestSupport api;
  protected String ownerToken;
  protected String businessId;
  protected String slug;
  protected String serviceId;
  protected String miaId;

  @BeforeEach
  void setUpBusiness() throws Exception {
    clock.set(TestClockConfig.START);
    mail.clear();
    api = new ApiTestSupport(mvc, json);
    ownerToken = api.register("OWNER");
    slug = "bk-" + UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> business = new HashMap<>();
    business.put("name", "Berlin Clinic");
    business.put("slug", slug);
    business.put("timeZone", "Europe/Berlin");
    business.put("slotIntervalMinutes", 30);
    business.put("minNoticeMinutes", 120);
    business.put("maxAdvanceDays", 90);
    business.put("cancelCutoffMinutes", 1440);
    businessId = api.send(post("/v1/businesses"), ownerToken, business, 201).get("id").asText();
    serviceId =
        api.send(
                post("/v1/businesses/" + businessId + "/services"),
                ownerToken,
                Map.of(
                    "name", "Check-up",
                    "durationMinutes", 60,
                    "price", "80.00",
                    "currency", "EUR",
                    "bufferAfter", 15),
                201)
            .get("id")
            .asText();
    miaId = addStaff("Mia", null, null);
  }

  protected String addStaff(String name, String email, String password) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("serviceIds", List.of(serviceId));
    if (email != null) {
      body.put("email", email);
      body.put("password", password);
    }
    String id =
        api.send(post("/v1/businesses/" + businessId + "/staff"), ownerToken, body, 201)
            .get("id")
            .asText();
    List<Map<String, String>> entries = new ArrayList<>();
    for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")) {
      entries.add(Map.of("dayOfWeek", day, "start", "09:00", "end", "17:00"));
    }
    api.send(
        put("/v1/staff/" + id + "/working-hours"), ownerToken, Map.of("entries", entries), 200);
    return id;
  }

  protected JsonNode hold(String startUtc, String staffId, int expected) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("serviceId", serviceId);
    body.put("start", startUtc);
    if (staffId != null) {
      body.put("staffId", staffId);
    }
    return api.send(post("/v1/holds"), null, body, expected);
  }

  protected JsonNode book(String holdId, String key, String bearer, int expected) throws Exception {
    MockHttpServletRequestBuilder req = post("/v1/bookings");
    if (key != null) {
      req.header("Idempotency-Key", key);
    }
    Map<String, Object> body = new HashMap<>();
    body.put("holdId", holdId);
    if (bearer == null) {
      body.put("guestName", "Sam Guest");
      body.put("guestEmail", "Sam@Example.com");
    }
    return api.send(req, bearer, body, expected);
  }

  /** Hold + confirm as a guest; returns the booking response. */
  protected JsonNode bookAsGuest(String startUtc) throws Exception {
    String holdId = hold(startUtc, null, 201).get("holdId").asText();
    return book(holdId, null, null, 201);
  }

  protected JsonNode availabilityMonday() throws Exception {
    return api.send(
        get(
            "/v1/businesses/%s/availability?serviceId=%s&from=2030-01-14&to=2030-01-14"
                .formatted(slug, serviceId)),
        null,
        null,
        200);
  }

  protected List<String> mondayStartsUtc() throws Exception {
    List<String> out = new ArrayList<>();
    availabilityMonday()
        .at("/days/0/slots")
        .forEach(
            s ->
                out.add(
                    java.time.OffsetDateTime.parse(s.get("start").asText())
                        .toInstant()
                        .toString()));
    return out;
  }

  protected JsonNode manage(
      MockHttpServletRequestBuilder request, String manageToken, Object body, int expected)
      throws Exception {
    if (manageToken != null) {
      request.header("X-Manage-Token", manageToken);
    }
    return api.send(request, null, body, expected);
  }
}
