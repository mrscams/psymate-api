package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.vocabulary.EmotionService.MergeView;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistance JPA du module vocabulary (M8-03) : les 409 d'unicité sont attrapés au commit (courses
 * à quatre threads), un seul undo concurrent passe, les lectures gardent leur tri et leurs filtres,
 * les colonnes {@code text[]} remontent en listes, et la date limite d'annulation vaut la date de
 * fusion plus 30 jours.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(VocabularyTestConfig.class)
class VocabularyPersistenceIT {

  @Autowired private TagService tags;
  @Autowired private EmotionService emotions;
  @Autowired private EmotionCatalog emotionCatalog;
  @Autowired private TagFacade tagFacade;
  @Autowired private LinkTypeCatalog linkTypes;
  @Autowired private TagRepository tagRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private TransactionTemplate tx;

  private VocabularyTestFixtures fx;
  private UUID alice;

  @BeforeEach
  void practitioner() {
    fx = new VocabularyTestFixtures(jdbc);
    alice = fx.newPractitioner();
    fx.loginAs(alice);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  // ---- 409 attrapés au commit

  @Test
  void fourConcurrentTagCreationsGiveOneWinnerAndThreeTagExists() throws Exception {
    int workers = 4;
    List<Future<?>> results = runConcurrently(workers, i -> tags.create("Même libellé"));

    assertOneWinnerAndConflicts(results, workers, "TAG_EXISTS");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from tag where practitioner_id = ? and archived_at is null",
                Integer.class,
                alice))
        .isEqualTo(1);
  }

  @Test
  void fourConcurrentEmotionCreationsGiveOneWinnerAndThreeEmotionLabelExists() throws Exception {
    int workers = 4;
    List<Future<?>> results = runConcurrently(workers, i -> emotions.create("Même émotion", "joy"));

    assertOneWinnerAndConflicts(results, workers, "EMOTION_LABEL_EXISTS");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from emotion where practitioner_id = ? and scope = 'custom'",
                Integer.class,
                alice))
        .isEqualTo(1);
  }

  @Test
  void fourConcurrentTagRenamesToTheSameLabelGiveOneWinnerAndThreeTagExists() throws Exception {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      ids.add(fx.newTag(alice, "Source " + i));
    }
    List<Future<?>> results = runConcurrently(4, i -> tags.rename(ids.get(i), "Cible commune"));

    assertOneWinnerAndConflicts(results, 4, "TAG_EXISTS");
  }

  // ---- 409 attrapés au commit : preuve déterministe (le pré-contrôle ne voit rien)

  private static final String PENDING_TAG =
      "insert into tag (id, tenant_id, practitioner_id, label, normalized_label)"
          + " values (?, ?, ?, 'Doublon', 'doublon')";
  private static final String PENDING_EMOTION =
      "insert into emotion (code, scope, tenant_id, practitioner_id, family, label_fr,"
          + " label_normalized) values (?, 'custom', ?, ?, 'joy', 'Doublon', 'doublon')";

  @Test
  void tagCreationConflictInvisibleToThePreCheckIsCaughtAtCommitAsTagExists() throws Exception {
    VocabularyException conflict =
        conflictAtFlush(PENDING_TAG, tagArgs(), () -> tags.create("Doublon"));

    assertThat(conflict.code()).isEqualTo("TAG_EXISTS");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from tag where practitioner_id = ? and normalized_label ="
                    + " 'doublon'",
                Integer.class,
                alice))
        .isEqualTo(1);
  }

  @Test
  void tagRenameConflictInvisibleToThePreCheckIsCaughtAtCommitAsTagExists() throws Exception {
    UUID mine = fx.newTag(alice, "Avant");

    VocabularyException conflict =
        conflictAtFlush(PENDING_TAG, tagArgs(), () -> tags.rename(mine, "Doublon"));

    assertThat(conflict.code()).isEqualTo("TAG_EXISTS");
    assertThat(jdbc.queryForObject("select label from tag where id = ?", String.class, mine))
        .isEqualTo("Avant");
  }

  @Test
  void emotionCreationConflictInvisibleToThePreCheckIsCaughtAtCommitAsLabelExists()
      throws Exception {
    VocabularyException conflict =
        conflictAtFlush(PENDING_EMOTION, emotionArgs(), () -> emotions.create("Doublon", "joy"));

    assertThat(conflict.code()).isEqualTo("EMOTION_LABEL_EXISTS");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from emotion where practitioner_id = ? and label_normalized ="
                    + " 'doublon'",
                Integer.class,
                alice))
        .isEqualTo(1);
  }

  @Test
  void emotionRenameConflictInvisibleToThePreCheckIsCaughtAtCommitAsLabelExists() throws Exception {
    String code = emotions.create("Avant", "joy").code();

    VocabularyException conflict =
        conflictAtFlush(PENDING_EMOTION, emotionArgs(), () -> emotions.rename(code, "Doublon"));

    assertThat(conflict.code()).isEqualTo("EMOTION_LABEL_EXISTS");
    assertThat(
            jdbc.queryForObject("select label_fr from emotion where code = ?", String.class, code))
        .isEqualTo("Avant");
  }

  private Object[] tagArgs() {
    return new Object[] {UUID.randomUUID(), fx.tenant, alice};
  }

  private Object[] emotionArgs() {
    return new Object[] {"c_" + UUID.randomUUID().toString().substring(0, 8), fx.tenant, alice};
  }

  /**
   * Une connexion annexe insère la ligne conflictuelle sans la valider : le pré-contrôle du service
   * ne la voit pas, mais son INSERT ou UPDATE attend l'index unique. Quand le thread du service est
   * bloqué sur ce verrou (pg_stat_activity), la connexion annexe valide : l'index refuse la ligne
   * du service au flush, et c'est bien le catch après commit qui produit le 409.
   */
  private VocabularyException conflictAtFlush(String pendingSql, Object[] args, Callable<?> call)
      throws Exception {
    try (Connection pending = dataSource.getConnection()) {
      pending.setAutoCommit(false);
      try (PreparedStatement ps = pending.prepareStatement(pendingSql)) {
        for (int i = 0; i < args.length; i++) {
          ps.setObject(i + 1, args[i]);
        }
        ps.executeUpdate();
      }
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<?> f =
            pool.submit(
                () -> {
                  fx.loginAs(alice);
                  try {
                    return call.call();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                });
        awaitOneBackendWaitingOnALock();
        pending.commit();
        try {
          f.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(VocabularyException.class);
          VocabularyException conflict = (VocabularyException) e.getCause();
          assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
          return conflict;
        }
        throw new AssertionError("le service aurait dû échouer en 409 au commit");
      } finally {
        pool.shutdownNow();
      }
    }
  }

  private void awaitOneBackendWaitingOnALock() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname = current_database()"
                  + " and wait_event_type = 'Lock'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("le thread du service ne s'est jamais bloqué sur l'index unique");
  }

  /**
   * Quatre fusions de la même source au même instant : la source est chargée sous verrou de ligne,
   * la première fusion la désactive, les trois suivantes relisent {@code active = false} et
   * reçoivent EMOTION_INACTIVE (une seule ligne emotion_merge).
   */
  @Test
  void fourConcurrentMergesOfTheSameSourceGiveOneWinnerAndThreeInactive() throws Exception {
    String code = emotions.create("Lassitude", "sadness").code();

    int workers = 4;
    List<Future<?>> results = runConcurrently(workers, i -> emotions.merge(code, "sadness"));

    assertOneWinnerAndConflicts(results, workers, "EMOTION_INACTIVE");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from emotion_merge where source_code = ?", Integer.class, code))
        .isEqualTo(1);
  }

  /**
   * Quatre undo au même instant : la fusion est chargée sous verrou, le premier l'annule, les trois
   * suivants relisent {@code undone_at} et reçoivent MERGE_ALREADY_UNDONE (jamais deux succès).
   * Avec l'ancien UPDATE conditionnel, les perdants recevaient MERGE_UNDO_EXPIRED : même statut
   * 409, message plus juste désormais.
   */
  @Test
  void fourConcurrentUndosGiveOneWinnerAndThreeAlreadyUndone() throws Exception {
    String code = emotions.create("Lassitude", "sadness").code();
    UUID mergeId = emotions.merge(code, "sadness").id();

    int workers = 4;
    List<Future<?>> results = runConcurrently(workers, i -> emotions.undo(mergeId));

    assertOneWinnerAndConflicts(results, workers, "MERGE_ALREADY_UNDONE");
    assertThat(
            jdbc.queryForObject("select active from emotion where code = ?", Boolean.class, code))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select undone_at is not null from emotion_merge where id = ?",
                Boolean.class,
                mergeId))
        .isTrue();
  }

  // ---- lectures

  @Test
  void activeEmotionsListGlobalsThenCustomsByFamilyAndIntensityWithNullsLast() {
    emotions.create("Bravo", "sadness");
    emotions.create("Alpha", "sadness");
    emotions.create("Charlie", "anger");

    List<EmotionView> all = emotionCatalog.activeEmotions();

    assertThat(all).hasSize(32);
    assertThat(all.subList(0, 29)).extracting(EmotionView::scope).containsOnly("global");
    assertThat(all.subList(29, 32)).extracting(EmotionView::scope).containsOnly("custom");
    assertThat(all.subList(29, 32))
        .extracting(EmotionView::labelFr)
        .containsExactly("Charlie", "Alpha", "Bravo");
    assertSortedByFamilyThenIntensityNullsLast(all.subList(0, 29));
    assertSortedByFamilyThenIntensityNullsLast(all.subList(29, 32));
  }

  private static void assertSortedByFamilyThenIntensityNullsLast(List<EmotionView> group) {
    for (int i = 1; i < group.size(); i++) {
      EmotionView previous = group.get(i - 1);
      EmotionView current = group.get(i);
      int byFamily = previous.family().compareTo(current.family());
      assertThat(byFamily).as(previous.code() + " avant " + current.code()).isLessThanOrEqualTo(0);
      if (byFamily == 0 && previous.intensityLevel() == null) {
        assertThat(current.intensityLevel())
            .as("intensité nulle en dernier : " + current.code())
            .isNull();
      }
      if (byFamily == 0 && previous.intensityLevel() != null && current.intensityLevel() != null) {
        assertThat(previous.intensityLevel()).isLessThanOrEqualTo(current.intensityLevel());
      }
    }
    assertThat(group).extracting(EmotionView::intensityLevel).contains((Integer) null);
  }

  @Test
  void labelsByIdsKeepsOnlyActiveTagsOfTheCurrentPractitioner() {
    UUID bob = fx.newPractitioner();
    UUID mine = fx.newTag(alice, "Actif");
    UUID archived = fx.newTag(alice, "Archive");
    UUID others = fx.newTag(bob, "Autre");
    jdbc.update("update tag set archived_at = now() where id = ?", archived);

    Map<UUID, String> labels = tagFacade.labelsByIds(List.of(mine, archived, others));

    assertThat(labels).containsOnlyKeys(mine).containsEntry(mine, "Actif");
    assertThat(tagFacade.labelsByIds(List.of())).isEmpty();
  }

  @Test
  void activeLinkTypesExposeTheTextArraysAsNonEmptyLists() {
    List<LinkTypeView> types = linkTypes.activeLinkTypes();

    assertThat(types).hasSize(8);
    assertThat(types).extracting(LinkTypeView::sortOrder).isSortedAccordingTo(Integer::compareTo);
    assertThat(types)
        .allSatisfy(
            t -> {
              assertThat(t.allowedSourceTypes()).isNotEmpty();
              assertThat(t.allowedTargetTypes()).isNotEmpty();
            });
    LinkTypeView first = types.get(0);
    String sql =
        jdbc.queryForObject(
            "select array_to_string(allowed_source_types, ',') from link_type where code = ?",
            String.class,
            first.code());
    assertThat(String.join(",", first.allowedSourceTypes())).isEqualTo(sql);
    assertThat(linkTypes.findByCode(first.code())).contains(first);
    assertThat(linkTypes.findByCode("inconnu")).isEmpty();
  }

  // ---- fusion d'émotion et de tags

  @Test
  void mergeSetsUndoUntilThirtyDaysAfterMergedAtWithMicrosecondPrecision() {
    String code = emotions.create("Lassitude", "sadness").code();

    MergeView merge = emotions.merge(code, "sadness");

    assertThat(merge.undoUntil()).isEqualTo(merge.mergedAt().plusDays(30));
    assertThat(merge.undoneAt()).isNull();
    Map<String, Object> row =
        jdbc.queryForMap(
            "select merged_at, undo_until, undo_until - merged_at = interval '30 days' as thirty"
                + " from emotion_merge where id = ?",
            merge.id());
    assertThat(row.get("thirty")).isEqualTo(true);
    assertThat(((java.sql.Timestamp) row.get("merged_at")).toInstant())
        .isEqualTo(merge.mergedAt().toInstant());
    OffsetDateTime undone = emotions.undo(merge.id()).undoneAt();
    assertThat(undone).isNotNull().isAfterOrEqualTo(merge.mergedAt());
  }

  /**
   * La cible est déjà chargée dans le contexte de persistance quand le compteur est additionné en
   * SQL : l'entité est rafraîchie, et le dirty checking de la source n'écrit jamais usage_count.
   */
  @Test
  void tagMergeKeepsTheSqlUsageCountWhenTheTargetIsAlreadyLoaded() {
    UUID source = fx.newTag(alice, "Boulot");
    UUID target = fx.newTag(alice, "Travail");
    jdbc.update("update tag set usage_count = 3 where id = ?", source);
    jdbc.update("update tag set usage_count = 4 where id = ?", target);

    TagView merged =
        tx.execute(
            status -> {
              Tag loaded = tagRepository.findById(target).orElseThrow();
              assertThat(loaded.getUsageCount()).isEqualTo(4);
              return tags.merge(source, target);
            });

    assertThat(merged.usageCount()).isEqualTo(7);
    assertThat(
            jdbc.queryForObject("select usage_count from tag where id = ?", Integer.class, target))
        .isEqualTo(7);
    assertThat(
            jdbc.queryForObject("select usage_count from tag where id = ?", Integer.class, source))
        .isEqualTo(3);
    assertThat(jdbc.queryForObject("select merged_into from tag where id = ?", UUID.class, source))
        .isEqualTo(target);
  }

  @Test
  void tagMergeRelabelsPatientTagsWithoutDuplicates() {
    UUID source = fx.newTag(alice, "Boulot");
    UUID target = fx.newTag(alice, "Travail");
    UUID p1 = fx.newPatient(alice);
    UUID p2 = fx.newPatient(alice);
    jdbc.update("insert into patient_tag (patient_id, tag_id) values (?, ?)", p1, source);
    jdbc.update("insert into patient_tag (patient_id, tag_id) values (?, ?)", p2, source);
    jdbc.update("insert into patient_tag (patient_id, tag_id) values (?, ?)", p2, target);

    tags.merge(source, target);

    assertThat(
            jdbc.queryForList(
                "select patient_id from patient_tag where tag_id = ?", UUID.class, target))
        .containsExactlyInAnyOrder(p1, p2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient_tag where tag_id = ?", Integer.class, source))
        .isZero();
    assertThat(tagFacade.tagsOfCurrentPractitioner())
        .extracting(TagView::id)
        .containsExactly(target);
  }

  // ---- aides

  private static void assertOneWinnerAndConflicts(
      List<Future<?>> results, int workers, String conflictCode) throws InterruptedException {
    int ok = 0;
    List<VocabularyException> conflicts = new ArrayList<>();
    for (Future<?> f : results) {
      try {
        f.get();
        ok++;
      } catch (ExecutionException e) {
        assertThat(e.getCause()).isInstanceOf(VocabularyException.class);
        conflicts.add((VocabularyException) e.getCause());
      }
    }
    assertThat(ok).isEqualTo(1);
    assertThat(conflicts)
        .hasSize(workers - 1)
        .allSatisfy(
            c -> {
              assertThat(c.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(c.code()).isEqualTo(conflictCode);
            });
  }

  private interface Work {
    void run(int index) throws Exception;
  }

  /** Lance N appels au même instant, chacun avec le praticien courant dans son propre thread. */
  private List<Future<?>> runConcurrently(int workers, Work work) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      CountDownLatch ready = new CountDownLatch(workers);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        int index = i;
        Callable<Void> task =
            () -> {
              fx.loginAs(alice);
              try {
                ready.countDown();
                go.await();
                work.run(index);
                return null;
              } finally {
                SecurityContextHolder.clearContext();
              }
            };
        futures.add(pool.submit(task));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).as("tous les threads prêts").isTrue();
      go.countDown();
      pool.shutdown();
      pool.awaitTermination(60, TimeUnit.SECONDS);
      return futures;
    } finally {
      pool.shutdownNow();
    }
  }
}
