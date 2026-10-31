package com.psymate.clinical;

import com.psymate.identity.api.CurrentPractitioner;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Données de test : cabinet, praticien authentifié sur le thread, patients. */
class ClinicalFixtures {

  final JdbcTemplate jdbc;
  final UUID tenant = UUID.randomUUID();
  final UUID practitioner;

  ClinicalFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    practitioner = newPractitioner();
    authenticate(practitioner);
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

  void authenticate(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Test",
            "psychologist",
            "none");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  /** Patient du praticien courant, inséré directement (aucun événement, donc aucun accueil). */
  UUID newPatient(LocalDate birthDate) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, birth_date)"
            + " values (?, ?, ?, 'Patient test', ?)",
        id,
        tenant,
        practitioner,
        birthDate);
    return id;
  }
}
