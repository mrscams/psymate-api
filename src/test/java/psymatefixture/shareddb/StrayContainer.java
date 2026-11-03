package psymatefixture.shareddb;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Interdit : un test ordinaire qui démarre sa propre base au lieu de la base partagée. */
public class StrayContainer {
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");
}
