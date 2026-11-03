package com.psymate.linking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.service.CorrectEntryCommand;
import com.psymate.clinical.service.CorrectionResult;
import com.psymate.clinical.service.EntryService;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.linking.LinkingFixtures;
import com.psymate.linking.api.LinkAccepted;
import com.psymate.linking.api.LinkRejected;
import com.psymate.linking.model.Link;
import com.psymate.linking.model.LinkError;
import com.psymate.linking.model.LinkException;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.testsupport.SharedPostgres;
import com.psymate.vocabulary.api.LinkTypeView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cycle de vie des liens (M2-01, A7, UC-06) sur PostgreSQL réel : règles, compteurs, événements.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({SharedPostgres.class, LinkServiceIT.ListenerConfig.class})
class LinkServiceIT {

  /**
   * Abonnés de test : le registre Modulith n'écrit une publication que s'il existe au moins un
   * abonné pour l'événement.
   */
  static class OutboxListeners {
    @ApplicationModuleListener
    void on(LinkAccepted event) {}

    @ApplicationModuleListener
    void on(LinkRejected event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    OutboxListeners linkOutboxListeners() {
      return new OutboxListeners();
    }
  }

  @Autowired private LinkService links;
  @Autowired private EntryService entries;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;

  private LinkingFixtures fx;
  private UUID patient;
  private UUID session;
  private UUID event;
  private UUID feeling;

  @BeforeEach
  void setUp() {
    fx = new LinkingFixtures(jdbc, entries);
    patient = fx.newPatient();
    session = fx.session(patient);
    event = fx.entry(patient, session, EntryType.EVENT);
    feeling = fx.entry(patient, session, EntryType.FEELING);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private static LinkException refused(ThrowingCallable action) {
    Throwable thrown = catchThrowable(action);
    assertThat(thrown).isInstanceOf(LinkException.class);
    return (LinkException) thrown;
  }

  private static void assertForbidden(ThrowingCallable action) {
    assertThat(catchThrowable(action))
        .isInstanceOfSatisfying(
            PatientAccessException.class, e -> assertThat(e.status().value()).isEqualTo(403));
  }

  private int linkRows() {
    return fx.count("select count(*) from link where patient_id = ?", patient);
  }

  /** Événements publiés (un par événement, quel que soit le nombre d'abonnés). */
  private int publications(Class<?> type, UUID linkId) {
    return fx.count(
        "select count(distinct (serialized_event::jsonb ->> 'id')) from event_publication"
            + " where event_type = ? and serialized_event::text like ?",
        type.getName(),
        "%" + linkId + "%");
  }

  private Map<String, Object> row(UUID linkId) {
    return jdbc.queryForMap("select * from link where id = ?", linkId);
  }

  // ---- création

  @Test
  void createStoresAManualAcceptedLinkAndMaintainsCountersRevisionAndEvent() {
    int revBefore = fx.clinicalRev(patient);

    Link link = links.create(event, feeling, "triggers", "  depuis le décès  ");

    Map<String, Object> row = row(link.id());
    assertThat(row)
        .containsEntry("patient_id", patient)
        .containsEntry("tenant_id", fx.tenant)
        .containsEntry("source_id", event)
        .containsEntry("target_id", feeling)
        .containsEntry("link_type", "triggers")
        .containsEntry("directed", true)
        .containsEntry("provenance", "manual")
        .containsEntry("status", "accepted")
        .containsEntry("rationale", "depuis le décès")
        .containsEntry("author_id", fx.practitioner)
        .containsEntry("version", 1);
    assertThat(row.get("superseded_by")).isNull();
    assertThat(row.get("confidence")).isNull();
    assertThat(fx.linkCount(event)).isEqualTo(1);
    assertThat(fx.linkCount(feeling)).isEqualTo(1);
    assertThat(fx.clinicalRev(patient)).isEqualTo(revBefore + 1);
    assertThat(publications(LinkAccepted.class, link.id())).isEqualTo(1);
  }

  @Test
  void createdLinksAreReadableThroughTheService() {
    Link link = links.create(event, feeling, "triggers", null);

    assertThat(links.acceptedLinksOf(event)).hasSize(1);
    assertThat(links.acceptedLinksOf(feeling))
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(link.id());
              assertThat(v.sourceId()).isEqualTo(event);
              assertThat(v.targetId()).isEqualTo(feeling);
              assertThat(v.provenance().code()).isEqualTo("manual");
              assertThat(v.authorId()).isEqualTo(fx.practitioner);
            });
  }

