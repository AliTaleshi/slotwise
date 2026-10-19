package com.slotwise.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.common.error.ConstraintViolations;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** The double-booking guarantees, proven against a real PostgreSQL under real contention. */
class BookingConcurrencyIntegrationTest extends AbstractBookingTest {

  static final int THREADS = 20;

  @Autowired BookingRepository bookings;

  /** Runs all tasks at the same instant on separate threads and collects their results. */
  private <T> List<T> race(List<Callable<T>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      CountDownLatch ready = new CountDownLatch(tasks.size());
      CountDownLatch go = new CountDownLatch(1);
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> f : futures) {
        results.add(f.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private int statusOf(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
      throws Exception {
    return mvc.perform(req).andReturn().getResponse().getStatus();
  }

  @Test
  void twentyThreadsBookingTheSameSlotYieldExactlyOneBooking() throws Exception {
    List<Callable<Integer>> tasks = new ArrayList<>();
    List<Integer> bookingStatuses = java.util.Collections.synchronizedList(new ArrayList<>());
    for (int i = 0; i < THREADS; i++) {
      int n = i;
      tasks.add(
          () -> {
            var holdRes =
                mvc.perform(
                        post("/v1/holds")
                            .contentType("application/json")
                            .content(
                                json.writeValueAsString(
                                    Map.of("serviceId", serviceId, "start", NINE))))
                    .andReturn()
                    .getResponse();
            if (holdRes.getStatus() != 201) {
              return holdRes.getStatus();
            }
            JsonNode hold = json.readTree(holdRes.getContentAsString());
            int status =
                statusOf(
                    post("/v1/bookings")
                        .contentType("application/json")
                        .header("Idempotency-Key", "race-" + n)
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "holdId", hold.get("holdId").asText(),
                                    "guestName", "Guest " + n,
                                    "guestEmail", "g" + n + "@example.com"))));
            bookingStatuses.add(status);
            return 100 + status; // distinguish "hold ok, booking status" from hold failures
          });
    }

    List<Integer> results = race(tasks);

    assertThat(results.stream().filter(s -> s == 201 + 100).count())
        .as("threads that completed a booking")
        .isEqualTo(1);
    assertThat(results.stream().filter(s -> s == 409).count())
        .as("threads rejected with 409 SLOT_UNAVAILABLE")
        .isEqualTo(THREADS - 1);
    assertThat(bookingStatuses).containsExactly(201);
    assertThat(
            bookings.findAll().stream()
                .filter(b -> b.getBusinessId().toString().equals(businessId))
                .count())
        .isEqualTo(1);
  }

  /** Skips the hold step: the bookings table's own exclusion constraint must stop the overlap. */
  @Test
  void exclusionConstraintAloneStopsTwentyConcurrentOverlappingInserts() throws Exception {
    UUID business = UUID.fromString(businessId);
    UUID staff = UUID.fromString(miaId);
    UUID service = UUID.fromString(serviceId);
    Instant start = Instant.parse(NINE);

    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      // every attempt overlaps the others, with slightly different start times
      Instant s = start.plus(Duration.ofMinutes(i % 3 * 10));
      tasks.add(
          () -> {
            try {
              bookings.saveAndFlush(newBooking(business, staff, service, s));
              return "OK";
            } catch (DataIntegrityViolationException e) {
              return ConstraintViolations.isExclusionViolation(e) ? "EXCLUDED" : "OTHER:" + e;
            }
          });
    }

    List<String> results = race(tasks);

    assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
    assertThat(results.stream().filter("EXCLUDED"::equals).count()).isEqualTo(THREADS - 1);
  }

  @Test
  void twentyConcurrentReschedulesIntoOneFreeSlotLetExactlyOneWin() throws Exception {
    List<JsonNode> created = new ArrayList<>();
    // 20 bookings, one per half hour from 09:00 to 15:30 UTC-ish: use staff-per-booking to avoid
    // overlaps
    for (int i = 0; i < THREADS; i++) {
      String staff = addStaff("Extra " + i, null, null);
      String holdId = hold(NINE, staff, 201).get("holdId").asText();
      created.add(book(holdId, null, null, 201));
    }
    // all target the same slot with the same staff member (Mia, who is free at 13:00)
    String target = "2030-01-14T13:00:00Z";

    List<Callable<Integer>> tasks = new ArrayList<>();
    for (JsonNode b : created) {
      tasks.add(
          () ->
              statusOf(
                  post("/v1/bookings/" + b.get("id").asText() + "/reschedule")
                      .contentType("application/json")
                      .header("X-Manage-Token", b.get("manageToken").asText())
                      .content(
                          json.writeValueAsString(Map.of("newStart", target, "staffId", miaId)))));
    }

    List<Integer> results = race(tasks);

    assertThat(results.stream().filter(s -> s == 200).count()).isEqualTo(1);
    assertThat(results.stream().filter(s -> s == 409).count()).isEqualTo(THREADS - 1);
  }

  private static Booking newBooking(UUID business, UUID staff, UUID service, Instant start) {
    return new Booking(
        business,
        staff,
        service,
        null,
        "Racer",
        "racer@example.com",
        null,
        start,
        start.plus(Duration.ofMinutes(60)),
        Duration.ZERO,
        Duration.ofMinutes(15),
        new BigDecimal("80.00"),
        "EUR",
        null,
        null,
        Instant.parse("2030-01-07T08:00:00Z"));
  }
}
