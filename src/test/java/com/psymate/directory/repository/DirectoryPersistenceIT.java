package com.psymate.directory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.psymate.directory.DirectoryTestConfig;
import com.psymate.directory.DirectoryTestFixtures;
import com.psymate.directory.api.ProfilePublished;
import com.psymate.directory.api.ProfileUnpublished;
import com.psymate.directory.dto.CreateRequest;
import com.psymate.directory.dto.ItemDto;
import com.psymate.directory.dto.ListDto;
import com.psymate.directory.dto.ProfileDto;
import com.psymate.directory.dto.UpdateProfileRequest;
import com.psymate.directory.model.DirectoryVocabularyException;
import com.psymate.directory.service.ContactRequestService;
import com.psymate.directory.service.DirectoryVocabularyAdminService;
import com.psymate.directory.service.ProfileService;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistance JPA du module directory (M8-04) : création idempotente du profil (le premier gagne),
 * publication et dépublication sans second événement, deux publications concurrentes qui ne
 * produisent qu'un événement, liste d'administration sans N+1, purge physique et 409 d'unicité
 * attrapé après le commit.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Import({DirectoryTestConfig.class, DirectoryPersistenceIT.EventSink.class})
class DirectoryPersistenceIT {

  /** Collecte les événements du module au moment où ils sont publiés. */
  static class Events {
    final List<UUID> published = new CopyOnWriteArrayList<>();
    final List<UUID> unpublished = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ProfilePublished event) {
      published.add(event.getPractitionerId());
    }