  @Test
  void noteToFeelingTriggersIsRefusedWithTheTypesThatAreAllowed() {
    UUID note = fx.entry(patient, session, EntryType.NOTE);
    int revBefore = fx.clinicalRev(patient);

    LinkException error = refused(() -> links.create(note, feeling, "triggers", null));

    assertThat(error.error()).isEqualTo(LinkError.LINK_TYPE_NOT_ALLOWED);
    assertThat(error.allowedTypes())
        .extracting(LinkTypeView::code)
        .containsExactly("co_occurs", "follows");
    assertThat(linkRows()).isZero();
    assertThat(fx.linkCount(note)).isZero();
    assertThat(fx.clinicalRev(patient)).isEqualTo(revBefore);
  }

  @Test
  void eventToFeelingTriggersIsAllowed() {
    assertThat(links.create(event, feeling, "triggers", null).status().code())
        .isEqualTo("accepted");
  }

  @Test
  void anActiveDuplicateIsRefusedWithTheExistingLinkId() {
    Link first = links.create(event, feeling, "triggers", null);

    LinkException error = refused(() -> links.create(event, feeling, "triggers", null));

    assertThat(error.error()).isEqualTo(LinkError.LINK_EXISTS);
    assertThat(error.existingLinkId()).isEqualTo(first.id());
    assertThat(linkRows()).isEqualTo(1);
    assertThat(fx.linkCount(event)).isEqualTo(1);
  }

  @Test
  void aSymmetricTypeIsADuplicateInTheOppositeDirectionToo() {
    UUID note = fx.entry(patient, session, EntryType.NOTE);
    Link first = links.create(note, feeling, "co_occurs", null);

    LinkException error = refused(() -> links.create(feeling, note, "co_occurs", null));

    assertThat(error.error()).isEqualTo(LinkError.LINK_EXISTS);
    assertThat(error.existingLinkId()).isEqualTo(first.id());
  }

  @Test
  void aDirectedTypeInTheOppositeDirectionIsAnotherLink() {
    UUID other = fx.entry(patient, session, EntryType.EVENT);
    links.create(event, other, "follows", null);

    assertThat(links.create(other, event, "follows", null).id()).isNotNull();
    assertThat(linkRows()).isEqualTo(2);
  }

  @Test
  void aSupersededLinkCanBeCreatedAgain() {
    Link first = links.create(event, feeling, "triggers", null);
    links.remove(first.id());

    Link again = links.create(event, feeling, "triggers", null);

    assertThat(again.id()).isNotEqualTo(first.id());
    assertThat(fx.linkCount(event)).isEqualTo(1);
  }

  @Test
  void aSelfLinkIsRefused() {
    assertThat(refused(() -> links.create(event, event, "follows", null)).error())
        .isEqualTo(LinkError.SELF_LINK);
    assertThat(linkRows()).isZero();
  }

  @Test
  void unknownTypeIsRefused() {
    assertThat(refused(() -> links.create(event, feeling, "causes", null)).error())
        .isEqualTo(LinkError.UNKNOWN_LINK_TYPE);
  }

  @Test
  void twoPatientsOfTheSamePractitionerCannotBeLinked() {
    UUID otherPatient = fx.newPatient();
    UUID otherFeeling = fx.entry(otherPatient, fx.session(otherPatient), EntryType.FEELING);

    LinkException error = refused(() -> links.create(event, otherFeeling, "triggers", null));

    assertThat(error.error()).isEqualTo(LinkError.DIFFERENT_PATIENT);
    assertThat(fx.linkCount(event)).isZero();
    assertThat(fx.linkCount(otherFeeling)).isZero();
  }

  @Test
  void anotherPractitionersEntryIsForbiddenAndAMissingOneIsNotFound() {
    fx.authenticate(fx.newPractitioner());
    UUID strangerPatient = fx.newPatient();
    UUID strangerEntry = fx.entry(strangerPatient, fx.session(strangerPatient), EntryType.FEELING);
    fx.authenticate(fx.practitioner);

    assertForbidden(() -> links.create(event, strangerEntry, "triggers", null));
    assertForbidden(() -> links.create(strangerEntry, event, "triggers", null));
    assertForbidden(() -> links.acceptedLinksOf(strangerEntry));
    assertThat(refused(() -> links.create(event, UUID.randomUUID(), "triggers", null)).error())
        .isEqualTo(LinkError.ENTRY_NOT_FOUND);
    assertThat(refused(() -> links.create(UUID.randomUUID(), event, "triggers", null)).error())
        .isEqualTo(LinkError.ENTRY_NOT_FOUND);
    assertThat(refused(() -> links.acceptedLinksOf(UUID.randomUUID())).error())
        .isEqualTo(LinkError.ENTRY_NOT_FOUND);
    assertThat(linkRows()).isZero();
    assertThat(fx.linkCount(strangerEntry)).isZero();
    assertThat(fx.linkCount(event)).isZero();
  }

