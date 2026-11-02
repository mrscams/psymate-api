package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.clinical.ClinicalTestConfig.MutableClock;
import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.application.CloseSessionCommand;
import com.psymate.clinical.application.ContactCommand;
import com.psymate.clinical.application.CorrectEntryCommand;
import com.psymate.clinical.application.CreateEntryCommand;
import com.psymate.clinical.application.EntryService;
import com.psymate.clinical.application.SessionService;
import com.psymate.clinical.application.StartSessionCommand;
import com.psymate.clinical.domain.ClinicalError;
import com.psymate.clinical.domain.ClinicalException;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.TimeSpec;
import com.psymate.patients.api.PatientAccessException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** M1-04 : démarrer, clôturer, compléter une séance, séance courante, contacts. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ClinicalTestConfig.class)
@RecordApplicationEvents
class SessionServiceIT {

  private static final Instant T0 = Instant.parse("2026-10-30T10:00:00Z");

  @Autowired private SessionService sessions;
  @Autowired private EntryService entries;
  @Autowired private EntryFacade facade;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private ApplicationEvents events;

  private ClinicalFixtures fx;
  private UUID patient;

  @BeforeEach
  void setUp() {
    fx = new ClinicalFixtures(jdbc);
    patient = fx.newPatient(LocalDate.of(1990, 4, 2));
    clock.set(T0);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- aides

  private UUID start() {
    return sessions.start(patient, new StartSessionCommand("in_person", null));
  }

  private UUID startAt(Instant from) {
    return sessions.start(patient, new StartSessionCommand("video", from));
  }

  private UUID note(UUID sessionId, TimeSpec time) {
    return entries.create(
        patient,
        new CreateEntryCommand(
            EntryType.NOTE, sessionId, null, "Une note", null, null, time, null, null, null));
  }

  private Instant instantOf(String sql, Object... args) {
    OffsetDateTime o = jdbc.queryForObject(sql, OffsetDateTime.class, args);
    return o == null ? null : o.toInstant();
  }

  private static void assertError(Runnable call, ClinicalError expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ClinicalException.class, e -> assertThat(e.error()).isEqualTo(expected));
  }

  // ---- une entrée prend la séance courante

  @Test
  void entryCreatedDuringOpenSessionGetsItsSessionIdWithoutSayingSo() {
    UUID session = start();

    UUID note = note(null, null);

    assertThat(jdbc.queryForObject("select session_id from entry where id = ?", UUID.class, note))
        .isEqualTo(session);
  }

  @Test
  void closedSessionAcceptsLaterEntriesRecordedAfterClosedAt() {
    UUID session = start();
    clock.advance(Duration.ofMinutes(50));
    sessions.close(session, new CloseSessionCommand(50, null));
    Instant closedAt = instantOf("select closed_at from entry_session where entry_id = ?", session);
    clock.advance(Duration.ofDays(3));
    Instant during = T0.plus(Duration.ofMinutes(10));

    UUID explicit = note(session, TimeSpec.exact(during));
    UUID implicit = note(null, TimeSpec.exact(during));

    for (UUID id : new UUID[] {explicit, implicit}) {
      Map<String, Object> row = jdbc.queryForMap("select session_id from entry where id = ?", id);
      assertThat(row.get("session_id")).isEqualTo(session);
      assertThat(instantOf("select recorded_at from entry where id = ?", id)).isAfter(closedAt);
      assertThat(instantOf("select occurred_from from entry where id = ?", id)).isEqualTo(during);
    }
  }

  @Test
  void entryWithoutSessionIdOnAPatientWithoutAnySessionIsStillRefused() {
    assertError(() -> note(null, null), ClinicalError.SESSION_REQUIRED);
  }

  // ---- démarrer

