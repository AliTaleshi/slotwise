package com.slotwise.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.config.dev.DevDataSeeder;
import com.slotwise.support.AbstractIntegrationTest;
import com.slotwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({"test", "dev"})
class DevSeedIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DevDataSeeder seeder;

  @Test
  void seedProvidesABookableDemoBusinessAndIsIdempotent() throws Exception {
    seeder.run(new DefaultApplicationArguments()); // second run must be a no-op
    ApiTestSupport api = new ApiTestSupport(mvc, json);

    JsonNode profile = api.send(get("/v1/businesses/demo-clinic/public"), null, null, 200);
    assertThat(profile.get("services")).hasSize(2);
    assertThat(profile.get("staff")).hasSize(2);

    String serviceId = profile.at("/services/0/id").asText();
    // clock is 2030-01-07 (Monday) in tests; check the next Monday has slots
    JsonNode slots =
        api.send(
            get(
                "/v1/businesses/demo-clinic/availability?serviceId="
                    + serviceId
                    + "&from=2030-01-14&to=2030-01-14"),
            null,
            null,
            200);
    assertThat(slots.at("/days/0/slots").size()).isGreaterThan(5);

    String owner = api.login("owner@demo.test", DevDataSeeder.PASSWORD);
    assertThat(api.send(get("/v1/businesses"), owner, null, 200)).hasSize(1);
    api.login("mia@demo.test", DevDataSeeder.PASSWORD);
    api.login("customer@demo.test", DevDataSeeder.PASSWORD);
  }
}
