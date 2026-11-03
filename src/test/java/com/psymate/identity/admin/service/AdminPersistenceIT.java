package com.psymate.identity.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.admin.AbstractAccountsIT;
import com.psymate.identity.admin.model.AdminException;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.SuspendedAccount;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Persistance JPA de identity.admin (M8-05, lot 3) : 409 d'unicité obtenu au flush, verrou des
 * administrateurs actifs, et absence de N+1 sur les listes d'administration.
 */
class AdminPersistenceIT extends AbstractAccountsIT {

  private static final String REASON =
      "Plusieurs signalements concordants sur l'exercice du praticien.";
  private static final String MESSAGE =
      "Je conteste cette décision : les éléments invoqués sont inexacts.";

  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory emf;
  @Autowired private AppealService appeals;
  @Autowired private AdminPractitionerService accounts;
  @Autowired private AdminVerificationService verifications;
  @Autowired private AdminJournalService journal;

  // ---- fixtures

  private UUID suspendOverHttp(UUID admin, UUID target) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/admin/practitioners/" + target + "/suspend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"" + REASON + "\"}")
                    .with(asAdmin(admin)))
            .andReturn();
    assertThat(status(r)).isEqualTo(200);
    return UUID.fromString(body(r).path("decisionId").asString());
  }

  /**
   * Attend que les abonnés asynchrones des événements qui concernent ces praticiens aient terminé
   * (plus de ligne event_publication sans completion_date) : la suspension publie
   * PractitionerSuspended, que directory.PractitionerStatusListener consomme hors requete en
   * écrivant par Hibernate dans la même SessionFactory. Sans cette attente, un compteur de
   * statistiques Hibernate lu pendant ce travail compte aussi ses requêtes.
   */
  private void awaitPublicationsSettled(List<UUID> practitioners) {
    for (UUID id : practitioners) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(100))
          .until(
              () ->
                  count(
                          "select count(*) from event_publication where completion_date is null"
                              + " and serialized_event like ?",
                          "%" + id + "%")
                      == 0);
    }
  }

  private CurrentPractitioner actor(UUID id) {
    return new CurrentPractitioner(
        id, tenant, id + "@example.test", "Admin Test", "psychologist", "approved");
  }

  private void awaitBackendsWaitingOnALock(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname = current_database()"
                  + " and wait_event_type = 'Lock'",
              Integer.class);
      if (waiting != null && waiting >= expected) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError(
        expected + " thread(s) du service ne se sont jamais bloqués sur un verrou");
  }

  // ---- 409 APPEAL_ALREADY_OPEN obtenu au flush

  /**
   * Une connexion annexe insère, sans valider, un recours sur la même décision (pour un autre
   * praticien : le verrou FOR UPDATE du service ne l'attend donc pas). Les pré-contrôles du service
   * ne le voient pas ; son INSERT part au flush du commit et attend l'index unique
   * ux_appeal_decision. La connexion annexe valide : l'index refuse la ligne du service, et c'est
   * le catch après commit qui produit le 409.
   */
  @Test
  void anAppealConflictInvisibleToThePreChecksIsCaughtAtCommitAsAppealAlreadyOpen()
      throws Exception {
    UUID admin = admin();
    UUID subject = practitioner("Dr Recours", "approved");
    UUID decisionId = suspendOverHttp(admin, subject);
    UUID other = practitioner("Dr Autre", "approved");
    UUID rival = UUID.randomUUID();
    SuspendedAccount who =
        new SuspendedAccount(subject, tenant, emailOf(subject), "Dr Recours", "fr", "suspended");

    try (Connection pending = dataSource.getConnection()) {
      pending.setAutoCommit(false);
      try (PreparedStatement ps =
          pending.prepareStatement(
              "insert into practitioner_appeal (id, practitioner_id, decision_id, message,"
                  + " submitted_at) values (?, ?, ?, 'Recours concurrent', now())")) {
        ps.setObject(1, rival);
        ps.setObject(2, other);
        ps.setObject(3, decisionId);
        ps.executeUpdate();
      }
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<?> call = pool.submit(() -> appeals.submit(who, MESSAGE));
        awaitBackendsWaitingOnALock(1);
        pending.commit();
        try {
          call.get(30, TimeUnit.SECONDS);
          throw new AssertionError("le dépôt aurait dû échouer en 409 au commit");
        } catch (ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(AdminException.class);
          AdminException conflict = (AdminException) e.getCause();
          assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(conflict.code()).isEqualTo("APPEAL_ALREADY_OPEN");
        }
      } finally {
        pool.shutdownNow();
      }
    }

    // La transaction du perdant est annulée en bloc : seule la ligne de la connexion annexe reste.
    assertThat(count("select count(*) from practitioner_appeal where decision_id = ?", decisionId))
        .isEqualTo(1);
    assertThat(count("select count(*) from practitioner_appeal where practitioner_id = ?", subject))
        .isZero();
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'appeal.submitted'",
                subject))
        .as("aucun événement orphelin")
        .isZero();
  }

  // ---- verrou des administrateurs actifs

  /**
   * Un autre administrateur verrouillé par une connexion annexe bloque la suspension d'un
   * administrateur cible : lockActiveAdmins verrouille tous les administrateurs actifs, pas
   * seulement la cible.
   */
  @Test
  void suspendingAnAdminWaitsForTheLockOnAnotherActiveAdmin() throws Exception {
    UUID acting = admin();
    UUID locked = admin();
    UUID target = admin();

    try (Connection side = dataSource.getConnection()) {
      side.setAutoCommit(false);
      try (PreparedStatement ps =
          side.prepareStatement("select id from practitioner where id = ? for update")) {
        ps.setObject(1, locked);
        ps.executeQuery();
      }
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<?> call = pool.submit(() -> accounts.suspend(actor(acting), target, REASON, null));
        awaitBackendsWaitingOnALock(1);
        assertThat(call.isDone()).as("la suspension attend le verrou").isFalse();
        assertThat(statusOf(target)).isEqualTo("approved");
        side.commit();
        call.get(30, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }
    }

    assertThat(statusOf(target)).isEqualTo("suspended");
    assertThat(statusOf(locked)).isEqualTo("approved");
  }

  /**
   * Deux administrateurs se suspendent mutuellement au même instant (tous deux attendent derrière
   * un verrou posé par une connexion annexe, puis partent ensemble). Les verrous pris dans l'ordre
   * des id sérialisent les deux appels : pas d'interblocage, l'un réussit et l'autre voit qu'il
   * resterait zéro administrateur actif (ADMIN_LAST_ADMIN).
   */
  @Test
  void crossedAdminSuspensionsAreSerializedWithoutDeadlock() throws Exception {
    UUID a = admin();
    UUID b = admin();
    // Seuls a et b sont actifs : le résultat est le même quel que soit l'ordre d'arrivée. Les
    // autres administrateurs actifs (laissés par d'autres tests) sont écartés le temps du test,
    // puis rétablis, comme dans AdminPractitionersIT.
    Map<UUID, String> others = new LinkedHashMap<>();
    jdbc.query(
        "select id, verification_status from practitioner where role = 'admin'"
            + " and verification_status not in ('suspended','rejected','closed')"
            + " and id not in (?, ?)",
        rs -> {
          others.put(rs.getObject(1, UUID.class), rs.getString(2));
        },
        a,
        b);
    others
        .keySet()
        .forEach(
            id ->
                jdbc.update(
                    "update practitioner set verification_status = 'closed' where id = ?", id));
    try {
      List<Object> outcomes = new ArrayList<>();
      try (Connection side = dataSource.getConnection()) {
        side.setAutoCommit(false);
        try (PreparedStatement ps =
            side.prepareStatement(
                "select id from practitioner where role = 'admin' and verification_status not in"
                    + " ('suspended','rejected','closed') order by id for update")) {
          ps.executeQuery();
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
          Future<Object> aSuspendsB =
              pool.submit(outcome(() -> accounts.suspend(actor(a), b, REASON, null)));
          Future<Object> bSuspendsA =
              pool.submit(outcome(() -> accounts.suspend(actor(b), a, REASON, null)));
          awaitBackendsWaitingOnALock(2);
          side.commit();
          outcomes.add(aSuspendsB.get(30, TimeUnit.SECONDS));
          outcomes.add(bSuspendsA.get(30, TimeUnit.SECONDS));
        } finally {
          pool.shutdownNow();
        }
      }

      long succeeded = outcomes.stream().filter(o -> !(o instanceof Throwable)).count();
      List<String> refusals =
          outcomes.stream()
              .filter(o -> o instanceof AdminException)
              .map(o -> ((AdminException) o).code())
              .toList();
      assertThat(outcomes)
          .as("aucune erreur de verrou ou d'interblocage")
          .allMatch(o -> !(o instanceof Throwable) || o instanceof AdminException);
      assertThat(succeeded).isEqualTo(1);
      assertThat(refusals).containsExactly("ADMIN_LAST_ADMIN");
      assertThat(
              count(
                  "select count(*) from practitioner where id in (?, ?)"
                      + " and verification_status = 'approved'",
                  a,
                  b))
          .as("un administrateur actif reste")
          .isEqualTo(1);
    } finally {
      others.forEach(
          (id, st) ->
              jdbc.update("update practitioner set verification_status = ? where id = ?", st, id));
    }
  }

  private static Callable<Object> outcome(Callable<?> call) {
    return () -> {
      try {
        return call.call();
      } catch (Throwable t) {
        return t;
      }
    };
  }

  // ---- pas de N+1 sur les listes d'administration

  @Test
  void adminListsRunOneQueryEachWhateverTheNumberOfRows() throws Exception {
    UUID admin = admin();
    String marker = "Nplus" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    List<UUID> queued = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      UUID p = practitioner(marker + " " + i, "under_review");
      queued.add(p);
      jdbc.update(
          "insert into security_event (id, practitioner_id, kind, occurred_at) values (?, ?,"
              + " 'verification_submitted', ?)",
          UUID.randomUUID(),
          p,
          Timestamp.from(Instant.now().minusSeconds(60L * i)));
      for (String type : List.of("diploma", "identity")) {
        jdbc.update(
            "insert into verification_document (id, practitioner_id, type, title, storage_key,"
                + " mime, size_bytes, sha256) values (?, ?, ?, 'Titre', ?, 'application/pdf', 10,"
                + " 'abc')",
            UUID.randomUUID(),
            p,
            type,
            "verification/" + p + "/" + type + ".bin");
      }
    }
    // Quatre recours (une suspension chacun) pour la liste des recours et le détail d'un compte.
    UUID withAppeals = null;
    List<UUID> suspended = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      UUID p = practitioner(marker + " appel " + i, "approved");
      suspended.add(p);
      UUID decision = suspendOverHttp(admin, p);
      jdbc.update(
          "insert into practitioner_appeal (id, practitioner_id, decision_id, message,"
              + " submitted_at) values (?, ?, ?, 'Message de recours assez long', now())",
          UUID.randomUUID(),
          p,
          decision);
      withAppeals = p;
    }

    // Les dépublications de fiches déclenchées par les suspensions écrivent encore en arrière-plan
    // par la même SessionFactory : attendre leur fin avant de compter les requêtes.
    awaitPublicationsSettled(suspended);

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    try {
      long before = stats.getPrepareStatementCount();
      var queue = verifications.list("under_review", "oldest", null, 50);
      assertThat(queue.items().size()).isGreaterThanOrEqualTo(12);
      assertThat(stats.getPrepareStatementCount() - before).as("file de vérification").isEqualTo(1);

      before = stats.getPrepareStatementCount();
      var list = accounts.list(marker, null, null, null, null, 50);
      assertThat(list.items()).hasSize(16);
      assertThat(stats.getPrepareStatementCount() - before)
          .as("liste des comptes : la page puis les compteurs")
          .isEqualTo(2);

      before = stats.getPrepareStatementCount();
      var page = appeals.list("open", null, 50);
      assertThat(page.items().size()).isGreaterThanOrEqualTo(4);
      assertThat(stats.getPrepareStatementCount() - before).as("recours").isEqualTo(1);

      before = stats.getPrepareStatementCount();
      var entries = journal.list(null, null, null, null, null, null, 50);
      assertThat(entries.items().size()).isGreaterThanOrEqualTo(4);
      assertThat(stats.getPrepareStatementCount() - before).as("journal").isEqualTo(1);

      before = stats.getPrepareStatementCount();
      var detail = accounts.get(withAppeals);
      assertThat(detail.decisions()).isNotEmpty();
      assertThat(detail.appeals()).isNotEmpty();
      assertThat(stats.getPrepareStatementCount() - before)
          .as("détail d'un compte : le compte, ses décisions, ses recours")
          .isEqualTo(3);
    } finally {
      stats.setStatisticsEnabled(false);
    }
  }
}