  @Test
  void secondStartWithoutCloseIsConflict() {
    start();

    assertThatThrownBy(this::start)
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> {
              assertThat(e.error()).isEqualTo(ClinicalError.SESSION_ALREADY_OPEN);
              assertThat(e.error().status()).isEqualTo(409);
            });
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry where patient_id = ? and type = 'session'",
                Integer.class,
                patient))
        .isEqualTo(1);
  }

  @Test
  void concurrentStartsLeaveExactlyOneOpenSession() throws Exception {
    int threads = 4;
    CyclicBarrier go = new CyclicBarrier(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<Object>> outcomes = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        outcomes.add(
            pool.submit(
                () -> {
                  fx.authenticate(fx.practitioner);
                  try {
                    go.await(10, TimeUnit.SECONDS);
                    return start();
                  } catch (ClinicalException e) {
                    return e.error();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      }
      List<Object> results = new ArrayList<>();
      for (Future<Object> f : outcomes) {
        results.add(f.get(30, TimeUnit.SECONDS));
      }
      assertThat(results.stream().filter(UUID.class::isInstance).count())
          .as("un seul start réussit")
          .isEqualTo(1);
      assertThat(results.stream().filter(ClinicalError.SESSION_ALREADY_OPEN::equals).count())
          .as("les autres reçoivent 409")
          .isEqualTo(threads - 1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry e join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = ? and s.closed_at is null",
                Integer.class,
                patient))
        .isEqualTo(1);
  }

  @Test
  void startCreatesAnOpenSessionStartingNowByDefault() {
    UUID session = start();

    Map<String, Object> row =
        jdbc.queryForMap(
            "select e.type, e.session_id, e.occurred_precision, e.occurred_to, e.author_type,"
                + " s.kind, s.modality, s.duration_min, s.closed_at, s.summary_version_id"
                + " from entry e join entry_session s on s.entry_id = e.id where e.id = ?",
            session);
    assertThat(row.get("type")).isEqualTo("session");
    assertThat(row.get("session_id")).isNull();
    assertThat(row.get("occurred_precision")).isEqualTo("exact");
    assertThat(row.get("occurred_to")).isNull();
    assertThat(row.get("author_type")).isEqualTo("practitioner");
    assertThat(row.get("kind")).isEqualTo("session");
    assertThat(row.get("modality")).isEqualTo("in_person");
    assertThat(row.get("duration_min")).isNull();
    assertThat(row.get("closed_at")).isNull();
    assertThat(row.get("summary_version_id")).isNull();
    assertThat(instantOf("select occurred_from from entry where id = ?", session)).isEqualTo(T0);
  }

  @Test
  void startPublishesEntryCreatedAndBumpsClinicalRev() {
    long before =
        jdbc.queryForObject("select clinical_rev from patient where id = ?", Long.class, patient);
    events.clear();

    UUID session = start();

    assertThat(
            jdbc.queryForObject(
                "select clinical_rev from patient where id = ?", Long.class, patient))
        .isEqualTo(before + 1);
    assertThat(events.stream(EntryCreated.class).toList())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getEntryId()).isEqualTo(session);
              assertThat(e.getEntryType()).isEqualTo("session");
              assertThat(e.getVersion()).isEqualTo(1);
            });
  }

  @Test
  void startWithAnInvalidModalityIsRefused() {
    assertThatThrownBy(() -> sessions.start(patient, new StartSessionCommand("telepathy", null)))
        .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
  }

  // ---- séance courante

  @Test
  void currentReturnsTheOpenSession() {
    UUID closed = start();
    sessions.close(closed, new CloseSessionCommand(30, null));
    UUID open = startAt(T0.plus(Duration.ofDays(1)));

    SessionView view = sessions.current(patient).orElseThrow();

    assertThat(view.id()).isEqualTo(open);
    assertThat(view.patientId()).isEqualTo(patient);
    assertThat(view.kind()).isEqualTo("session");
    assertThat(view.modality()).isEqualTo("video");
    assertThat(view.occurredFrom()).isEqualTo(T0.plus(Duration.ofDays(1)));
    assertThat(view.closedAt()).isNull();
    assertThat(view.durationMin()).isNull();
    assertThat(facade.openSessionOf(patient)).map(SessionView::id).contains(open);
  }

  @Test
  void currentReturnsTheLatestClosedSessionWhenNoneIsOpen() {
    Instant oct10 = Instant.parse("2026-10-10T14:00:00Z");
    Instant oct01 = Instant.parse("2026-10-01T14:00:00Z");
    // créées dans le désordre : la plus récente par occurred_from gagne
    UUID later = startAt(oct10);
    sessions.close(later, new CloseSessionCommand(50, null));
    UUID earlier = startAt(oct01);
    sessions.close(earlier, new CloseSessionCommand(50, null));

    SessionView view = sessions.current(patient).orElseThrow();
    assertThat(view.id()).isEqualTo(later);
    assertThat(view.closedAt()).isNotNull();
    assertThat(view.durationMin()).isEqualTo(50);
    assertThat(facade.openSessionOf(patient)).isEmpty();

    // un contact plus récent qu'une séance est renvoyé
    UUID contact =
        sessions.contact(
            patient,
            new ContactCommand("phone", Instant.parse("2026-10-15T09:00:00Z"), "Appel", null));
    SessionView withContact = sessions.current(patient).orElseThrow();
    assertThat(withContact.id()).isEqualTo(contact);
    assertThat(withContact.kind()).isEqualTo("contact");
  }

  @Test
  void currentFallsBackToTheIntakeSession() {
    Instant intakeAt = Instant.parse("2026-09-01T08:00:00Z");
    entries.createIntakeSession(fx.tenant, patient, fx.practitioner, intakeAt);

    SessionView intake = sessions.current(patient).orElseThrow();
    assertThat(intake.kind()).isEqualTo("intake");
    assertThat(intake.closedAt()).isNull();
    assertThat(intake.occurredFrom()).isEqualTo(intakeAt);
    assertThat(facade.openSessionOf(patient)).as("l'accueil n'est jamais ouverte").isEmpty();

    // l'accueil, ouvert par construction, ne bloque pas le démarrage d'une séance
    UUID session = start();
    SessionView view = sessions.current(patient).orElseThrow();
    assertThat(view.id()).isEqualTo(session);
    assertThat(view.kind()).isEqualTo("session");
    assertThat(facade.openSessionOf(patient)).map(SessionView::id).contains(session);

    // une fois la séance clôturée, elle prime encore sur l'accueil
    sessions.close(session, new CloseSessionCommand(40, null));
    assertThat(sessions.current(patient)).map(SessionView::id).contains(session);
  }

  @Test
  void currentIsEmptyWhenThePatientHasNoSessionAtAll() {
    assertThat(sessions.current(patient)).isEmpty();
    assertThat(facade.currentSession(patient)).isEmpty();
  }

  // ---- clôturer

  @Test
  void closeCreatesARealVersionCarryingTheSummaryEvenWithinGrace() {
    UUID session = start();
    UUID v1 =
        jdbc.queryForObject(
            "select current_version_id from entry where id = ?", UUID.class, session);
    clock.advance(Duration.ofMinutes(10)); // bien avant la fin de la fenêtre de grâce (75 min)
    events.clear();

    UUID versionId =
        sessions.close(session, new CloseSessionCommand(45, "Compte rendu : séance difficile."));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry_version where entry_id = ?", Integer.class, session))
        .isEqualTo(2);
    Map<String, Object> v2 =
        jdbc.queryForMap(
            "select id, body, reason, version from entry_version where entry_id = ? and version = 2",
            session);
    assertThat(v2.get("id")).isEqualTo(versionId);
    assertThat(v2.get("body")).isEqualTo("Compte rendu : séance difficile.");
    assertThat(v2.get("reason")).isEqualTo("Clôture de la séance");
    Map<String, Object> first =
        jdbc.queryForMap("select body, reason from entry_version where id = ?", v1);
    assertThat(first.get("body")).isNull();
    assertThat(first.get("reason")).isNull();

    Map<String, Object> entry =
        jdbc.queryForMap(
            "select current_version, current_version_id from entry where id = ?", session);
    assertThat(entry.get("current_version")).isEqualTo(2);
    assertThat(entry.get("current_version_id")).isEqualTo(versionId);
    Map<String, Object> detail =
        jdbc.queryForMap(
            "select duration_min, summary_version_id from entry_session where entry_id = ?",
            session);
    assertThat(detail.get("summary_version_id")).isEqualTo(versionId);
    assertThat(detail.get("duration_min")).isEqualTo(45);
    assertThat(instantOf("select closed_at from entry_session where entry_id = ?", session))
        .isEqualTo(clock.instant());
    assertThat(instantOf("select occurred_to from entry where id = ?", session))
        .isEqualTo(T0.plus(Duration.ofMinutes(45)));
    assertThat(instantOf("select occurred_from from entry where id = ?", session)).isEqualTo(T0);
    assertThat(events.stream(EntryCorrected.class).toList())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getEntryId()).isEqualTo(session);
              assertThat(e.getPreviousVersion()).isEqualTo(1);
              assertThat(e.getNewVersion()).isEqualTo(2);
            });
  }

  @Test
  void closeWithoutDurationTakesThePractitionerPreference() {
    jdbc.update(
        "update practitioner set preferences = '{\"sessions.defaultDurationMin\": 45}'::jsonb"
            + " where id = ?",
        fx.practitioner);
    UUID session = start();

    sessions.close(session, new CloseSessionCommand(null, null));

    assertThat(durationOf(session)).isEqualTo(45);
    assertThat(instantOf("select occurred_to from entry where id = ?", session))
        .isEqualTo(T0.plus(Duration.ofMinutes(45)));
  }

  @Test
  void closeWithoutDurationNorPreferenceFallsBackToSixtyMinutes() {
    jdbc.update("update practitioner set preferences = '{}'::jsonb where id = ?", fx.practitioner);
    UUID session = start();

    sessions.close(session, new CloseSessionCommand(null, null));

    assertThat(durationOf(session)).isEqualTo(60);
  }

  private int durationOf(UUID session) {
    return jdbc.queryForObject(
        "select duration_min from entry_session where entry_id = ?", Integer.class, session);
  }

  @Test
  void closeTwiceIsConflict() {
    UUID session = start();
    sessions.close(session, new CloseSessionCommand(30, null));

    assertThatThrownBy(() -> sessions.close(session, new CloseSessionCommand(30, null)))
        .isInstanceOfSatisfying(
            ClinicalException.class,
            e -> {
              assertThat(e.error()).isEqualTo(ClinicalError.SESSION_ALREADY_CLOSED);
              assertThat(e.error().status()).isEqualTo(409);
            });
  }

  @Test
  void closeOfANoteIsRefused() {
    UUID session = start();
    UUID aNote = note(session, null);

    assertError(
        () -> sessions.close(aNote, new CloseSessionCommand(30, null)),
        ClinicalError.SESSION_INVALID);
  }

  @Test
  void closeOfAnUnknownEntryIsRefused() {
    assertError(
        () -> sessions.close(UUID.randomUUID(), new CloseSessionCommand(30, null)),
        ClinicalError.ENTRY_NOT_FOUND);
  }

  @Test
  void closeOfAnArchivedSessionIsRefused() {
    UUID session = start();
    entries.archive(session);

    assertError(
        () -> sessions.close(session, new CloseSessionCommand(30, null)),
        ClinicalError.ENTRY_ARCHIVED);
  }

  // ---- contacts

  @Test
  void contactIsClosedImmediatelyAndCarriesItsBody() {
    Instant when = Instant.parse("2026-10-20T16:30:00Z");

    UUID contact =
        sessions.contact(
            patient, new ContactCommand("phone", when, "Appel du patient : crise.", null));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select s.kind, s.modality, s.duration_min, s.summary_version_id, e.session_id,"
                + " e.current_version_id, e.current_version"
                + " from entry e join entry_session s on s.entry_id = e.id where e.id = ?",
            contact);
    assertThat(row.get("kind")).isEqualTo("contact");
    assertThat(row.get("modality")).isEqualTo("phone");
    assertThat(row.get("duration_min")).isNull();
    assertThat(row.get("session_id")).isNull();
    assertThat(row.get("current_version")).isEqualTo(1);
    assertThat(row.get("summary_version_id")).isEqualTo(row.get("current_version_id"));
    assertThat(instantOf("select closed_at from entry_session where entry_id = ?", contact))
        .isEqualTo(T0);
    assertThat(instantOf("select occurred_from from entry where id = ?", contact)).isEqualTo(when);
    assertThat(instantOf("select occurred_to from entry where id = ?", contact)).isEqualTo(when);
    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where id = ?",
                String.class,
                row.get("summary_version_id")))
        .isEqualTo("Appel du patient : crise.");
  }

  @Test
  void contactWithADurationEndsAfterIt() {
    Instant when = Instant.parse("2026-10-20T16:30:00Z");

    UUID contact = sessions.contact(patient, new ContactCommand("email", when, "Échange", 15));

    assertThat(durationOf(contact)).isEqualTo(15);
    assertThat(instantOf("select occurred_to from entry where id = ?", contact))
        .isEqualTo(when.plus(Duration.ofMinutes(15)));
  }

  @Test
  void contactDoesNotTouchTheOpenSessionNorBlockAnything() {
    UUID open = start();

    sessions.contact(patient, new ContactCommand("phone", T0, "Appel", null));

    assertThat(sessions.current(patient)).map(SessionView::id).contains(open);
    assertThat(facade.openSessionOf(patient)).map(SessionView::id).contains(open);
    assertThat(
            jdbc.queryForObject(
                "select closed_at from entry_session where entry_id = ?", Object.class, open))
        .isNull();
    // et il ne bloque pas la clôture ni un nouveau contact
    sessions.contact(patient, new ContactCommand("email", T0, "Mail", null));
    sessions.close(open, new CloseSessionCommand(30, null));
  }

  @Test
  void contactWithInPersonModalityIsRefused() {
    assertError(
        () -> sessions.contact(patient, new ContactCommand("in_person", T0, "Texte", null)),
        ClinicalError.SESSION_INVALID);
    assertError(
        () -> sessions.contact(patient, new ContactCommand(null, T0, "Texte", null)),
        ClinicalError.SESSION_INVALID);
  }

  @Test
  void contactWithoutDateOrBodyIsRefused() {
    assertError(
        () -> sessions.contact(patient, new ContactCommand("phone", null, "Texte", null)),
        ClinicalError.INVALID_TIME);
    assertError(
        () -> sessions.contact(patient, new ContactCommand("phone", T0, "  ", null)),
        ClinicalError.SESSION_INVALID);
    assertError(
        () -> sessions.contact(patient, new ContactCommand("phone", T0, null, null)),
        ClinicalError.SESSION_INVALID);
  }

  // ---- séances archivées

  @Test
  void archivedOpenSessionNoLongerBlocksNorIsCurrent() {
    UUID closed = start();
    sessions.close(closed, new CloseSessionCommand(30, null));
    UUID open = startAt(T0.plus(Duration.ofDays(1)));
    assertThat(sessions.current(patient)).map(SessionView::id).contains(open);

    entries.archive(open);

    assertThat(sessions.current(patient)).map(SessionView::id).contains(closed);
    assertThat(facade.openSessionOf(patient)).isEmpty();
    UUID again = startAt(T0.plus(Duration.ofDays(2)));
    assertThat(sessions.current(patient)).map(SessionView::id).contains(again);
    // une note sans sessionId ne va jamais à la séance archivée
    UUID aNote = note(null, null);
    assertThat(jdbc.queryForObject("select session_id from entry where id = ?", UUID.class, aNote))
        .isEqualTo(again);
    // ni explicitement
    assertError(() -> note(open, null), ClinicalError.SESSION_INVALID);
  }

  @Test
  void entriesOfAnArchivedSessionStayAttachedAndCorrectable() {
    UUID session = start();
    UUID aNote = note(null, null);
    clock.advance(Duration.ofMinutes(76)); // hors grâce : la correction crée une version

    entries.archive(session);
    entries.correct(
        aNote,
        new CorrectEntryCommand(
            null, "Note corrigée", null, null, null, null, null, null, "Précision"));

    assertThat(jdbc.queryForObject("select session_id from entry where id = ?", UUID.class, aNote))
        .isEqualTo(session);
    assertThat(
            jdbc.queryForObject(
                "select current_version from entry where id = ?", Integer.class, aNote))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select archived_at is null from entry where id = ?", Boolean.class, aNote))
        .isTrue();
  }

  // ---- propriété et authentification

  @Test
  void startCloseContactAndCurrentRequireOwnership() {
    UUID session = start();
    fx.authenticate(fx.newPractitioner());

    assertThatThrownBy(() -> sessions.start(patient, new StartSessionCommand("in_person", null)))
        .isInstanceOf(PatientAccessException.class);
    assertThatThrownBy(() -> sessions.close(session, new CloseSessionCommand(30, null)))
        .isInstanceOf(PatientAccessException.class);
    assertThatThrownBy(
            () -> sessions.contact(patient, new ContactCommand("phone", T0, "Appel", null)))
        .isInstanceOf(PatientAccessException.class);
    assertThatThrownBy(() -> sessions.current(patient)).isInstanceOf(PatientAccessException.class);
  }

  @Test
  void facadeCurrentSessionIsEmptyForAnotherPractitioner() {
    UUID session = start();
    assertThat(facade.currentSession(patient)).map(SessionView::id).contains(session);
    assertThat(facade.openSessionOf(patient)).map(SessionView::id).contains(session);

    fx.authenticate(fx.newPractitioner());
    assertThat(facade.currentSession(patient)).isEmpty();
    assertThat(facade.openSessionOf(patient)).isEmpty();

    SecurityContextHolder.clearContext();
    assertThat(facade.currentSession(patient)).isEmpty();
    assertThat(facade.openSessionOf(patient)).isEmpty();
  }

  @Test
  void unauthenticatedCallsAreRefused() {
    SecurityContextHolder.clearContext();

    assertError(() -> start(), ClinicalError.UNAUTHENTICATED);
    assertError(() -> sessions.current(patient), ClinicalError.UNAUTHENTICATED);
    assertError(
        () -> sessions.contact(patient, new ContactCommand("phone", T0, "Appel", null)),
        ClinicalError.UNAUTHENTICATED);
    assertError(
        () -> sessions.close(UUID.randomUUID(), new CloseSessionCommand(null, null)),
        ClinicalError.UNAUTHENTICATED);
  }
}
