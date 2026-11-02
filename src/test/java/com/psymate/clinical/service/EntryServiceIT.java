package com.psymate.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.clinical.ClinicalFixtures;
import com.psymate.clinical.ClinicalTestConfig;
import com.psymate.clinical.ClinicalTestConfig.MutableClock;
import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.model.ClinicalError;
import com.psymate.clinical.model.ClinicalException;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.FeelingDetail;
import com.psymate.clinical.model.MedicationDetail;
import com.psymate.clinical.model.SessionDetail;
import com.psymate.clinical.model.TimeSpan.Precision;
import com.psymate.clinical.model.TimeSpec;
import com.psymate.patients.api.PatientAccessException;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@ActiveProfiles("test")
@Import(ClinicalTestConfig.class)
@RecordApplicationEvents
class EntryServiceIT {

  /** Fenêtre de grâce : 1 h 15 (versioning.grace-period = 75m). */
  private static final Duration WITHIN_GRACE = Duration.ofMinutes(74);

  private static final Duration AFTER_GRACE = Duration.ofMinutes(76);

  @Autowired private EntryService service;
  @Autowired private EntryFacade facade;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private ApplicationEvents events;

  private ClinicalFixtures fx;
  private UUID patient;
  private UUID session;

  @BeforeEach
  void setUp() {
    fx = new ClinicalFixtures(jdbc);
    patient = fx.newPatient(LocalDate.of(1990, 4, 2));
    clock.set(Instant.now());
    session = service.create(patient, sessionCommand());
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- fabriques de commandes

  private static CreateEntryCommand sessionCommand() {
    return new CreateEntryCommand(
        EntryType.SESSION,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new SessionDetail(null, "in_person", 50, null));
  }

  private CreateEntryCommand note(String body) {
    return new CreateEntryCommand(
        EntryType.NOTE, session, null, body, null, null, null, null, null, null);
  }

  private CreateEntryCommand of(EntryType type, UUID sessionId) {
    return switch (type) {
      case NOTE ->
          new CreateEntryCommand(
              type, sessionId, null, "Une note", null, null, null, null, null, null);
      case EVENT ->
          new CreateEntryCommand(
              type, sessionId, "Décès du père", null, null, null, null, null, null, null);
      case SITUATION ->
          new CreateEntryCommand(
              type,
              sessionId,
              "Chômage",
              null,
              null,
              null,
              TimeSpec.ongoing(Precision.MONTH, Instant.parse("2026-03-15T00:00:00Z")),
              null,
              null,
              null);
      case FEELING ->
          new CreateEntryCommand(
              type,
              sessionId,
              null,
              null,
              null,
              null,
              null,
              new FeelingDetail("anxiety", 7, "avant la réunion"),
              null,
              null);
      case MEDICATION ->
          new CreateEntryCommand(
              type,
              sessionId,
              null,
              null,
              null,
              null,
              null,
              null,
              new MedicationDetail(
                  "sertraline", "50 mg", "matin", "Dr X", LocalDate.of(2026, 1, 5), null),
              null);
      case SESSION ->
          new CreateEntryCommand(
              type,
              sessionId,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              new SessionDetail(null, "in_person", 50, null));
    };
  }

  private static CorrectEntryCommand correctBody(String body) {
    return new CorrectEntryCommand(null, body, null, null, null, null, null, null, "précision");
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  // ---- six types : table de détail et validateur

  @Test
  void everyTypeHasItsDetailTableAndItsValidator() {
    for (EntryType type : EntryType.values()) {
      UUID id = service.create(patient, of(type, type == EntryType.SESSION ? null : session));
      assertThat(count("select count(*) from entry where id = ? and type = ?", id, type.code()))
          .as(type + " entry")
          .isEqualTo(1);
      assertThat(count("select count(*) from entry_version where entry_id = ?", id))
          .as(type + " version 1")
          .isEqualTo(1);
      if (type.detailTable() != null) {
        assertThat(count("select count(*) from " + type.detailTable() + " where entry_id = ?", id))
            .as(type + " detail row")
            .isEqualTo(1);
      }
      assertThat(
              jdbc.queryForObject(
                  "select detail_table from entry_type where code = ?", String.class, type.code()))
          .as(type + " entry_type.detail_table")
          .isEqualTo(type.detailTable());
    }
  }

  @Test
  void eachTypeRejectsItsMinimalMissingField() {
    Map<EntryType, CreateEntryCommand> invalid =
        Map.of(
            EntryType.NOTE,
            new CreateEntryCommand(
                EntryType.NOTE, session, null, " ", null, null, null, null, null, null),
            EntryType.EVENT,
            new CreateEntryCommand(
                EntryType.EVENT, session, null, "x", null, null, null, null, null, null),
            EntryType.SITUATION,
            new CreateEntryCommand(
                EntryType.SITUATION, session, "Chômage", null, null, null, null, null, null, null),
            EntryType.FEELING,
            new CreateEntryCommand(
                EntryType.FEELING,
                session,
                null,
                null,
                null,
                null,
                null,
                new FeelingDetail("anxiety", null, null),
                null,
                null),
            EntryType.SESSION,
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
                new SessionDetail(null, null, null, null)),
            EntryType.MEDICATION,
            new CreateEntryCommand(
                EntryType.MEDICATION,
                session,
                null,
                null,
                null,
                null,
                null,
                null,
                new MedicationDetail(" ", null, null, null, null, null),
                null));
    assertThat(invalid.keySet()).containsExactlyInAnyOrder(EntryType.values());
    invalid.forEach(
        (type, cmd) ->
            assertThatThrownBy(() -> service.create(patient, cmd))
                .as(type.toString())
                .isInstanceOf(ConstraintViolationException.class));
    assertThat(count("select count(*) from entry where patient_id = ?", patient))
        .as("seule la séance du setUp existe")
        .isEqualTo(1);
  }

  @Test
  void feelingWithoutEmotionCodeOrWithIntensityOutOfRangeIsRejected() {
    assertThatThrownBy(
            () ->
                service.create(
                    patient,
                    new CreateEntryCommand(
                        EntryType.FEELING,
                        session,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new FeelingDetail(" ", 3, null),
                        null,
                        null)))
        .isInstanceOf(ConstraintViolationException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    patient,
                    new CreateEntryCommand(
                        EntryType.FEELING,
                        session,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new FeelingDetail("anxiety", 11, null),
                        null,
                        null)))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void unknownEmotionIsRefused() {
    assertThatThrownBy(
            () ->
                service.create(
                    patient,
                    new CreateEntryCommand(
                        EntryType.FEELING,
                        session,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new FeelingDetail("nope", 3, null),
                        null,
                        null)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.UNKNOWN_EMOTION));
  }

  // ---- séance obligatoire

  @Test
  void entryWithoutSessionIsRefused() {
    // Le patient du setUp a une séance ouverte (M1-04 : une note y serait rattachée) : on en prend
    // un autre, sans aucune séance.
    UUID noSessionPatient = fx.newPatient(LocalDate.of(1985, 1, 1));
    assertThatThrownBy(() -> service.create(noSessionPatient, of(EntryType.NOTE, null)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.SESSION_REQUIRED));
  }

  @Test
  void sessionIdPointingToANoteIsRefused() {
    UUID aNote = service.create(patient, note("pas une séance"));
    assertThatThrownBy(() -> service.create(patient, of(EntryType.EVENT, aNote)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.SESSION_INVALID));
  }

  @Test
  void sessionOfAnotherPatientIsRefused() {
    UUID other = fx.newPatient(null);
    UUID otherSession = service.create(other, sessionCommand());
    assertThatThrownBy(() -> service.create(patient, of(EntryType.EVENT, otherSession)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.SESSION_INVALID));
  }

  @Test
  void aSessionCannotHaveAParentSession() {
    assertThatThrownBy(() -> service.create(patient, of(EntryType.SESSION, session)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.SESSION_NOT_ALLOWED));
  }

  @Test
  void patientOfAnotherPractitionerIsForbidden() {
    UUID mine = patient;
    fx.authenticate(fx.newPractitioner());
    assertThatThrownBy(() -> service.create(mine, note("x")))
        .isInstanceOf(PatientAccessException.class);
  }

  // ---- colonnes réservées (A11)

  @Test
  void everyCreatedEntryIsAuthoredByThePractitionerWithoutSubmission() {
    for (EntryType type : EntryType.values()) {
      service.create(patient, of(type, type == EntryType.SESSION ? null : session));
    }
    assertThat(
            count(
                "select count(*) from entry where patient_id = ? and (author_type <> 'practitioner'"
                    + " or submission_id is not null)",
                patient))
        .isZero();
    assertThat(count("select count(*) from entry where patient_id = ?", patient)).isEqualTo(7);
    assertThat(count("select count(*) from patient_device")).isZero();
    assertThat(count("select count(*) from patient_submission")).isZero();
    assertThat(
            count(
                "select count(*) from entry where patient_id = ? and created_by = ?",
                patient,
                fx.practitioner))
        .isEqualTo(7);
  }

  // ---- valence / arousal

  @Test
  void valenceAndArousalAreCopiedWhenTheEmotionHasThemAndNullOtherwise() {
    jdbc.update("update emotion set valence = -1, arousal = 3 where code = 'anxiety'");
    UUID withAffect = service.create(patient, of(EntryType.FEELING, session));
    assertThat(
            jdbc.queryForMap(
                "select valence, arousal from entry_feeling where entry_id = ?", withAffect))
        .containsEntry("valence", -1)
        .containsEntry("arousal", 3);

    UUID without =
        service.create(
            patient,
            new CreateEntryCommand(
                EntryType.FEELING,
                session,
                null,
                null,
                null,
                null,
                null,
                new FeelingDetail("guilt", 4, null),
                null,
                null));
    Map<String, Object> row =
        jdbc.queryForMap("select valence, arousal from entry_feeling where entry_id = ?", without);
    assertThat(row.get("valence")).isNull();
    assertThat(row.get("arousal")).isNull();
  }

  // ---- temps

  @Test
  void situationWithoutOccurredFromIsRejectedAndOtherTypesDefaultToNow() {
    assertThatThrownBy(
            () ->
                service.create(
                    patient,
                    new CreateEntryCommand(
                        EntryType.SITUATION,
                        session,
                        "Chômage",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ConstraintViolationException.class);
    UUID n = service.create(patient, note("maintenant"));
    Map<String, Object> row =
        jdbc.queryForMap(
            "select occurred_from, occurred_to, occurred_precision from entry where id = ?", n);
    assertThat(row.get("occurred_precision")).isEqualTo("exact");
    assertThat(row.get("occurred_from")).isEqualTo(row.get("occurred_to"));
  }

  @Test
  void ongoingSituationKeepsOccurredToNull() {
    UUID id = service.create(patient, of(EntryType.SITUATION, session));
    Map<String, Object> row =
        jdbc.queryForMap(
            "select occurred_from, occurred_to, occurred_precision from entry where id = ?", id);
    assertThat(row.get("occurred_to")).isNull();
    assertThat(row.get("occurred_precision")).isEqualTo("month");
  }

  @Test
  void lifePhaseIsComputedFromThePatientBirthDate() {
    UUID id =
        service.create(
            patient,
            new CreateEntryCommand(
                EntryType.EVENT,
                session,
                "Déménagement",
                null,
                null,
                null,
                new TimeSpec(Precision.LIFE_PHASE, null, null, "enfance", false),
                null,
                null,
                null));
    assertThat(
            count(
                "select count(*) from entry where id = ? and occurred_precision = 'life_phase'"
                    + " and occurred_label = 'enfance'"
                    + " and occurred_from = timestamptz '1990-04-02 00:00:00+00'"
                    + " and occurred_to = timestamptz '2002-04-02 00:00:00+00'",
                id))
        .isEqualTo(1);
  }

  // ---- versions et règle de grâce

  @Test
  void correctionWithinGraceByTheSameAuthorCreatesNoVersionNorEvent() {
    UUID id = service.create(patient, note("coquile"));
    events.clear();
    clock.advance(WITHIN_GRACE);

    CorrectionResult r = service.correct(id, correctBody("coquille"));

    assertThat(r.versioned()).isFalse();
    assertThat(r.changed()).isTrue();
    assertThat(count("select count(*) from entry_version where entry_id = ?", id)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where entry_id = ?", String.class, id))
        .isEqualTo("coquille");
    assertThat(count("select current_version from entry where id = ?", id)).isEqualTo(1);
    assertThat(count("select count(*) from entry where id = ? and updated_at > created_at", id))
        .isEqualTo(1);
    assertThat(events.stream(EntryCorrected.class).count()).isZero();
  }

  @Test
  void correctionAfterFiveMinutesCreatesVersionTwoAndEvent() {
    UUID id = service.create(patient, note("v1"));
    events.clear();
    clock.advance(AFTER_GRACE);

    CorrectionResult r = service.correct(id, correctBody("v2"));

    assertThat(r.versioned()).isTrue();
    assertThat(r.version()).isEqualTo(2);
    assertThat(count("select count(*) from entry_version where entry_id = ?", id)).isEqualTo(2);
    assertThat(count("select current_version from entry where id = ?", id)).isEqualTo(2);
    assertThat(
            count(
                "select count(*) from entry e join entry_version v on v.id = e.current_version_id"
                    + " where e.id = ? and v.version = 2 and v.body = 'v2' and v.reason ="
                    + " 'précision' and v.author_id = ?",
                id,
                fx.practitioner))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where entry_id = ? and version = 1",
                String.class,
                id))
        .isEqualTo("v1");
    List<EntryCorrected> corrected = events.stream(EntryCorrected.class).toList();
    assertThat(corrected).hasSize(1);
    assertThat(corrected.get(0).getPreviousVersion()).isEqualTo(1);
    assertThat(corrected.get(0).getNewVersion()).isEqualTo(2);
  }

  @Test
  void secondCorrectionWithinGraceWindowCreatesAVersionOnceOneExists() {
    UUID id = service.create(patient, note("v1"));
    clock.advance(AFTER_GRACE);
    service.correct(id, correctBody("v2"));
    clock.advance(Duration.ofSeconds(30));
    CorrectionResult r = service.correct(id, correctBody("v3"));
    assertThat(r.versioned()).isTrue();
    assertThat(r.version()).isEqualTo(3);
  }

  @Test
  void correctionWithinGraceButLinkedCreatesAVersion() {
    UUID id = service.create(patient, note("v1"));
    jdbc.update("update entry set link_count = 1 where id = ?", id);
    clock.advance(Duration.ofMinutes(1));
    CorrectionResult r = service.correct(id, correctBody("v2"));
    assertThat(r.versioned()).isTrue();
    assertThat(count("select count(*) from entry_version where entry_id = ?", id)).isEqualTo(2);
  }

  @Test
  void correctionWithoutChangeWritesNothing() {
    UUID id = service.create(patient, note("v1"));
    clock.advance(AFTER_GRACE);
    events.clear();
    CorrectionResult r = service.correct(id, correctBody("v1"));
    assertThat(r.changed()).isFalse();
    assertThat(count("select count(*) from entry_version where entry_id = ?", id)).isEqualTo(1);
    assertThat(events.stream(EntryCorrected.class).count()).isZero();
  }

  @Test
  void correctingEachTypeUpdatesItsDetailTable() {
    clock.advance(AFTER_GRACE);
    UUID feeling = service.create(patient, of(EntryType.FEELING, session));
    UUID medication = service.create(patient, of(EntryType.MEDICATION, session));
    UUID sessionEntry = service.create(patient, sessionCommand());
    UUID situation = service.create(patient, of(EntryType.SITUATION, session));
    clock.advance(AFTER_GRACE);

    service.correct(
        feeling,
        new CorrectEntryCommand(
            null,
            null,
            null,
            null,
            null,
            new FeelingDetail("shame", 9, "plus fort"),
            null,
            null,
            null));
    service.correct(
        medication,
        new CorrectEntryCommand(
            null,
            null,
            null,
            null,
            null,
            null,
            new MedicationDetail("sertraline", "100 mg", "matin", "Dr X", null, null),
            null,
            "dose"));
    service.correct(
        sessionEntry,
        new CorrectEntryCommand(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SessionDetail(null, "video", 45, null),
            null));
    service.correct(
        situation,
        new CorrectEntryCommand(
            "Chômage de longue durée", null, null, null, null, null, null, null, null));

    assertThat(
            jdbc.queryForMap(
                "select emotion_code, intensity, context_note from entry_feeling where entry_id"
                    + " = ?",
                feeling))
        .containsEntry("emotion_code", "shame")
        .containsEntry("intensity", 9)
        .containsEntry("context_note", "plus fort");
    assertThat(
            jdbc.queryForObject(
                "select dosage from entry_medication where entry_id = ?", String.class, medication))
        .isEqualTo("100 mg");
    assertThat(
            jdbc.queryForMap(
                "select modality, duration_min from entry_session where entry_id = ?",
                sessionEntry))
        .containsEntry("modality", "video")
        .containsEntry("duration_min", 45);
    assertThat(
            jdbc.queryForObject(
                "select title from entry_version where entry_id = ? and version = 2",
                String.class,
                situation))
        .isEqualTo("Chômage de longue durée");
    // Le payload de la version 2 porte la copie du détail typé.
    assertThat(
            jdbc.queryForObject(
                "select payload->'detail'->>'emotionCode' from entry_version where entry_id = ?"
                    + " and version = 2",
                String.class,
                feeling))
        .isEqualTo("shame");
  }

  @Test
  void correctingAnotherPractitionersEntryIsForbidden() {
    UUID id = service.create(patient, note("v1"));
    clock.advance(AFTER_GRACE);
    fx.authenticate(fx.newPractitioner());
    assertThatThrownBy(() -> service.correct(id, correctBody("piratage")))
        .isInstanceOf(PatientAccessException.class);
  }

  // ---- archivage, rev, événements, façade

  @Test
  void archiveSetsArchivedAtPublishesEventAndRefusesFurtherCorrections() {
    UUID id = service.create(patient, note("à archiver"));
    events.clear();
    service.archive(id);
    assertThat(count("select count(*) from entry where id = ? and archived_at is not null", id))
        .isEqualTo(1);
    assertThat(events.stream(EntryArchived.class).count()).isEqualTo(1);
    service.archive(id);
    assertThat(events.stream(EntryArchived.class).count()).as("idempotent").isEqualTo(1);
    clock.advance(AFTER_GRACE);
    assertThatThrownBy(() -> service.correct(id, correctBody("x")))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.ENTRY_ARCHIVED));
    assertThat(count("select count(*) from entry_version where entry_id = ?", id)).isEqualTo(1);
  }

