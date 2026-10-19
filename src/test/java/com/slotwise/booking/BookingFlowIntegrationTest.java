package com.slotwise.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingFlowIntegrationTest extends AbstractBookingTest {

  @Test
  void fullGuestFlowHoldBookViewAndSlotDisappears() throws Exception {
    assertThat(mondayStartsUtc()).contains(NINE);

    JsonNode hold = hold(NINE, null, 201);
    assertThat(hold.get("staffId").asText()).isEqualTo(miaId);
    assertThat(hold.get("start").asText()).isEqualTo("2030-01-14T09:00:00+01:00");

    JsonNode booking = book(hold.get("holdId").asText(), null, null, 201);
    assertThat(booking.get("status").asText()).isEqualTo("CONFIRMED");
    assertThat(booking.get("guestEmail").asText()).isEqualTo("sam@example.com");
    assertThat(booking.get("price").decimalValue()).isEqualByComparingTo("80.00");
    assertThat(booking.get("start").asText()).isEqualTo("2030-01-14T09:00:00+01:00");
    assertThat(booking.get("serviceName").asText()).isEqualTo("Check-up");
    String id = booking.get("id").asText();
    String token = booking.get("manageToken").asText();
    assertThat(booking.get("manageUrl").asText()).contains("/v1/bookings/" + id).contains(token);

    // 09:00 booking + 15 min buffer blocks 09:00, 09:30 and 10:00 (10:00 start needs the gap)
    assertThat(mondayStartsUtc()).doesNotContain(NINE, "2030-01-14T08:30:00Z", TEN);
    assertThat(mondayStartsUtc()).contains("2030-01-14T09:30:00Z");

    manage(get("/v1/bookings/" + id), token, null, 200);
    api.send(get("/v1/bookings/" + id + "?token=" + token), null, null, 200);
    api.send(get("/v1/bookings/" + id), null, null, 403);
    api.send(get("/v1/bookings/" + id + "?token=garbage"), null, null, 403);
    api.send(get("/v1/bookings/" + UUID.randomUUID()), null, null, 404);
  }

  @Test
  void manageTokenOnlyWorksForItsOwnBooking() throws Exception {
    JsonNode a = bookAsGuest(NINE);
    JsonNode b = bookAsGuest("2030-01-14T13:00:00Z");
    manage(get("/v1/bookings/" + b.get("id").asText()), a.get("manageToken").asText(), null, 403);
  }

  @Test
  void aSecondHoldOnTheSameSlotIsRejectedAndOverlapsToo() throws Exception {
    hold(NINE, null, 201);
    JsonNode second = hold(NINE, null, 409);
    assertThat(second.get("code").asText()).isEqualTo("SLOT_UNAVAILABLE");
    // 09:30 overlaps the held 09:00-10:00 (+buffer)
    hold("2030-01-14T08:30:00Z", null, 409);
    // availability hides it right away
    assertThat(mondayStartsUtc()).doesNotContain(NINE, "2030-01-14T08:30:00Z");
  }

  @Test
  void expiredHoldIsIgnoredImmediatelyWithoutCleanup() throws Exception {
    String holdId = hold(NINE, null, 201).get("holdId").asText();
    clock.advance(Duration.ofMinutes(10));

    assertThat(mondayStartsUtc()).contains(NINE);
    JsonNode err = book(holdId, null, null, 410);
    assertThat(err.get("code").asText()).isEqualTo("HOLD_EXPIRED");

    // a new hold takes over even though the stale row is still in the table
    hold(NINE, null, 201);
  }

  @Test
  void confirmingBeforeExpiryWorksAtTheLastMinute() throws Exception {
    String holdId = hold(NINE, null, 201).get("holdId").asText();
    clock.advance(Duration.ofMinutes(9).plusSeconds(59));
    book(holdId, null, null, 201);
  }

  @Test
  void unknownHoldIsGone() throws Exception {
    book(UUID.randomUUID().toString(), null, null, 410);
  }

  @Test
  void slotsOutsideTheGridHoursOrNoticeAreRejected() throws Exception {
    hold("2030-01-14T08:10:00Z", null, 409); // off the 30 min grid
    hold("2030-01-14T06:00:00Z", null, 409); // 07:00 local, before opening
    hold("2030-01-19T10:00:00Z", null, 409); // Saturday
    hold("2030-01-07T09:00:00Z", null, 409); // today 10:00 local but only 1 h notice (needs 2)
  }

  @Test
  void missingGuestDetailsAreRejected() throws Exception {
    String holdId = hold(NINE, null, 201).get("holdId").asText();
    api.send(post("/v1/bookings"), null, Map.of("holdId", holdId), 400);
    // the failed attempt did not consume the hold
    book(holdId, null, null, 201);
  }

  @Test
  void idempotencyKeyReplaysTheSameBooking() throws Exception {
    String holdId = hold(NINE, null, 201).get("holdId").asText();
    JsonNode first = book(holdId, "key-1", null, 201);
    JsonNode retry = book(holdId, "key-1", null, 201);
    assertThat(retry.get("id").asText()).isEqualTo(first.get("id").asText());
    assertThat(mondayStartsUtc()).doesNotContain(NINE);

    // without the key a reused hold is a conflict, not a second booking
    book(holdId, null, null, 409);
  }

  @Test
  void idempotencyKeyCannotBeReusedForAnotherBooking() throws Exception {
    book(hold(NINE, null, 201).get("holdId").asText(), "key-2", null, 201);
    JsonNode err =
        book(hold("2030-01-14T13:00:00Z", null, 201).get("holdId").asText(), "key-2", null, 409);
    assertThat(err.get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void anyStaffHoldsPickDifferentPeopleThenRunOut() throws Exception {
    String tom = addStaff("Tom", null, null);
    JsonNode h1 = hold(NINE, null, 201);
    JsonNode h2 = hold(NINE, null, 201);
    assertThat(java.util.Set.of(h1.get("staffId").asText(), h2.get("staffId").asText()))
        .containsExactlyInAnyOrder(miaId, tom);
    hold(NINE, null, 409);
    // explicit staff that is busy
    hold(NINE, miaId, 409);
  }

  @Test
  void customerAccountBookingUsesProfileAndIsOwnedByCustomer() throws Exception {
    JsonNode reg = api.registerFull("CUSTOMER");
    String customer = reg.get("accessToken").asText();
    String other = api.register("CUSTOMER");

    JsonNode booking = book(hold(NINE, null, 201).get("holdId").asText(), null, customer, 201);
    assertThat(booking.get("customerId").asText()).isEqualTo(reg.at("/user/id").asText());
    assertThat(booking.get("guestEmail").asText()).isEqualTo(reg.at("/user/email").asText());
    String id = booking.get("id").asText();

    api.send(get("/v1/bookings/" + id), customer, null, 200);
    api.send(get("/v1/bookings/" + id), other, null, 403);
    api.send(get("/v1/bookings/" + id), ownerToken, null, 200);
    api.send(get("/v1/bookings/" + id), api.register("OWNER"), null, 403);
  }

  @Test
  void rescheduleMovesAtomicallyAndFreesTheOldSlot() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    String id = booking.get("id").asText();
    String token = booking.get("manageToken").asText();

    JsonNode moved =
        manage(
            post("/v1/bookings/" + id + "/reschedule"),
            token,
            Map.of("newStart", "2030-01-14T13:00:00Z"),
            200);
    assertThat(moved.get("start").asText()).isEqualTo("2030-01-14T14:00:00+01:00");
    assertThat(moved.get("version").asLong()).isGreaterThan(booking.get("version").asLong());
    assertThat(mondayStartsUtc()).contains(NINE).doesNotContain("2030-01-14T13:00:00Z");
  }

  @Test
  void rescheduleMayOverlapItsOwnOldSlot() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    manage(
        post("/v1/bookings/" + booking.get("id").asText() + "/reschedule"),
        booking.get("manageToken").asText(),
        Map.of("newStart", "2030-01-14T08:30:00Z"),
        200);
  }

  @Test
  void rescheduleOntoATakenSlotConflictsAndKeepsTheOriginal() throws Exception {
    JsonNode a = bookAsGuest(NINE);
    bookAsGuest("2030-01-14T13:00:00Z");
    JsonNode err =
        manage(
            post("/v1/bookings/" + a.get("id").asText() + "/reschedule"),
            a.get("manageToken").asText(),
            Map.of("newStart", "2030-01-14T13:00:00Z"),
            409);
    assertThat(err.get("code").asText()).isEqualTo("SLOT_UNAVAILABLE");
    JsonNode unchanged =
        manage(
            get("/v1/bookings/" + a.get("id").asText()), a.get("manageToken").asText(), null, 200);
    assertThat(unchanged.get("start").asText()).isEqualTo("2030-01-14T09:00:00+01:00");
  }

  @Test
  void rescheduleAndCancelRespectTheCutoffForGuestsButNotOwners() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    String id = booking.get("id").asText();
    String token = booking.get("manageToken").asText();

    // appointment is Mon 09:00 Berlin; cutoff 24 h -> at Sun 09:01 Berlin it is too late
    clock.set(java.time.Instant.parse("2030-01-13T08:01:00Z"));
    JsonNode err = manage(post("/v1/bookings/" + id + "/cancel"), token, null, 422);
    assertThat(err.get("code").asText()).isEqualTo("CANCELLATION_WINDOW_CLOSED");
    manage(
        post("/v1/bookings/" + id + "/reschedule"),
        token,
        Map.of("newStart", "2030-01-14T13:00:00Z"),
        422);

    // owner overrides the cutoff
    JsonNode cancelled = api.send(post("/v1/bookings/" + id + "/cancel"), ownerToken, null, 200);
    assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
  }

  @Test
  void cancelExactlyAtTheCutoffIsStillAllowed() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    clock.set(java.time.Instant.parse("2030-01-13T08:00:00Z")); // exactly 24 h before
    manage(
        post("/v1/bookings/" + booking.get("id").asText() + "/cancel"),
        booking.get("manageToken").asText(),
        null,
        200);
  }

  @Test
  void cancelFreesTheSlotAndCannotRepeat() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    String id = booking.get("id").asText();
    String token = booking.get("manageToken").asText();
    manage(post("/v1/bookings/" + id + "/cancel"), token, null, 200);
    assertThat(mondayStartsUtc()).contains(NINE);

    JsonNode err = manage(post("/v1/bookings/" + id + "/cancel"), token, null, 422);
    assertThat(err.get("code").asText()).isEqualTo("INVALID_STATE");
    manage(
        post("/v1/bookings/" + id + "/reschedule"),
        token,
        Map.of("newStart", "2030-01-14T13:00:00Z"),
        422);
    // the freed slot can be booked again
    bookAsGuest(NINE);
  }

  @Test
  void customerAndStrangersOnCancel() throws Exception {
    String customer = api.register("CUSTOMER");
    JsonNode booking = book(hold(NINE, null, 201).get("holdId").asText(), null, customer, 201);
    String id = booking.get("id").asText();
    api.send(post("/v1/bookings/" + id + "/cancel"), api.register("CUSTOMER"), null, 403);
    api.send(post("/v1/bookings/" + id + "/cancel"), api.register("OWNER"), null, 403);
    api.send(post("/v1/bookings/" + id + "/cancel"), null, null, 403);
    api.send(post("/v1/bookings/" + id + "/cancel"), customer, null, 200);
  }

  @Test
  void staffCompletesOnlyAfterStartAndOnlyOwnBookings() throws Exception {
    String email = "mia-" + UUID.randomUUID() + "@example.com";
    String miaLoginStaff = addStaff("Mia Login", email, "password123");
    String staffToken = api.login(email, "password123");
    String otherStaffToken;
    String email2 = "tom-" + UUID.randomUUID() + "@example.com";
    addStaff("Tom Login", email2, "password123");
    otherStaffToken = api.login(email2, "password123");

    String holdId = hold(NINE, miaLoginStaff, 201).get("holdId").asText();
    JsonNode booking = book(holdId, null, null, 201);
    String id = booking.get("id").asText();

    JsonNode early =
        api.send(
            patch("/v1/bookings/" + id + "/status"),
            staffToken,
            Map.of("status", "COMPLETED"),
            422);
    assertThat(early.get("code").asText()).isEqualTo("INVALID_STATE");

    clock.set(java.time.Instant.parse("2030-01-14T09:30:00Z"));
    api.send(
        patch("/v1/bookings/" + id + "/status"),
        otherStaffToken,
        Map.of("status", "COMPLETED"),
        403);
    api.send(
        patch("/v1/bookings/" + id + "/status"),
        api.register("CUSTOMER"),
        Map.of("status", "COMPLETED"),
        403);
    api.send(
        patch("/v1/bookings/" + id + "/status"), staffToken, Map.of("status", "CONFIRMED"), 400);
    JsonNode done =
        api.send(
            patch("/v1/bookings/" + id + "/status"),
            staffToken,
            Map.of("status", "COMPLETED"),
            200);
    assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
    // owner can correct it to no-show
    assertThat(
            api.send(
                    patch("/v1/bookings/" + id + "/status"),
                    ownerToken,
                    Map.of("status", "NO_SHOW"),
                    200)
                .get("status")
                .asText())
        .isEqualTo("NO_SHOW");
    // a staff login cannot cancel
    api.send(post("/v1/bookings/" + id + "/cancel"), staffToken, null, 403);
  }

  @Test
  void cancelledBookingCannotBeMarkedCompleted() throws Exception {
    JsonNode booking = bookAsGuest(NINE);
    String id = booking.get("id").asText();
    api.send(post("/v1/bookings/" + id + "/cancel"), ownerToken, null, 200);
    clock.set(java.time.Instant.parse("2030-01-14T09:30:00Z"));
    api.send(
        patch("/v1/bookings/" + id + "/status"), ownerToken, Map.of("status", "COMPLETED"), 422);
  }
}
