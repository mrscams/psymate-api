package psymatefixture.shareddb.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Correct : le dossier de support de la fixture construit les conteneurs. */
public final class FixtureContainers {

  private FixtureContainers() {}

  public static PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:16");
  }
}