  @Test
  void everyWriteBumpsPatientClinicalRev() {
    long before =
        jdbc.queryForObject("select clinical_rev from patient where id = ?", Long.class, patient);
    UUID id = service.create(patient, note("rev"));
    clock.advance(Duration.ofSeconds(10));
    service.correct(id, correctBody("rev2"));
    clock.advance(AFTER_GRACE);
    service.correct(id, correctBody("rev3"));
    service.archive(id);
    long after =
        jdbc.queryForObject("select clinical_rev from patient where id = ?", Long.class, patient);
    assertThat(after - before).isEqualTo(4);
  }

  @Test
  void creationPublishesEntryCreated() {
    events.clear();
    UUID id = service.create(patient, note("evt"));
    List<EntryCreated> created = events.stream(EntryCreated.class).toList();
    assertThat(created).hasSize(1);
    assertThat(created.get(0).getEntryId()).isEqualTo(id);
    assertThat(created.get(0).getPatientId()).isEqualTo(patient);
    assertThat(created.get(0).getEntryType()).isEqualTo("note");
    assertThat(created.get(0).getTenantId()).isEqualTo(fx.tenant);
  }

  @Test
  void facadeAnswersForOwnedEntries() {
    UUID id = service.create(patient, note("facade"));
    UUID otherPatient = fx.newPatient(null);
    assertThat(facade.exists(id)).isTrue();
    assertThat(facade.exists(UUID.randomUUID())).isFalse();
    assertThat(facade.belongsToPatient(id, patient)).isTrue();
    assertThat(facade.belongsToPatient(id, otherPatient)).isFalse();
    assertThat(facade.typeOf(id)).contains("note");
    assertThat(facade.currentVersionOf(id))
        .hasValueSatisfying(
            v -> {
              assertThat(v.version()).isEqualTo(1);
              assertThat(v.body()).isEqualTo("facade");
            });
  }

