package com.psymate.linking;

import static org.awaitility.Awaitility.await;

import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.FeelingDetail;
import com.psymate.clinical.model.MedicationDetail;
import com.psymate.clinical.model.SessionDetail;
import com.psymate.clinical.service.CreateEntryCommand;
import com.psymate.clinical.service.EntryService;
import com.psymate.identity.api.CurrentPractitioner;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Données de test du module linking : cabinet, praticien authentifié sur le thread, patients, et
 * entrées créées par le service réel de clinical (jamais de SQL sur entry).
 */
public class LinkingFixtures {

  public final JdbcTemplate jdbc;
  public final EntryService entries;
  public final UUID tenant = UUID.randomUUID();
  public final UUID practitioner;

  public LinkingFixtures(JdbcTemplate jdbc, EntryService entries) {
    this.jdbc = jdbc;
    this.entries = entries;
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet liens')", tenant);
    practitioner = newPractitioner();
    authenticate(practitioner);
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

  public void authenticate(UUID practitionerId) {
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
  public UUID newPatient() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, birth_date)"
            + " values (?, ?, ?, 'Patient test', ?)",
        id,
        tenant,
        currentPractitionerId(),
        LocalDate.of(1990, 4, 2));
    return id;
  }

  public UUID currentPractitionerId() {
    return CurrentPractitioner.get().orElseThrow().id();
  }

  /** Séance du patient, à passer aux autres entrées. */
  public UUID session(UUID patient) {
    return entries.create(
        patient,
        new CreateEntryCommand(
            EntryType.SESSION,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SessionDetail(null, "in_person", 50, null)));
  }

  /** Entrée de ce type dans cette séance, avec le minimum que son validateur exige. */
  public UUID entry(UUID patient, UUID session, EntryType type) {
    return entries.create(
        patient,
        switch (type) {
          case NOTE ->
              new CreateEntryCommand(
                  type, session, null, "Une note", null, null, null, null, null, null);
          case EVENT ->
              new CreateEntryCommand(
                  type, session, "Décès du père", null, null, null, null, null, null, null);
          case FEELING ->
              new CreateEntryCommand(
                  type,
                  session,
                  null,
                  "Peur au réveil",
                  null,
                  null,
                  null,
                  new FeelingDetail("anxiety", 7, "avant la réunion"),
                  null,
                  null);
          case MEDICATION ->
              new CreateEntryCommand(
                  type,
                  session,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  new MedicationDetail(
                      "sertraline", "50 mg", "matin", "Dr X", LocalDate.of(2026, 1, 5), null),
                  null);
          default -> throw new IllegalArgumentException("Type non géré par la fabrique : " + type);
        });
  }

  public int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  /**
   * Attend que toutes les publications d'événements qui concernent ce patient soient complétées.
   * Les abonnés asynchrones (recalcul de la vue d'ensemble, indexation de recherche, liens) sont
   * servis dans l'ordre d'arrivée par le même exécuteur : quand les publications du jeu de données
   * du test sont complétées, l'arriéré laissé par les tests précédents l'est aussi, et l'attente
   * qui suit un événement du test ne mesure plus que l'abonné testé. Sur la CI à 2 CPU, cet arriéré
   * a dépassé 15 s (centaines de recalculs d'un même patient, sérialisés sur sa ligne).
   */
  public void awaitPublicationsSettled(UUID patientId) {
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                count(
                        "select count(*) from event_publication where completion_date is null"
                            + " and serialized_event like ?",
                        "%" + patientId + "%")
                    == 0);
  }

  public int linkCount(UUID entryId) {
    return count("select link_count from entry where id = ?", entryId);
  }

  public int clinicalRev(UUID patientId) {
    return count("select clinical_rev from patient where id = ?", patientId);
  }
}
