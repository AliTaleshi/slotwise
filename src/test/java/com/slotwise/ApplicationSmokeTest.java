package com.slotwise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotwise.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class ApplicationSmokeTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired DataSource dataSource;

  @Test
  void healthEndpointIsPublicAndUp() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void flywayInstalledBtreeGistAndShedlock() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer ext =
        jdbc.queryForObject(
            "select count(*) from pg_extension where extname='btree_gist'", Integer.class);
    Integer lock =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name='shedlock'",
            Integer.class);
    assertThat(ext).isEqualTo(1);
    assertThat(lock).isEqualTo(1);
  }
}
