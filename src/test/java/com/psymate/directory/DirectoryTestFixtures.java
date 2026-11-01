package com.psymate.directory;

import com.psymate.identity.api.CurrentPractitioner;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Données de test du module directory : praticien, session, requête de profil complète. */
final class DirectoryTestFixtures {

  private final JdbcTemplate jdbc;
  private final UUID tenant = UUID.randomUUID();

  DirectoryTestFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet directory')", tenant);
  }

  UUID tenant() {
    return tenant;
  }

  /** Insère un praticien approuvé et retourne son identifiant. */
  UUID newPractitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', 'Dr Persistance', 'psychologist', 'approved')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  void loginAs(UUID practitioner) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitioner,
            tenant,
            practitioner + "@example.test",
            "Dr Persistance",
            "psychologist",
            "none");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  /** Profil assez complet pour être publié (ville et un moyen de contact affiché). */
  static UpdateProfileRequest completeProfile(String bio) {
    return new UpdateProfileRequest(
        "  Dr Persistance  ",
        "Psychologue",
        true,
        bio,
        List.of("anxiety"),
        List.of("adults"),
        List.of("video"),
        List.of("fr"),
        "Lyon",
        "69001",
        "fr",
        null,
        "cabinet@example.test",
        null,
        null,
        null,
        true,
        null,
        "sous 48 h",
        null,
        "accepting",
        LocalDate.of(2030, 1, 15));
  }
}
