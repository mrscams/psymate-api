package com.psymate.patients;

import com.psymate.identity.api.CurrentPractitioner;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Données et identité de test pour les IT qui appellent PatientService directement (sans REST) : un
 * cabinet, des praticiens, des tags, et le praticien courant posé dans le SecurityContext.
 */
public final class PatientServiceFixtures {

  private final JdbcTemplate jdbc;
  public final UUID tenant = UUID.randomUUID();

  public PatientServiceFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  public UUID newPractitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  /**
   * Attend la séance d'accueil que clinical écrit en asynchrone à la création du patient : elle
   * incrémente clinical_rev, donc un test qui compare clinical_rev doit d'abord la laisser passer.
   */
  public void awaitIntakeSession(UUID patientId) {
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (System.nanoTime() < deadline) {
      Integer entries =
          jdbc.queryForObject(
              "select count(*) from entry where patient_id = ?", Integer.class, patientId);
      if (entries != null && entries > 0) {
        return;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new IllegalStateException("Séance d'accueil non créée pour " + patientId);
  }

  public UUID newTag(UUID practitioner, String label) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into tag (id, tenant_id, practitioner_id, label, normalized_label)"
            + " values (?, ?, ?, ?, lower(?))",
        id,
        tenant,
        practitioner,
        label,
        label);
    return id;
  }

  public void loginAs(UUID practitioner) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitioner,
            tenant,
            practitioner + "@example.test",
            "Dr Test",
            "psychologist",
            "none");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }
}
