package com.psymate.vocabulary;

import com.psymate.identity.api.CurrentPractitioner;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Données et identité de test pour les IT qui appellent les services du module directement (sans
 * REST) : un cabinet, des praticiens, des tags, et le praticien courant posé dans le
 * SecurityContext (lu par identity.api.CurrentPractitioner.get()).
 */
final class VocabularyTestFixtures {

  private final JdbcTemplate jdbc;
  final UUID tenant = UUID.randomUUID();

  VocabularyTestFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  UUID newPractitioner() {
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

  UUID newPatient(UUID owner) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Patient')",
        id,
        tenant,
        owner);
    return id;
  }

  /** Tag inséré en SQL ; le libellé normalisé est le libellé en minuscules. */
  UUID newTag(UUID practitioner, String label) {
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

  void loginAs(UUID practitioner) {
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