  @Test
  void aLinkOfAnotherPractitionerCannotBeChangedNorRemoved() {
    Link link = links.create(event, feeling, "triggers", null);
    fx.authenticate(fx.newPractitioner());

    assertForbidden(() -> links.changeType(link.id(), "worsens"));
    assertForbidden(() -> links.remove(link.id()));

    assertThat(row(link.id())).containsEntry("status", "accepted");
    assertThat(fx.linkCount(event)).isEqualTo(1);
  }

  @Test
  void withoutAPractitionerNothingIsWritten() {
    SecurityContextHolder.clearContext();

    assertThat(refused(() -> links.create(event, feeling, "triggers", null)).error())
        .isEqualTo(LinkError.UNAUTHENTICATED);
    assertThat(linkRows()).isZero();
  }

  @Test
  void concurrentCreationsOfTheSameLinkLeaveExactlyOne() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Object>> tasks = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        tasks.add(
            () -> {
              fx.authenticate(fx.practitioner);
              try {
                return links.create(event, feeling, "triggers", null);
              } catch (LinkException e) {
                return e;
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
      }
      int created = 0;
      for (Future<Object> future : pool.invokeAll(tasks)) {
        Object result = future.get();
        if (result instanceof Link) {
          created++;
        } else {
          LinkException error = (LinkException) result;
          assertThat(error.error()).isEqualTo(LinkError.LINK_EXISTS);
          assertThat(error.existingLinkId()).isNotNull();
        }
      }
      assertThat(created).isEqualTo(1);
      assertThat(linkRows()).isEqualTo(1);
      assertThat(fx.linkCount(event)).isEqualTo(1);
      assertThat(fx.linkCount(feeling)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  // ---- changement de type

  @Test
  void changingTheTypeSupersedesTheOldLinkAndCreatesANewManualOne() {
    Link old = links.create(event, feeling, "triggers", "parce que");
    int revBefore = fx.clinicalRev(patient);

    Link replacement = links.changeType(old.id(), "worsens");

    Map<String, Object> oldRow = row(old.id());
    assertThat(oldRow)
        .containsEntry("status", "superseded")
        .containsEntry("superseded_by", replacement.id())
        .containsEntry("decided_by", fx.practitioner);
    assertThat(oldRow.get("decided_at")).isNotNull();
    assertThat(row(replacement.id()))
        .containsEntry("status", "accepted")
        .containsEntry("provenance", "manual")
        .containsEntry("link_type", "worsens")
        .containsEntry("source_id", event)
        .containsEntry("target_id", feeling)
        .containsEntry("rationale", "parce que")
        .containsEntry("author_id", fx.practitioner);
    assertThat(fx.linkCount(event)).isEqualTo(1);
    assertThat(fx.linkCount(feeling)).isEqualTo(1);
    assertThat(fx.clinicalRev(patient)).isEqualTo(revBefore + 1);
    assertThat(publications(LinkAccepted.class, replacement.id())).isEqualTo(1);
    assertThat(links.acceptedLinksOf(event))
        .extracting(v -> v.id())
        .containsExactly(replacement.id());
  }

  @Test
  void changingToATypeTheMatrixForbidsLeavesEverythingUntouched() {
    Link old = links.create(event, feeling, "triggers", null);

    LinkException error = refused(() -> links.changeType(old.id(), "treats"));

    assertThat(error.error()).isEqualTo(LinkError.LINK_TYPE_NOT_ALLOWED);
    assertThat(error.allowedTypes()).extracting(LinkTypeView::code).contains("triggers");
    assertThat(row(old.id())).containsEntry("status", "accepted");
    assertThat(linkRows()).isEqualTo(1);
  }

  @Test
  void changingToTheCurrentTypeIsADuplicate() {
    Link old = links.create(event, feeling, "triggers", null);

    LinkException error = refused(() -> links.changeType(old.id(), "triggers"));

    assertThat(error.error()).isEqualTo(LinkError.LINK_EXISTS);
    assertThat(error.existingLinkId()).isEqualTo(old.id());
  }

  @Test
  void changingToATypeAlreadyLinkedIsADuplicate() {
    Link triggers = links.create(event, feeling, "triggers", null);
    Link worsens = links.create(event, feeling, "worsens", null);

    LinkException error = refused(() -> links.changeType(triggers.id(), "worsens"));

    assertThat(error.error()).isEqualTo(LinkError.LINK_EXISTS);
    assertThat(error.existingLinkId()).isEqualTo(worsens.id());
    assertThat(row(triggers.id())).containsEntry("status", "accepted");
  }

  @Test
  void aSupersededLinkCannotBeChangedAgain() {
    Link old = links.create(event, feeling, "triggers", null);
    links.changeType(old.id(), "worsens");

    assertThat(refused(() -> links.changeType(old.id(), "relieves")).error())
        .isEqualTo(LinkError.LINK_NOT_ACTIVE);
    assertThat(refused(() -> links.changeType(UUID.randomUUID(), "relieves")).error())
        .isEqualTo(LinkError.LINK_NOT_FOUND);
  }

  // ---- entrées archivées (A7 : une entrée archivée n'a que des liens superseded)

  @Test
  void anArchivedSourceOrTargetCannotBeLinked() {
    UUID archived = fx.entry(patient, session, EntryType.EVENT);
    entries.archive(archived);

    LinkException asSource = refused(() -> links.create(archived, feeling, "triggers", null));
    LinkException asTarget = refused(() -> links.create(event, archived, "follows", null));

    assertThat(asSource.error()).isEqualTo(LinkError.ENTRY_ARCHIVED);
    assertThat(asSource.field()).isEqualTo("sourceId");
    assertThat(asTarget.error()).isEqualTo(LinkError.ENTRY_ARCHIVED);
    assertThat(asTarget.field()).isEqualTo("targetId");
    assertThat(linkRows()).isZero();
    assertThat(fx.linkCount(archived)).isZero();
    assertThat(fx.linkCount(feeling)).isZero();
  }

  @Test
  void changingTheTypeOfALinkWithAnArchivedEndIsRefusedAsArchivedUntilTheSubscriberRuns() {
    Link link = links.create(event, feeling, "triggers", null);
    // Archivage sans événement : l'abonné n'est pas passé, le lien est encore actif.
    jdbc.update("update entry set archived_at = now() where id = ?", feeling);

    LinkException error = refused(() -> links.changeType(link.id(), "worsens"));

    assertThat(error.error()).isEqualTo(LinkError.ENTRY_ARCHIVED);
    assertThat(error.field()).isEqualTo("targetId");
    assertThat(row(link.id())).containsEntry("status", "accepted");
    assertThat(linkRows()).isEqualTo(1);
  }

  @Test
  void changingTheTypeAfterTheSubscriberRanIsRefusedAsNotActive() {
    Link link = links.create(event, feeling, "triggers", null);
    fx.awaitPublicationsSettled(patient);
    entries.archive(feeling);
    await().atMost(Duration.ofSeconds(15)).until(() -> activeLinksOf(feeling) == 0);

    assertThat(refused(() -> links.changeType(link.id(), "worsens")).error())
        .isEqualTo(LinkError.LINK_NOT_ACTIVE);
  }

  // ---- suppression

  @Test
  void removingALinkSupersedesItAndLowersBothCounters() {
    Link link = links.create(event, feeling, "triggers", null);
    int revBefore = fx.clinicalRev(patient);

    links.remove(link.id());

    assertThat(row(link.id()))
        .containsEntry("status", "superseded")
        .containsEntry("decided_by", fx.practitioner);
    assertThat(row(link.id()).get("superseded_by")).isNull();
    assertThat(fx.linkCount(event)).isZero();
    assertThat(fx.linkCount(feeling)).isZero();
    assertThat(fx.clinicalRev(patient)).isEqualTo(revBefore + 1);
    assertThat(links.acceptedLinksOf(event)).isEmpty();
    // Rien n'est supprimé physiquement, et LinkRejected est réservé à l'IA (V2).
    assertThat(linkRows()).isEqualTo(1);
    assertThat(publications(LinkRejected.class, link.id())).isZero();
  }

  @Test
  void removingTwiceChangesNothingTheSecondTime() {
    Link link = links.create(event, feeling, "triggers", null);
    links.remove(link.id());
    int revAfterFirst = fx.clinicalRev(patient);

    links.remove(link.id());

    assertThat(fx.linkCount(event)).isZero();
    assertThat(fx.clinicalRev(patient)).isEqualTo(revAfterFirst);
    assertThat(refused(() -> links.remove(UUID.randomUUID())).error())
        .isEqualTo(LinkError.LINK_NOT_FOUND);
  }

  // ---- archivage d'une entrée

  private int activeLinksOf(UUID entryId) {
    return fx.count(
        "select count(*) from link where status = 'accepted' and (source_id = ? or target_id = ?)",
        entryId,
        entryId);
  }

  private int completedArchiveRuns(String needle) {
    return fx.count(
        "select count(*) from event_publication where listener_id = 'linking.entry-archived'"
            + " and completion_date is not null and serialized_event::text like ?",
        "%" + needle + "%");
  }

  @Test
  void archivingAnEntrySupersedesItsLinksAndFixesTheCounters() {
    UUID other = fx.entry(patient, session, EntryType.EVENT);
    Link a = links.create(event, feeling, "triggers", null);
    Link b = links.create(other, event, "follows", null);
    Link kept = links.create(other, feeling, "triggers", null);
    assertThat(fx.linkCount(event)).isEqualTo(2);
    assertThat(fx.linkCount(feeling)).isEqualTo(2);
    assertThat(fx.linkCount(other)).isEqualTo(2);

    fx.awaitPublicationsSettled(patient);
    entries.archive(event);

    await().atMost(Duration.ofSeconds(15)).until(() -> activeLinksOf(event) == 0);
    assertThat(row(a.id())).containsEntry("status", "superseded");
    assertThat(row(b.id())).containsEntry("status", "superseded");
    assertThat(row(a.id()).get("superseded_by")).isNull();
    assertThat(row(kept.id())).containsEntry("status", "accepted");
    assertThat(fx.linkCount(event)).isZero();
    assertThat(fx.linkCount(feeling)).isEqualTo(1);
    assertThat(fx.linkCount(other)).isEqualTo(1);
    await().atMost(Duration.ofSeconds(15)).until(() -> completedArchiveRuns(event.toString()) == 1);
  }

  @Test
  void theArchiveSubscriberIsIdempotent() {
    UUID other = fx.entry(patient, session, EntryType.EVENT);
    links.create(event, feeling, "triggers", null);
    links.create(other, feeling, "triggers", null);
    fx.awaitPublicationsSettled(patient);
    entries.archive(event);
    await().atMost(Duration.ofSeconds(15)).until(() -> activeLinksOf(event) == 0);
    assertThat(fx.linkCount(feeling)).isEqualTo(1);
    int revAfter = fx.clinicalRev(patient);

    // Même service rappelé, puis même événement publié deux fois : rien ne bouge.
    links.supersedeLinksOf(event);
    EntryArchived replay = new EntryArchived(fx.tenant, event, patient, "event", 1);
    tx.executeWithoutResult(s -> publisher.publish(replay));
    tx.executeWithoutResult(s -> publisher.publish(replay));
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> completedArchiveRuns(replay.getId().toString()) == 2);

    assertThat(fx.linkCount(feeling)).isEqualTo(1);
    assertThat(fx.linkCount(other)).isEqualTo(1);
    assertThat(fx.clinicalRev(patient)).isEqualTo(revAfter);
  }

  // ---- règle de grâce

  @Test
  void aLinkedEntryIsCorrectedByANewVersionEvenInsideTheGraceWindow() {
    UUID plain = fx.entry(patient, session, EntryType.NOTE);
    UUID linkedNote = fx.entry(patient, session, EntryType.NOTE);
    CorrectEntryCommand edit =
        new CorrectEntryCommand(
            null, "Texte précisé", null, null, null, null, null, null, "précision");

    // Témoin : sans lien, la correction toute fraîche est faite en place (grâce).
    CorrectionResult inPlace = entries.correct(plain, edit);
    assertThat(inPlace.versioned()).isFalse();
    assertThat(fx.count("select count(*) from entry_version where entry_id = ?", plain))
        .isEqualTo(1);

    links.create(linkedNote, feeling, "follows", null);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update entry_version set body = 'modifié en place' where entry_id = ?",
                    linkedNote))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("liée");

    CorrectionResult versioned = entries.correct(linkedNote, edit);

    assertThat(versioned.versioned()).isTrue();
    assertThat(versioned.version()).isEqualTo(2);
    assertThat(fx.count("select count(*) from entry_version where entry_id = ?", linkedNote))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where entry_id = ? and version = 1",
                String.class,
                linkedNote))
        .isEqualTo("Une note");
  }

  @Test
  void removingTheLastLinkGivesTheGraceWindowBack() {
    UUID note = fx.entry(patient, session, EntryType.NOTE);
    Link link = links.create(note, feeling, "follows", null);
    links.remove(link.id());

    CorrectionResult result =
        entries.correct(
            note,
            new CorrectEntryCommand(
                null, "Texte précisé", null, null, null, null, null, null, "précision"));

    assertThat(result.versioned()).isFalse();
  }
}
