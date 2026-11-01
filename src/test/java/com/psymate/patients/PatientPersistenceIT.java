package com.psymate.patients;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.patients.PatientDtos.CreatePatientRequest;
import com.psymate.patients.PatientDtos.FlagRequest;
import com.psymate.patients.PatientDtos.PatientDto;
import com.psymate.patients.PatientDtos.PatientPage;
import com.psymate.patients.PatientDtos.VersionDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Persistance JPA du module patients (M8-02) : clinical_rev jamais écrite par un UPDATE d'entité,
 * incrément atomique, chargement par lot des drapeaux et des tags, verrou de ligne du PATCH et 409
 * d'une course sur le drapeau actif.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PatientTestConfig.class)
class PatientPersistenceIT {

  @Autowired private PatientService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;

  private PatientServiceFixtures fx;
  private UUID alice;

  @BeforeEach
  void practitioner() {
    fx = new PatientServiceFixtures(jdbc);
    alice = fx.newPractitioner();
    fx.loginAs(alice);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static CreatePatientRequest named(String name) {
    return new CreatePatientRequest(
        name, null, null, null, null, null, null, null, null, null, null);
  }

  /**
   * Crée le patient et attend la séance d'accueil que clinical écrit en asynchrone (clinical_rev).
   */
  private UUID create(String name) {
    UUID id = service.create(named(name)).id();
    fx.awaitIntakeSession(id);
    return id;
  }

  private long clinicalRev(UUID id) {
    return jdbc.queryForObject("select clinical_rev from patient where id = ?", Long.class, id);
  }

  private int currentVersion(UUID id) {
    return jdbc.queryForObject(
        "select current_version from patient where id = ?", Integer.class, id);
  }

  @Test
  void patchBumpsTheVersionButNeverWritesClinicalRev() {
    UUID id = create("Version");
    long base = clinicalRev(id);
    assertThat(currentVersion(id)).isEqualTo(1);

    service.patch(id, Map.of("phone", "0102"));

    assertThat(currentVersion(id)).isEqualTo(2);
    assertThat(clinicalRev(id)).isEqualTo(base);
    assertThat(jdbc.queryForObject("select phone from patient where id = ?", String.class, id))
        .isEqualTo("0102");
  }

  @Test
  void touchClinicalRevAddsOneEachTimeAndWritesTheColumnDespiteUpdatableFalse() {
    UUID id = create("Rev");
    long base = clinicalRev(id);
    service.touchClinicalRev(id);
    assertThat(clinicalRev(id)).isEqualTo(base + 1);
    service.touchClinicalRev(id);
    assertThat(clinicalRev(id)).isEqualTo(base + 2);
  }

  @Test
  void aStaleEntityNeverOverwritesAnAtomicIncrement() {
    UUID id = create("Stale");
    long base = clinicalRev(id);
    tx.executeWithoutResult(
        status -> {
          // L'entité est chargée avec l'ancienne clinical_rev, puis l'incrément SQL passe, puis un
          // PATCH sale l'entité : son UPDATE ne doit pas ramener clinical_rev à l'ancienne valeur.
          service.displayName(id);
          service.touchClinicalRev(id);
          service.patch(id, Map.of("phone", "0102"));
          service.touchClinicalRev(id);
        });
    assertThat(clinicalRev(id)).isEqualTo(base + 2);
    assertThat(currentVersion(id)).isEqualTo(2);
  }

  @Test
  void archiveAndRestoreKeepClinicalRev() {
    UUID id = create("Arch");
    service.touchClinicalRev(id);
    long base = clinicalRev(id);
    service.archive(id);
    assertThat(
            jdbc.queryForObject(
                "select archived_at is not null from patient where id = ?", Boolean.class, id))
        .isTrue();
    service.restore(id);
    assertThat(
            jdbc.queryForObject(
                "select archived_at is null from patient where id = ?", Boolean.class, id))
        .isTrue();
    assertThat(clinicalRev(id)).isEqualTo(base);
    assertThat(currentVersion(id)).isEqualTo(1);
  }

  @Test
  void jsonbRoundTripKeepsNestedValues() {
    UUID id =
        service
            .create(
                new CreatePatientRequest(
                    "Json",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(
                        "history", Map.of("events", List.of(1, "deux")), "activity", "infirmière"),
                    Map.of("durationMin", 45),
                    null,
                    null))
            .id();
    PatientDto read = service.get(id);
    assertThat(read.context()).containsEntry("activity", "infirmière");
    assertThat(read.context().get("history")).isEqualTo(Map.of("events", List.of(1, "deux")));
    assertThat(read.followUp()).containsEntry("durationMin", 45);

    service.patch(id, Map.of("context", Map.of("activity", "enseignante")));
    PatientDto patched = service.get(id);
    assertThat(patched.context()).containsEntry("activity", "enseignante");
    assertThat(patched.context()).containsKey("history");

    List<VersionDto> versions = service.versions(id);
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).snapshot()).containsEntry("displayName", "Json");
  }

  @Test
  void listGivesTheRightFlagsAndTagsForSeveralPatients() {
    UUID anna = create("Anna");
    UUID elodie = create("Élodie");
    UUID zoe = create("Zoé");
    fx.newTag(alice, "sommeil");
    fx.newTag(alice, "famille");

    service.replaceTags(anna, List.of("sommeil", "famille"));
    service.replaceTags(elodie, List.of("sommeil"));
    service.addFlag(anna, new FlagRequest("risk", null));
    service.addFlag(anna, new FlagRequest("watch", "Sommeil"));
    service.addFlag(zoe, new FlagRequest("shared_care", null));
    service.clearFlag(zoe, "shared_care");

    PatientPage page = service.list(null, false, null, "name", null, null);

    assertThat(page.items())
        .extracting(PatientDto::displayName)
        .containsExactly("Anna", "Élodie", "Zoé");
    PatientDto a = page.items().get(0);
    assertThat(a.tags()).extracting(t -> t.label()).containsExactly("famille", "sommeil");
    assertThat(a.flags()).extracting(f -> f.kind()).containsExactlyInAnyOrder("risk", "watch");
    assertThat(a.flags()).extracting(f -> f.setAt()).isSorted();
    PatientDto e = page.items().get(1);
    assertThat(e.tags()).extracting(t -> t.label()).containsExactly("sommeil");
    assertThat(e.flags()).isEmpty();
    PatientDto z = page.items().get(2);
    assertThat(z.tags()).isEmpty();
    assertThat(z.flags()).as("drapeau levé non renvoyé").isEmpty();

    // Filtres natifs f_unaccent : nom sans accent, tag, pagination.
    assertThat(service.list("elodie", false, null, "name", null, null).items())
        .extracting(PatientDto::id)
        .containsExactly(elodie);
    assertThat(service.list(null, false, "sommeil", "name", null, null).items())
        .extracting(PatientDto::id)
        .containsExactly(anna, elodie);
    PatientPage first = service.list(null, false, null, "name", null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.nextCursor()).isNotNull();
    PatientPage second = service.list(null, false, null, "name", first.nextCursor(), 2);
    assertThat(second.items()).extracting(PatientDto::id).containsExactly(zoe);
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void replaceTagsKeepsTheCommonOnesAndDropsTheOthers() {
    UUID id = create("Tags");
    UUID sommeil = fx.newTag(alice, "sommeil");
    fx.newTag(alice, "famille");
    fx.newTag(alice, "travail");

    service.replaceTags(id, List.of("sommeil", "famille"));
    assertThat(tagIds(id)).hasSize(2).contains(sommeil);

    PatientDto result = service.replaceTags(id, List.of("sommeil", "travail"));
    assertThat(result.tags()).extracting(t -> t.label()).containsExactly("sommeil", "travail");
    assertThat(tagIds(id)).hasSize(2).contains(sommeil);

    assertThat(service.replaceTags(id, List.of()).tags()).isEmpty();
    assertThat(tagIds(id)).isEmpty();
  }

  private List<UUID> tagIds(UUID patient) {
    return jdbc.queryForList(
        "select tag_id from patient_tag where patient_id = ?", UUID.class, patient);
  }

  @Test
  void concurrentPatchesAreSerializedByTheRowLock() throws Exception {
    UUID id = create("Course");
    int workers = 4;
    List<Future<?>> results =
        runConcurrently(workers, i -> service.patch(id, Map.of("phone", "0" + i)));
    for (Future<?> f : results) {
      f.get();
    }
    assertThat(currentVersion(id)).isEqualTo(1 + workers);
    assertThat(
            jdbc.queryForList(
                "select version from patient_version where patient_id = ? order by version",
                Integer.class,
                id))
        .containsExactly(2, 3, 4, 5);
  }

  @Test
  void concurrentAddFlagGivesOneWinnerAndTheOthersGet409() throws Exception {
    UUID id = create("Drapeau");
    int workers = 4;
    List<Future<?>> results =
        runConcurrently(workers, i -> service.addFlag(id, new FlagRequest("risk", null)));
    int ok = 0;
    List<PatientException> conflicts = new ArrayList<>();
    for (Future<?> f : results) {
      try {
        f.get();
        ok++;
      } catch (java.util.concurrent.ExecutionException e) {
        assertThat(e.getCause()).isInstanceOf(PatientException.class);
        conflicts.add((PatientException) e.getCause());
      }
    }
    assertThat(ok).isEqualTo(1);
    assertThat(conflicts)
        .hasSize(workers - 1)
        .allSatisfy(
            c -> {
              assertThat(c.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(c.code()).isEqualTo("FLAG_ALREADY_ACTIVE");
            });
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient_flag where patient_id = ? and cleared_at is null",
                Integer.class,
                id))
        .isEqualTo(1);
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
      ready.await();
      go.countDown();
      pool.shutdown();
      pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS);
      return futures;
    } finally {
      pool.shutdownNow();
    }
  }
}
