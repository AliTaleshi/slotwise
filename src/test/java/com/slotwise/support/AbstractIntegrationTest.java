package com.slotwise.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestClockConfig.class, TestMailConfig.class})
public abstract class AbstractIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired private MutableClock baseClock;

  /** The fake clock is shared by the cached context, so every test starts from the same instant. */
  @org.junit.jupiter.api.BeforeEach
  void resetClock() {
    baseClock.set(TestClockConfig.START);
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresTestSupport::jdbcUrl);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
  }
}
