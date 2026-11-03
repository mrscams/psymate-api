package psymatefixture.shareddb;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Correct : test à base dédiée (liste fermée de la fixture), qui peut avoir son conteneur. */
public class DedicatedBase {
  static final String SQL = "select count(*) from practitioner";
  static final String CLEAN = "delete from practitioner";

  PostgreSQLContainer postgres;
}
