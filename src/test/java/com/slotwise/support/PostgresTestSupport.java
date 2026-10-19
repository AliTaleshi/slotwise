package com.slotwise.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;

/**
 * One embedded PostgreSQL per test JVM (real PostgreSQL, no H2: the exclusion constraint needs it).
 */
public final class PostgresTestSupport {

  private static final EmbeddedPostgres POSTGRES = start();

  private PostgresTestSupport() {}

  private static EmbeddedPostgres start() {
    try {
      EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      pg.close();
                    } catch (IOException ignored) {
                      // best effort on shutdown
                    }
                  }));
      return pg;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot start embedded PostgreSQL", e);
    }
  }

  public static String jdbcUrl() {
    return POSTGRES.getJdbcUrl("postgres", "postgres");
  }

  public static DataSource dataSource() {
    return POSTGRES.getPostgresDatabase();
  }
}
