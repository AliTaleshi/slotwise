package com.slotwise.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.support.AbstractIntegrationTest;
import com.slotwise.support.ApiTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class BusinessSetupIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  ApiTestSupport api;

  @BeforeEach
  void setUp() {
    api = new ApiTestSupport(mvc, json);
  }

  JsonNode createBusiness(String token, String slug) throws Exception {
    return api.send(
        post("/v1/businesses"),
        token,
        Map.of("name", "Sunrise Dental", "slug", slug, "timeZone", "Europe/Berlin"),
        201);
  }

  String slug() {
    return "biz-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void ownerCreatesBusinessWithDefaultPolicies() throws Exception {
    String owner = api.register("OWNER");
    JsonNode b = createBusiness(owner, slug());
    assertThat(b.get("timeZone").asText()).isEqualTo("Europe/Berlin");
    assertThat(b.get("minNoticeMinutes").asInt()).isEqualTo(120);
    assertThat(b.get("maxAdvanceDays").asInt()).isEqualTo(60);
    assertThat(b.get("cancelCutoffMinutes").asInt()).isEqualTo(1440);
    assertThat(b.get("slotIntervalMinutes").asInt()).isEqualTo(15);
  }

  @Test
  void customerCannotCreateBusiness() throws Exception {
    String customer = api.register("CUSTOMER");
    api.send(
        post("/v1/businesses"),
        customer,
        Map.of("name", "X", "slug", slug(), "timeZone", "UTC"),
        403);
  }

  @Test
  void invalidTimeZoneAndSlugRejected() throws Exception {
    String owner = api.register("OWNER");
    JsonNode bad =
        api.send(
            post("/v1/businesses"),
            owner,
            Map.of("name", "X", "slug", "Bad Slug", "timeZone", "Mars/Olympus"),
            400);
    assertThat(bad.get("code").asText()).isEqualTo("VALIDATION_FAILED");
    assertThat(bad.get("errors")).hasSize(2);
    api.send(
        post("/v1/businesses"),
        owner,
        Map.of("name", "X", "slug", slug(), "timeZone", "+02:00"),
        400);
  }

  @Test
  void duplicateSlugIsConflict() throws Exception {
    String slug = slug();
    createBusiness(api.register("OWNER"), slug);
    JsonNode err =
        api.send(
            post("/v1/businesses"),
            api.register("OWNER"),
            Map.of("name", "Other", "slug", slug, "timeZone", "UTC"),
            409);
    assertThat(err.get("code").asText()).isEqualTo("SLUG_TAKEN");
  }

  @Test
  void patchUpdatesOnlyGivenFieldsAndChecksSlotInterval() throws Exception {
    String owner = api.register("OWNER");
    String id = createBusiness(owner, slug()).get("id").asText();
    JsonNode updated =
        api.send(
            patch("/v1/businesses/" + id),
            owner,
            Map.of("minNoticeMinutes", 30, "timeZone", "Asia/Tehran"),
            200);
    assertThat(updated.get("minNoticeMinutes").asInt()).isEqualTo(30);
    assertThat(updated.get("timeZone").asText()).isEqualTo("Asia/Tehran");
    assertThat(updated.get("name").asText()).isEqualTo("Sunrise Dental");
    api.send(patch("/v1/businesses/" + id), owner, Map.of("slotIntervalMinutes", 25), 400);
  }

  @Test
  void ownerOfAnotherBusinessIsDeniedEverywhere() throws Exception {
    String ownerA = api.register("OWNER");
    String ownerB = api.register("OWNER");
    String bizA = createBusiness(ownerA, slug()).get("id").asText();

    String serviceA =
        api.send(
                post("/v1/businesses/" + bizA + "/services"),
                ownerA,
                Map.of(
                    "name", "Cleaning", "durationMinutes", 30, "price", "50.00", "currency", "EUR"),
                201)
            .get("id")
            .asText();
    String staffA =
        api.send(
                post("/v1/businesses/" + bizA + "/staff"),
                ownerA,
                Map.of("name", "Mia", "serviceIds", java.util.List.of(serviceA)),
                201)
            .get("id")
            .asText();
    String timeOffA =
        api.send(
                post("/v1/staff/" + staffA + "/time-off"),
                ownerA,
                Map.of("startsAt", "2030-02-01T00:00:00Z", "endsAt", "2030-02-02T00:00:00Z"),
                201)
            .get("id")
            .asText();

    api.send(get("/v1/businesses/" + bizA), ownerB, null, 403);
    api.send(patch("/v1/businesses/" + bizA), ownerB, Map.of("name", "Hacked"), 403);
    api.send(get("/v1/businesses/" + bizA + "/services"), ownerB, null, 403);
    api.send(
        post("/v1/businesses/" + bizA + "/services"),
        ownerB,
        Map.of("name", "X", "durationMinutes", 30, "price", "1.00", "currency", "EUR"),
        403);
    api.send(
        patch("/v1/businesses/" + bizA + "/services/" + serviceA),
        ownerB,
        Map.of("name", "X"),
        403);
    api.send(delete("/v1/businesses/" + bizA + "/services/" + serviceA), ownerB, null, 403);
    api.send(get("/v1/businesses/" + bizA + "/staff"), ownerB, null, 403);
    api.send(delete("/v1/businesses/" + bizA + "/staff/" + staffA), ownerB, null, 403);
    api.send(
        put("/v1/staff/" + staffA + "/working-hours"),
        ownerB,
        Map.of("entries", java.util.List.of()),
        403);
    api.send(get("/v1/staff/" + staffA + "/time-off"), ownerB, null, 403);
    api.send(delete("/v1/staff/" + staffA + "/time-off/" + timeOffA), ownerB, null, 403);

    // and nothing was changed
    assertThat(api.send(get("/v1/businesses/" + bizA), ownerA, null, 200).get("name").asText())
        .isEqualTo("Sunrise Dental");
  }

  @Test
  void cannotAssignAnotherBusinessesServiceToStaff() throws Exception {
    String ownerA = api.register("OWNER");
    String ownerB = api.register("OWNER");
    String bizA = createBusiness(ownerA, slug()).get("id").asText();
    String bizB = createBusiness(ownerB, slug()).get("id").asText();
    String serviceB =
        api.send(
                post("/v1/businesses/" + bizB + "/services"),
                ownerB,
                Map.of(
                    "name", "Cleaning", "durationMinutes", 30, "price", "50.00", "currency", "EUR"),
                201)
            .get("id")
            .asText();
    api.send(
        post("/v1/businesses/" + bizA + "/staff"),
        ownerA,
        Map.of("name", "Mia", "serviceIds", java.util.List.of(serviceB)),
        400);
  }

  @Test
  void workingHoursSupportSplitShiftsAndRejectOverlaps() throws Exception {
    String owner = api.register("OWNER");
    String biz = createBusiness(owner, slug()).get("id").asText();
    String staff =
        api.send(post("/v1/businesses/" + biz + "/staff"), owner, Map.of("name", "Mia"), 201)
            .get("id")
            .asText();

    JsonNode saved =
        api.send(
            put("/v1/staff/" + staff + "/working-hours"),
            owner,
            Map.of(
                "entries",
                java.util.List.of(
                    Map.of("dayOfWeek", "MONDAY", "start", "14:00", "end", "18:00"),
                    Map.of("dayOfWeek", "MONDAY", "start", "09:00", "end", "13:00"))),
            200);
    assertThat(saved.get("entries")).hasSize(2);
    assertThat(saved.at("/entries/0/start").asText()).isEqualTo("09:00:00");

    api.send(
        put("/v1/staff/" + staff + "/working-hours"),
        owner,
        Map.of(
            "entries",
            java.util.List.of(
                Map.of("dayOfWeek", "MONDAY", "start", "09:00", "end", "13:00"),
                Map.of("dayOfWeek", "MONDAY", "start", "12:00", "end", "15:00"))),
        400);
    api.send(
        put("/v1/staff/" + staff + "/working-hours"),
        owner,
        Map.of(
            "entries",
            java.util.List.of(Map.of("dayOfWeek", "TUESDAY", "start", "10:00", "end", "09:00"))),
        400);

    // failed replacements leave the previous hours intact; a valid PUT replaces them
    assertThat(
            api.send(get("/v1/staff/" + staff + "/working-hours"), owner, null, 200).get("entries"))
        .hasSize(2);
    JsonNode replaced =
        api.send(
            put("/v1/staff/" + staff + "/working-hours"),
            owner,
            Map.of(
                "entries",
                java.util.List.of(Map.of("dayOfWeek", "FRIDAY", "start", "08:00", "end", "12:00"))),
            200);
    assertThat(replaced.get("entries")).hasSize(1);
  }

  @Test
  void staffLoginCanManageOwnTimeOffButNotOthers() throws Exception {
    String owner = api.register("OWNER");
    String biz = createBusiness(owner, slug()).get("id").asText();
    String email = "staff-" + UUID.randomUUID() + "@example.com";
    String mia =
        api.send(
                post("/v1/businesses/" + biz + "/staff"),
                owner,
                Map.of("name", "Mia", "email", email, "password", "password123"),
                201)
            .get("id")
            .asText();
    String tom =
        api.send(post("/v1/businesses/" + biz + "/staff"), owner, Map.of("name", "Tom"), 201)
            .get("id")
            .asText();
    String miaToken = api.login(email, "password123");

    JsonNode off =
        api.send(
            post("/v1/staff/" + mia + "/time-off"),
            miaToken,
            Map.of(
                "startsAt",
                "2030-03-01T00:00:00Z",
                "endsAt",
                "2030-03-08T00:00:00Z",
                "reason",
                "Vacation"),
            201);
    assertThat(off.get("kind").asText()).isEqualTo("OFF");
    assertThat(api.send(get("/v1/staff/" + mia + "/time-off"), miaToken, null, 200)).hasSize(1);
    api.send(get("/v1/staff/" + tom + "/time-off"), miaToken, null, 403);
    api.send(
        post("/v1/staff/" + tom + "/time-off"),
        miaToken,
        Map.of("startsAt", "2030-03-01T00:00:00Z", "endsAt", "2030-03-08T00:00:00Z"),
        403);
    // staff cannot change their weekly hours
    api.send(
        put("/v1/staff/" + mia + "/working-hours"),
        miaToken,
        Map.of("entries", java.util.List.of()),
        403);
    api.send(
        delete("/v1/staff/" + mia + "/time-off/" + off.get("id").asText()), miaToken, null, 204);
    api.send(
        post("/v1/staff/" + mia + "/time-off"),
        miaToken,
        Map.of("startsAt", "2030-03-08T00:00:00Z", "endsAt", "2030-03-01T00:00:00Z"),
        400);
  }

  @Test
  void publicProfileShowsOnlyActiveServicesAndStaffWithoutAuth() throws Exception {
    String owner = api.register("OWNER");
    String slug = slug();
    String biz = createBusiness(owner, slug).get("id").asText();
    String keep =
        api.send(
                post("/v1/businesses/" + biz + "/services"),
                owner,
                Map.of(
                    "name",
                    "Cleaning",
                    "durationMinutes",
                    30,
                    "price",
                    "50.00",
                    "currency",
                    "EUR",
                    "bufferAfter",
                    10),
                201)
            .get("id")
            .asText();
    String drop =
        api.send(
                post("/v1/businesses/" + biz + "/services"),
                owner,
                Map.of("name", "Old", "durationMinutes", 30, "price", "10.00", "currency", "EUR"),
                201)
            .get("id")
            .asText();
    api.send(
        post("/v1/businesses/" + biz + "/staff"),
        owner,
        Map.of("name", "Mia", "serviceIds", java.util.List.of(keep)),
        201);
    String gone =
        api.send(post("/v1/businesses/" + biz + "/staff"), owner, Map.of("name", "Gone"), 201)
            .get("id")
            .asText();
    api.send(delete("/v1/businesses/" + biz + "/services/" + drop), owner, null, 204);
    api.send(delete("/v1/businesses/" + biz + "/staff/" + gone), owner, null, 204);

    JsonNode pub = api.send(get("/v1/businesses/" + slug + "/public"), null, null, 200);
    assertThat(pub.get("services")).hasSize(1);
    assertThat(pub.at("/services/0/name").asText()).isEqualTo("Cleaning");
    assertThat(pub.get("staff")).hasSize(1);
    assertThat(pub.at("/staff/0/serviceIds/0").asText()).isEqualTo(keep);
    api.send(get("/v1/businesses/unknown-slug/public"), null, null, 404);
  }

  @Test
  void ownerUpdatesServicesAndStaffAndEveryChangeIsAudited() throws Exception {
    String owner = api.register("OWNER");
    String biz = createBusiness(owner, slug()).get("id").asText();
    String service =
        api.send(
                post("/v1/businesses/" + biz + "/services"),
                owner,
                Map.of(
                    "name", "Cleaning", "durationMinutes", 30, "price", "50.00", "currency", "EUR"),
                201)
            .get("id")
            .asText();
    String staff =
        api.send(post("/v1/businesses/" + biz + "/staff"), owner, Map.of("name", "Mia"), 201)
            .get("id")
            .asText();

    JsonNode updated =
        api.send(
            patch("/v1/businesses/" + biz + "/services/" + service),
            owner,
            Map.of(
                "name",
                "Deep cleaning",
                "durationMinutes",
                45,
                "price",
                "75.50",
                "currency",
                "USD",
                "bufferBefore",
                5,
                "bufferAfter",
                10,
                "active",
                true),
            200);
    assertThat(updated.get("name").asText()).isEqualTo("Deep cleaning");
    assertThat(updated.get("durationMinutes").asInt()).isEqualTo(45);
    assertThat(updated.get("price").decimalValue()).isEqualByComparingTo("75.50");
    assertThat(updated.get("currency").asText()).isEqualTo("USD");
    assertThat(updated.get("bufferBefore").asInt()).isEqualTo(5);
    assertThat(updated.get("bufferAfter").asInt()).isEqualTo(10);
    api.send(
        patch("/v1/businesses/" + biz + "/services/" + service),
        owner,
        Map.of("durationMinutes", 1),
        400);
    api.send(
        patch("/v1/businesses/" + biz + "/services/" + UUID.randomUUID()),
        owner,
        Map.of("name", "x"),
        404);

    JsonNode staffUpdated =
        api.send(
            patch("/v1/businesses/" + biz + "/staff/" + staff),
            owner,
            Map.of(
                "name",
                "Mia K.",
                "email",
                "mia@example.com",
                "serviceIds",
                java.util.List.of(service)),
            200);
    assertThat(staffUpdated.get("name").asText()).isEqualTo("Mia K.");
    assertThat(staffUpdated.get("serviceIds")).hasSize(1);
    JsonNode cleared =
        api.send(
            patch("/v1/businesses/" + biz + "/staff/" + staff),
            owner,
            Map.of("serviceIds", java.util.List.of()),
            200);
    assertThat(cleared.get("serviceIds")).isEmpty();
    api.send(
        patch("/v1/businesses/" + biz + "/staff/" + UUID.randomUUID()),
        owner,
        Map.of("name", "x"),
        404);

    // a service id from another business is not found under this business
    String otherOwner = api.register("OWNER");
    String otherBiz = createBusiness(otherOwner, slug()).get("id").asText();
    api.send(
        patch("/v1/businesses/" + otherBiz + "/services/" + service),
        otherOwner,
        Map.of("name", "x"),
        404);

    api.send(delete("/v1/businesses/" + biz + "/services/" + service), owner, null, 204);
    api.send(delete("/v1/businesses/" + biz + "/staff/" + staff), owner, null, 204);

    JsonNode log = api.send(get("/v1/businesses/" + biz + "/audit-log?size=50"), owner, null, 200);
    java.util.List<String> actions = new java.util.ArrayList<>();
    log.get("content").forEach(n -> actions.add(n.get("action").asText()));
    assertThat(actions)
        .contains("SERVICE_CHANGED", "SERVICE_DEACTIVATED", "STAFF_CHANGED", "STAFF_DEACTIVATED");
  }
}
