package com.psymate.testsupport;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Un test ne laisse pas d'administrateur actif en base partagée (M9-19) : chaque dossier soumis
 * notifie tous les administrateurs actifs (VerificationService), et un admin laissé par un test
 * recevrait les notifications de toute la tranche. L'envoi, séquentiel, finirait par retarder les
 * e-mails attendus par les autres tests.
 */
public final class TestAdmins {

  private static final String ACTIVE =
      " and role = 'admin'"
          + " and verification_status not in ('rejected','suspended','closed','email_pending')";

  private TestAdmins() {}

  /** Ferme les administrateurs actifs du tenant du test (à appeler en {@code @AfterEach}). */
  public static void closeInTenant(JdbcTemplate jdbc, UUID tenant) {
    jdbc.update(
        "update practitioner set verification_status = 'closed' where tenant_id = ?" + ACTIVE,
        tenant);
  }

  /** Ferme l'administrateur actif de cette adresse, s'il existe. */
  public static void closeByEmail(JdbcTemplate jdbc, String email) {
    jdbc.update(
        "update practitioner set verification_status = 'closed' where email_normalized = ?"
            + ACTIVE,
        email);
  }
}