    @EventListener
    void on(ProfileUnpublished event) {
      unpublished.add(event.getPractitionerId());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class EventSink {
    @Bean
    Events directoryEvents() {
      return new Events();
    }
  }

  @Autowired private ProfileService profiles;
  @Autowired private PractitionerProfileRepository profileRepository;
  @Autowired private DirectoryVocabularyAdminService vocabulary;
  @Autowired private ContactRequestService contacts;
  @Autowired private Events events;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;
  @Autowired private EntityManagerFactory emf;

  private DirectoryTestFixtures fx;
  private UUID alice;

  @BeforeEach
  void practitioner() {
    fx = new DirectoryTestFixtures(jdbc);
    alice = fx.newPractitioner();
    fx.loginAs(alice);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private boolean publishedColumn(UUID id) {
    return jdbc.queryForObject(
        "select published from practitioner_profile where practitioner_id = ?", Boolean.class, id);
  }

  private static String freshCode() {
    return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  // ---- profil

  @Test
  void insertIfAbsentIsIdempotentAndTheFirstCallWins() {
    tx.executeWithoutResult(
        s -> {
          profileRepository.insertIfAbsent(alice, "premier-slug-" + alice, "Premier");
          profileRepository.insertIfAbsent(alice, "second-slug-" + alice, "Second");
        });

    assertThat(
            jdbc.queryForObject(
                "select count(*) from practitioner_profile where practitioner_id = ?",
                Integer.class,
                alice))
        .isEqualTo(1);
    var row =
        jdbc.queryForMap("select * from practitioner_profile where practitioner_id = ?", alice);
    assertThat(row.get("slug")).isEqualTo("premier-slug-" + alice);
    assertThat(row.get("display_name")).isEqualTo("Premier");
    // Les DEFAULT du DDL s'appliquent (c'est pourquoi l'insertion reste native).
    assertThat(row.get("published")).isEqualTo(false);
    assertThat(row.get("country")).isEqualTo("FR");
    assertThat(row.get("availability")).isEqualTo("unknown");
    assertThat(row.get("updated_at")).isNotNull();
  }

  @Test
  void getCreatesTheEmptyProfileOnceAndUpdateNormalisesThenFiresTheSearchTrigger() {
    ProfileDto first = profiles.get();
    ProfileDto again = profiles.get();
    assertThat(first.slug()).isNotBlank().isEqualTo(again.slug());
    assertThat(first.published()).isFalse();
    assertThat(first.country()).isEqualTo("FR");
    assertThat(first.availability()).isEqualTo("unknown");
    assertThat(first.specialties()).isEmpty();

    ProfileDto edited = profiles.update(DirectoryTestFixtures.completeProfile("  Une bio  "));

    assertThat(edited.displayName()).isEqualTo("Dr Persistance");
    assertThat(edited.bio()).isEqualTo("Une bio");
    assertThat(edited.country()).isEqualTo("FR");
    assertThat(edited.showPhone()).isFalse();
    assertThat(edited.showEmail()).isTrue();
    assertThat(edited.specialties()).containsExactly("anxiety");
    assertThat(edited.slug()).isEqualTo(first.slug());
    var row =
        jdbc.queryForMap("select * from practitioner_profile where practitioner_id = ?", alice);
    assertThat(row.get("bio")).isEqualTo("Une bio");
    assertThat(row.get("next_slot_at").toString()).isEqualTo("2030-01-15");
    assertThat(row.get("postal_code")).isEqualTo("69001");
    assertThat(
            jdbc.queryForObject(
                "select search_vector is not null from practitioner_profile"
                    + " where practitioner_id = ?",
                Boolean.class,
                alice))
        .as("le trigger écrit search_vector à l'UPDATE Hibernate")
        .isTrue();
  }

  @Test
  void publishAndUnpublishAreIdempotentAndEmitOneEventEach() {
    profiles.update(DirectoryTestFixtures.completeProfile(null));

    profiles.publish();
    profiles.publish();
    assertThat(publishedColumn(alice)).isTrue();
    assertThat(events.published).containsOnlyOnce(alice);
    var publishedAt =
        jdbc.queryForObject(
            "select published_at from practitioner_profile where practitioner_id = ?",
            java.sql.Timestamp.class,
            alice);
    assertThat(publishedAt).isNotNull();

    profiles.unpublish();
    profiles.unpublish();
    profiles.unpublishByPractitioner(fx.tenant(), alice);
    assertThat(publishedColumn(alice)).isFalse();
    assertThat(events.unpublished).containsOnlyOnce(alice);
    assertThat(
            jdbc.queryForObject(
                "select published_at from practitioner_profile where practitioner_id = ?",
                java.sql.Timestamp.class,
                alice))
        .as("published_at est inchangé par la dépublication")
        .isEqualTo(publishedAt);
  }

  @Test
  void twoConcurrentPublicationsProduceASingleEvent() throws Exception {
    profiles.update(DirectoryTestFixtures.completeProfile(null));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      Callable<Void> publish =
          () -> {
            fx.loginAs(alice);
            start.await();
            profiles.publish();
            return null;
          };
      Future<Void> a = pool.submit(publish);
      Future<Void> b = pool.submit(publish);
      start.countDown();
      a.get();
      b.get();
    } finally {
      pool.shutdownNow();
    }
    assertThat(publishedColumn(alice)).isTrue();
    assertThat(events.published.stream().filter(alice::equals).count()).isEqualTo(1);
  }

  // ---- vocabulaire

  @Test
  void adminListCarriesTheUsedCountInASingleQueryPair() {
    String specialty = freshCode();
    String audience = freshCode();
    vocabulary.create(new CreateRequest("specialty", specialty, "Spé test", null, null));
    vocabulary.create(new CreateRequest("audience", audience, "Public test", "Test", null));

    for (int i = 0; i < 2; i++) {
      UUID practitioner = fx.newPractitioner();
      fx.loginAs(practitioner);
      var request = DirectoryTestFixtures.completeProfile(null);
      profiles.update(
          new UpdateProfileRequest(
              request.displayName(),
              request.titleLabel(),
              request.showProfessionalNumber(),
              request.bio(),
              List.of(specialty),
              i == 0 ? List.of(audience) : List.of("adults"),
              request.modalities(),
              request.languages(),
              request.city(),
              request.postalCode(),
              request.country(),
              request.contactPhone(),
              request.contactEmail(),
              request.websiteUrl(),
              request.address(),
              request.showPhone(),
              request.showEmail(),
              request.showAddress(),
              request.responseDelay(),
              request.fees(),
              request.availability(),
              request.nextSlotAt()));
      profiles.publish();
    }
    // Une troisième fiche utilise la spécialité mais n'est pas publiée : elle ne compte pas.
    UUID draft = fx.newPractitioner();
    fx.loginAs(draft);
    profiles.update(
        new UpdateProfileRequest(
            "Brouillon",
            null,
            null,
            null,
            List.of(specialty),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null));

    Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    ListDto all = vocabulary.list(null);
    long statements = statistics.getPrepareStatementCount();

    assertThat(all.items().size()).as("seed + deux entrées").isGreaterThanOrEqualTo(29);
    assertThat(statements).as("une requête pour les entrées, une pour les comptes").isEqualTo(2);
    assertThat(used(all, specialty)).isEqualTo(2);
    assertThat(used(all, audience)).isEqualTo(1);
    assertThat(used(all, "adults")).isGreaterThanOrEqualTo(1);
    assertThat(used(vocabulary.list("specialty"), specialty)).isEqualTo(2);
    assertThat(vocabulary.list("audience").items())
        .extracting(ItemDto::kind)
        .containsOnly("audience");
  }

  private static int used(ListDto list, String code) {
    return list.items().stream()
        .filter(i -> i.code().equals(code))
        .findFirst()
        .orElseThrow()
        .usedByPublishedProfiles();
  }

  @Test
  void vocabularyCreateSequentialDuplicateIs409AndAutomaticRankFollowsTheLast() {
    String code = freshCode();
    ItemDto created = vocabulary.create(new CreateRequest("specialty", code, "Un", null, null));
    assertThat(created.sortOrder()).isGreaterThan(22);
    ItemDto next =
        vocabulary.create(new CreateRequest("specialty", freshCode(), "Deux", null, null));
    assertThat(next.sortOrder()).isEqualTo(created.sortOrder() + 1);

    assertThatThrownBy(
            () -> vocabulary.create(new CreateRequest("specialty", code, "Bis", null, null)))
        .isInstanceOfSatisfying(
            DirectoryVocabularyException.class,
            e -> {
              assertThat(e.code()).isEqualTo("VOCABULARY_EXISTS");
              assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
            });
  }

  /**
   * La course est provoquée : une transaction ouverte a déjà inséré (sans commit) le même couple
   * (kind, code), donc l'existsById de create ne le voit pas et l'INSERT n'échoue qu'au flush du
   * commit, une fois l'autre transaction validée. Le 409 est attrapé après ce commit.
   */
  @Test
  void duplicateDetectedAtCommitStillAnswersVocabularyExists() throws Exception {
    String code = freshCode();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<ItemDto> loser =
          tx.execute(
              status -> {
                jdbc.update(
                    "insert into directory_vocabulary (kind, code, label_fr, sort_order)"
                        + " values ('specialty', ?, 'Concurrent', 99)",
                    code);
                Future<ItemDto> call =
                    pool.submit(
                        () ->
                            vocabulary.create(
                                new CreateRequest("specialty", code, "Perdant", null, null)));
                await()
                    .atMost(Duration.ofSeconds(30))
                    .until(
                        () ->
                            jdbc.queryForObject(
                                    "select count(*) from pg_stat_activity"
                                        + " where wait_event_type = 'Lock'"
                                        + " and query like '%directory_vocabulary%'",
                                    Integer.class)
                                > 0);
                return call;
              });
      assertThatThrownBy(() -> loser.get())
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOfSatisfying(
              DirectoryVocabularyException.class,
              e -> {
                assertThat(e.code()).isEqualTo("VOCABULARY_EXISTS");
                assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
              });
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "select label_fr from directory_vocabulary where kind = 'specialty' and code = ?",
                String.class,
                code))
        .isEqualTo("Concurrent");
  }

  // ---- contact

  @Test
  void purgeDeletesPastDueRowsPhysicallyAndKeepsTheOthers() {
    UUID old = insertContact("-40 days", "-10 days");
    UUID recent = insertContact("0 seconds", "30 days");

    int purged = contacts.purgeDue();

    assertThat(purged).isGreaterThanOrEqualTo(1);
    assertThat(contactCount(old)).as("DELETE physique, pas d'archivage").isZero();
    assertThat(contactCount(recent)).isEqualTo(1);
  }

  private UUID insertContact(String createdOffset, String purgeOffset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into contact_request (id, practitioner_id, sender_name, sender_email, message,"
            + " consent_at, ip_hash, created_at, purge_after) values (?, ?, 'N', 'n@example.test',"
            + " 'm', now(), 'h', now() + ?::interval, now() + ?::interval)",
        id,
        alice,
        createdOffset,
        purgeOffset);
    return id;
  }

  private int contactCount(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from contact_request where id = ?", Integer.class, id);
  }
}
