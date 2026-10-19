package com.slotwise.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.slotwise.booking.AbstractBookingTest;
import com.slotwise.notification.webhook.WebhookRetryJob;
import com.slotwise.notification.webhook.WebhookSigner;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookIntegrationTest extends AbstractBookingTest {

  record Received(String body, String signature, String event, String deliveryId) {}

  @Autowired WebhookRetryJob retryJob;
  @Autowired javax.sql.DataSource dataSource;

  HttpServer server;
  final List<Received> received = new CopyOnWriteArrayList<>();
  final AtomicInteger responseStatus = new AtomicInteger(200);
  String hookUrl;

  @BeforeEach
  void startReceiver() throws Exception {
    new JdbcTemplate(dataSource).update("delete from bookings");
    received.clear();
    responseStatus.set(200);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/hook",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          received.add(
              new Received(
                  body,
                  exchange.getRequestHeaders().getFirst("X-Slotwise-Signature"),
                  exchange.getRequestHeaders().getFirst("X-Slotwise-Event"),
                  exchange.getRequestHeaders().getFirst("X-Slotwise-Delivery")));
          exchange.sendResponseHeaders(responseStatus.get(), -1);
          exchange.close();
        });
    server.start();
    hookUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
  }

  @AfterEach
  void stopReceiver() {
    server.stop(0);
  }

  JsonNode register(Map<String, Object> body) throws Exception {
    return api.send(post("/v1/businesses/" + businessId + "/webhooks"), ownerToken, body, 201);
  }

  void awaitReceived(int n) {
    await().atMost(Duration.ofSeconds(10)).until(() -> received.size() >= n);
  }

  @Test
  void createdEventIsDeliveredSignedAfterCommit() throws Exception {
    JsonNode hook = register(Map.of("url", hookUrl));
    String secret = hook.get("secret").asText();
    assertThat(secret).startsWith("whsec_");

    JsonNode booking = bookAsGuest(NINE);

    awaitReceived(1);
    Received r = received.getFirst();
    assertThat(r.event()).isEqualTo("booking.created");
    assertThat(WebhookSigner.verify(secret, r.signature(), r.body())).isTrue();
    JsonNode payload = json.readTree(r.body());
    assertThat(payload.get("type").asText()).isEqualTo("booking.created");
    assertThat(payload.get("id").asText()).isEqualTo(r.deliveryId());
    assertThat(payload.at("/data/booking/id").asText()).isEqualTo(booking.get("id").asText());
    assertThat(payload.at("/data/booking/start").asText()).isEqualTo("2030-01-14T08:00:00Z");
    assertThat(payload.at("/data/booking/guestEmail").asText()).isEqualTo("sam@example.com");

    // delivery log shows one successful attempt; the secret is never listed again
    String id = hook.get("id").asText();
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> deliveryLog(id).get(0).get("status").asText().equals("SUCCESS"));
    JsonNode log = deliveryLog(id).get(0);
    assertThat(log.get("attempts").asInt()).isEqualTo(1);
    assertThat(log.at("/attemptLog/0/statusCode").asInt()).isEqualTo(200);
    assertThat(
            api.send(get("/v1/businesses/" + businessId + "/webhooks"), ownerToken, null, 200)
                .get(0)
                .has("secret"))
        .isFalse();
  }

  JsonNode deliveryLog(String endpointId) throws Exception {
    return api.send(
        get("/v1/businesses/" + businessId + "/webhooks/" + endpointId + "/deliveries"),
        ownerToken,
        null,
        200);
  }

  @Test
  void rescheduledAndCancelledEventsCarryTheirData() throws Exception {
    register(Map.of("url", hookUrl));
    JsonNode booking = bookAsGuest(NINE);
    String id = booking.get("id").asText();
    String token = booking.get("manageToken").asText();
    awaitReceived(1);

    manage(
        post("/v1/bookings/" + id + "/reschedule"),
        token,
        Map.of("newStart", "2030-01-14T13:00:00Z"),
        200);
    awaitReceived(2);
    JsonNode moved = json.readTree(received.get(1).body());
    assertThat(moved.get("type").asText()).isEqualTo("booking.rescheduled");
    assertThat(moved.at("/data/previousStart").asText()).isEqualTo("2030-01-14T08:00:00Z");
    assertThat(moved.at("/data/booking/start").asText()).isEqualTo("2030-01-14T13:00:00Z");

    manage(post("/v1/bookings/" + id + "/cancel"), token, null, 200);
    awaitReceived(3);
    JsonNode cancelled = json.readTree(received.get(2).body());
    assertThat(cancelled.get("type").asText()).isEqualTo("booking.cancelled");
    assertThat(cancelled.at("/data/booking/status").asText()).isEqualTo("CANCELLED");
  }

  @Test
  void onlySubscribedEventsAreSent() throws Exception {
    register(Map.of("url", hookUrl, "events", List.of("booking.cancelled")));
    JsonNode booking = bookAsGuest(NINE);
    Thread.sleep(400);
    assertThat(received).isEmpty();
    manage(
        post("/v1/bookings/" + booking.get("id").asText() + "/cancel"),
        booking.get("manageToken").asText(),
        null,
        200);
    awaitReceived(1);
    assertThat(received.getFirst().event()).isEqualTo("booking.cancelled");
  }

  @Test
  void failedDeliveryIsRetriedWithBackoffAndEveryAttemptIsLogged() throws Exception {
    JsonNode hook = register(Map.of("url", hookUrl));
    String endpointId = hook.get("id").asText();
    responseStatus.set(500);

    bookAsGuest(NINE);
    awaitReceived(1);
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> deliveryLog(endpointId).get(0).get("attempts").asInt() == 1);
    JsonNode first = deliveryLog(endpointId).get(0);
    assertThat(first.get("status").asText()).isEqualTo("PENDING");
    assertThat(first.at("/attemptLog/0/statusCode").asInt()).isEqualTo(500);

    // not due yet: nothing happens
    assertThat(retryJob.run()).isZero();

    // the receiver recovers; after the 1 minute backoff the job redelivers
    responseStatus.set(200);
    clock.advance(Duration.ofSeconds(61));
    assertThat(retryJob.run()).isEqualTo(1);
    awaitReceived(2);
    assertThat(received.get(1).deliveryId()).isEqualTo(received.get(0).deliveryId());
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> deliveryLog(endpointId).get(0).get("status").asText().equals("SUCCESS"));
    JsonNode done = deliveryLog(endpointId).get(0);
    assertThat(done.get("attempts").asInt()).isEqualTo(2);
    assertThat(done.get("attemptLog")).hasSize(2);
    assertThat(retryJob.run()).isZero();
  }

  @Test
  void deliveryGivesUpAfterTheMaximumAttempts() throws Exception {
    JsonNode hook = register(Map.of("url", hookUrl));
    String endpointId = hook.get("id").asText();
    responseStatus.set(503);
    bookAsGuest(NINE);
    awaitReceived(1);
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> deliveryLog(endpointId).get(0).get("attempts").asInt() == 1);

    for (int i = 0; i < 5; i++) {
      clock.advance(Duration.ofHours(13));
      assertThat(retryJob.run()).isEqualTo(1);
    }
    JsonNode log = deliveryLog(endpointId).get(0);
    assertThat(log.get("status").asText()).isEqualTo("FAILED");
    assertThat(log.get("attempts").asInt()).isEqualTo(6);
    assertThat(received).hasSize(6);
    clock.advance(Duration.ofDays(2));
    assertThat(retryJob.run()).isZero();
  }

  @Test
  void deletedEndpointReceivesNothingMore() throws Exception {
    JsonNode hook = register(Map.of("url", hookUrl));
    api.send(
        delete("/v1/businesses/" + businessId + "/webhooks/" + hook.get("id").asText()),
        ownerToken,
        null,
        204);
    bookAsGuest(NINE);
    Thread.sleep(400);
    assertThat(received).isEmpty();
    assertThat(api.send(get("/v1/businesses/" + businessId + "/webhooks"), ownerToken, null, 200))
        .isEmpty();
  }

  @Test
  void ownershipAndValidationAreEnforced() throws Exception {
    JsonNode hook = register(Map.of("url", hookUrl));
    String other = api.register("OWNER");
    api.send(get("/v1/businesses/" + businessId + "/webhooks"), other, null, 403);
    api.send(
        post("/v1/businesses/" + businessId + "/webhooks"), other, Map.of("url", hookUrl), 403);
    api.send(
        delete("/v1/businesses/" + businessId + "/webhooks/" + hook.get("id").asText()),
        other,
        null,
        403);
    api.send(
        get(
            "/v1/businesses/"
                + businessId
                + "/webhooks/"
                + hook.get("id").asText()
                + "/deliveries"),
        other,
        null,
        403);
    api.send(
        post("/v1/businesses/" + businessId + "/webhooks"),
        ownerToken,
        Map.of("url", "ftp://x.example/h"),
        400);
    api.send(
        post("/v1/businesses/" + businessId + "/webhooks"),
        ownerToken,
        Map.of("url", hookUrl, "events", List.of("booking.exploded")),
        400);
    api.send(
        post("/v1/businesses/" + businessId + "/webhooks"),
        api.register("CUSTOMER"),
        Map.of("url", hookUrl),
        403);
  }

  @Test
  void rolledBackBookingNeverEmitsAnEvent() throws Exception {
    JsonNode hook = register(Map.of("url", hookUrl));
    String holdId = hold(NINE, null, 201).get("holdId").asText();
    // missing guest details: the booking transaction fails before anything is queued
    api.send(post("/v1/bookings"), null, Map.of("holdId", holdId), 400);
    Thread.sleep(400);
    assertThat(received).isEmpty();
    Integer rows =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "select count(*) from webhook_deliveries where endpoint_id = ?::uuid",
                Integer.class,
                hook.get("id").asText());
    assertThat(rows).isZero();
  }
}
