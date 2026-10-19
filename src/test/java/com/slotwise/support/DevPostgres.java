package com.slotwise.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Starts a throwaway PostgreSQL on port 5432 (db/user/password "slotwise") for trying the app
 * locally without Docker: {@code java -cp target/test-classes:...
 * com.slotwise.support.DevPostgres}.
 */
public final class DevPostgres {

  private DevPostgres() {}

  public static void main(String[] args) throws Exception {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 5432;
    try (EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(port).start()) {
      try (var c = pg.getPostgresDatabase().getConnection();
          var st = c.createStatement()) {
        st.execute("CREATE ROLE slotwise LOGIN SUPERUSER PASSWORD 'slotwise'");
        st.execute("CREATE DATABASE slotwise OWNER slotwise");
      }
      System.out.println("READY postgres on " + port);
      Thread.currentThread().join();
    }
  }
}