  @Test
  void facadeDoesNotLeakEntriesOfAnotherPractitioner() {
    UUID id = service.create(patient, note("secret"));
    fx.authenticate(fx.newPractitioner());
    assertThat(facade.exists(id)).isFalse();
    assertThat(facade.belongsToPatient(id, patient)).isFalse();
    assertThat(facade.typeOf(id)).isEmpty();
    assertThat(facade.currentVersionOf(id)).isEmpty();
    SecurityContextHolder.clearContext();
    assertThat(facade.exists(id)).as("sans principal").isFalse();
  }

  @Test
  void archivedSessionCannotReceiveEntries() {
    UUID other = service.create(patient, sessionCommand());
    service.archive(other);
    assertThatThrownBy(() -> service.create(patient, of(EntryType.EVENT, other)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> assertThat(e.error()).isEqualTo(ClinicalError.SESSION_INVALID));
  }

  @Test
  void archivingAnotherPractitionersEntryIsForbidden() {
    UUID id = service.create(patient, note("v1"));
    fx.authenticate(fx.newPractitioner());
    assertThatThrownBy(() -> service.archive(id)).isInstanceOf(PatientAccessException.class);
    assertThat(count("select count(*) from entry where id = ? and archived_at is null", id))
        .isEqualTo(1);
  }
}
