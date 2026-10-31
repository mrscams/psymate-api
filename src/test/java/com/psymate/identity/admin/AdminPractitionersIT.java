package com.psymate.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

/**
 * M1-14b : liste des comptes, détail, suspension et réactivation manuelles (fiche 21), de bout en
 * bout (Postgres, Mailpit, session réelle).
 */
class AdminPractitionersIT extends AbstractAccountsIT {

  private static final String BASE = "/api/admin/practitioners";
  private static final String REASON =
      "Plusieurs signalements concordants sur l'exercice du praticien.";

  // ---- fixtures

  private MvcResult list(UUID admin, String query) throws Exception {
    return mvc.perform(get(java.net.URI.create(BASE + query)).with(asAdmin(admin))).andReturn();
  }

  private MvcResult suspend(UUID admin, UUID target, Object reason) throws Exception {
    return action(admin, target, "suspend", reason);
  }

  private MvcResult reactivate(UUID admin, UUID target, Object reason) throws Exception {
    return action(admin, target, "reactivate", reason);
  }

  private MvcResult action(UUID admin, UUID target, String name, Object reason) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (reason != null) {
      payload.put("reason", reason);
    }
    return mvc.perform(
            post(BASE + "/" + target + "/" + name)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))
                .with(asAdmin(admin)))
        .andReturn();
  }

  private static String marker() {
    return "Zed" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private void submitted(UUID practitionerId, Instant at) {
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, occurred_at) values (?, ?,"
            + " 'verification_submitted', ?)",
        UUID.randomUUID(),
        practitionerId,
        Timestamp.from(at));
  }

  private List<UUID> ids(JsonNode items) {
    List<UUID> ids = new ArrayList<>();
    for (JsonNode n : items) {
      ids.add(UUID.fromString(n.path("id").asString()));
    }
    return ids;
  }

  // ---- 1. rôle et 2FA

  @Test
  void practitionersAndAdminsWithoutTwoFactorAreRefusedOnEveryRoute() throws Exception {
    UUID target = practitioner("Dr Cible", "approved");
    Map<String, RequestPostProcessor> callers =
        Map.of(
            "ADMIN_ROLE_REQUIRED",
                as(practitioner("Dr Intrus", "approved"), "ROLE_PRACTITIONER", "approved"),
            "TWO_FACTOR_REQUIRED", asAdmin(adminWithoutTwoFactor()));
    for (var caller : callers.entrySet()) {
      List<MvcResult> results =
          List.of(
              mvc.perform(get(BASE).with(caller.getValue())).andReturn(),
              mvc.perform(get(BASE + "/" + target).with(caller.getValue())).andReturn(),
              mvc.perform(
                      post(BASE + "/" + target + "/suspend")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"reason\":\"" + REASON + "\"}")
                          .with(caller.getValue()))
                  .andReturn(),
              mvc.perform(
                      post(BASE + "/" + target + "/reactivate")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"reason\":\"" + REASON + "\"}")
                          .with(caller.getValue()))
                  .andReturn());
      for (MvcResult r : results) {
        assertThat(status(r)).as(caller.getKey()).isEqualTo(403);
        assertThat(code(r)).isEqualTo(caller.getKey());
      }
    }
    assertThat(statusOf(target)).isEqualTo("approved");
    assertThat(
            count("select count(*) from verification_decision where practitioner_id = ?", target))
        .isZero();
    assertThat(status(mvc.perform(get(BASE)).andReturn())).isEqualTo(401);
  }

  // ---- 2. liste

  @Test
  void listFiltersSearchesAndCountsAccounts() throws Exception {
    String m = marker();
    UUID admin = admin();
    UUID approved1 = practitioner("Dr " + m + " Alpha", "approved");
    UUID approved2 = practitioner("Dr " + m + " Beta", "approved");
    UUID review = practitioner("Dr " + m + " Gamma", "under_review");
    UUID suspended = practitioner("Dr " + m + " Delta", "suspended");
    UUID rejected = practitioner("Dr " + m + " Epsilon", "rejected");
    UUID otherAdmin = account("Admin " + m, "approved", "admin", true, "fr");
    jdbc.update("update practitioner set profession = 'coach' where id = ?", approved2);
    submitted(review, Instant.now().minus(Duration.ofDays(6)));
    jdbc.update(
        "update practitioner set suspended_at = now() - interval '2 days' where id = ?", suspended);
    jdbc.update(
        "update practitioner set suspended_at = now(), purge_after = now() + interval '30 days'"
            + " where id = ?",
        rejected);
    jdbc.update("update practitioner set last_login_at = now() where id = ?", approved1);

    MvcResult all = list(admin, "?q=" + m);
    assertThat(status(all)).isEqualTo(200);
    JsonNode body = body(all);
    assertThat(ids(body.path("items")))
        .containsExactlyInAnyOrder(approved1, approved2, review, suspended, rejected, otherAdmin);
    assertThat(body.path("nextCursor").isNull()).isTrue();
    JsonNode counts = body.path("counts");
    assertThat(counts.path("approved").asInt()).isEqualTo(3);
    assertThat(counts.path("under_review").asInt()).isEqualTo(1);
    assertThat(counts.path("needs_more").asInt()).isZero();
    assertThat(counts.path("suspended").asInt()).isEqualTo(1);
    assertThat(counts.path("rejected").asInt()).isEqualTo(1);

    Map<UUID, JsonNode> byId = new LinkedHashMap<>();
    for (JsonNode n : body.path("items")) {
      byId.put(UUID.fromString(n.path("id").asString()), n);
    }
    JsonNode a = byId.get(approved1);
    assertThat(a.path("displayName").asString()).isEqualTo("Dr " + m + " Alpha");
    assertThat(a.path("email").asString()).isEqualTo(emailOf(approved1));
    assertThat(a.path("profession").asString()).isEqualTo("psychologist");
    assertThat(a.path("city").asString()).isEqualTo("Lyon");
    assertThat(a.path("role").asString()).isEqualTo("practitioner");
    assertThat(a.path("verification").path("status").asString()).isEqualTo("approved");
    assertThat(a.path("verification").path("since").asString()).isNotBlank();
    assertThat(a.path("verification").path("overdue").asBoolean()).isFalse();
    assertThat(a.path("twoFactorEnabled").asBoolean()).isFalse();
    assertThat(a.path("lastLoginAt").asString()).isNotBlank();
    assertThat(a.path("createdAt").asString()).isNotBlank();
    assertThat(a.has("directory")).isFalse();
    assertThat(byId.get(otherAdmin).path("twoFactorEnabled").asBoolean()).isTrue();
    assertThat(byId.get(otherAdmin).path("role").asString()).isEqualTo("admin");
    assertThat(byId.get(review).path("verification").path("overdue").asBoolean()).isTrue();
    assertThat(byId.get(review).path("verification").path("since").asString()).isNotBlank();
    assertThat(byId.get(suspended).path("suspendedAt").asString()).isNotBlank();
    assertThat(byId.get(rejected).path("purgeAt").asString()).isNotBlank();
    assertNoForbiddenKey(body, "$");

    // status, role, profession, q (casse et e-mail)
    assertThat(ids(body(list(admin, "?q=" + m + "&status=approved")).path("items")))
        .containsExactlyInAnyOrder(approved1, approved2, otherAdmin);
    JsonNode onlyAdmins = body(list(admin, "?q=" + m + "&role=admin"));
    assertThat(ids(onlyAdmins.path("items"))).containsExactly(otherAdmin);
    assertThat(onlyAdmins.path("counts").path("approved").asInt()).isEqualTo(1);
    assertThat(onlyAdmins.path("counts").path("suspended").asInt()).isZero();
    assertThat(ids(body(list(admin, "?q=" + m + "&profession=coach")).path("items")))
        .containsExactly(approved2);
    assertThat(ids(body(list(admin, "?q=" + m.toLowerCase() + "%20GAMMA")).path("items")))
        .containsExactly(review);
    assertThat(ids(body(list(admin, "?q=" + review)).path("items"))).containsExactly(review);
    // Les jokers du ILIKE sont échappés.
    assertThat(ids(body(list(admin, "?q=%25" + m + "%25")).path("items"))).isEmpty();

    // Le filtre de statut ne change pas les compteurs des onglets.
    JsonNode suspendedTab = body(list(admin, "?q=" + m + "&status=suspended"));
    assertThat(ids(suspendedTab.path("items"))).containsExactly(suspended);
    assertThat(suspendedTab.path("counts").path("approved").asInt()).isEqualTo(3);
  }

  @Test
  void listPaginatesByCursorNewestFirstWithoutDuplicates() throws Exception {
    String m = marker();
    UUID admin = admin();
    List<UUID> created = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      created.add(practitioner("Dr " + m + " " + i, "approved"));
    }
    List<UUID> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      MvcResult r =
          list(admin, "?q=" + m + "&limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
      assertThat(status(r)).isEqualTo(200);
      JsonNode page = body(r);
      assertThat(page.path("items").size()).isLessThanOrEqualTo(2);
      seen.addAll(ids(page.path("items")));
      cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asString();
      pages++;
    } while (cursor != null && pages < 10);
    assertThat(pages).isEqualTo(3);
    assertThat(seen)
        .hasSize(5)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(created);
    // Plus récent d'abord : l'ordre est celui de created_at décroissant, puis de l'id décroissant.
    List<UUID> expected =
        jdbc.queryForList(
            "select id from practitioner where display_name like ? order by"
                + " date_trunc('milliseconds', created_at) desc, id desc",
            UUID.class,
            "%" + m + "%");
    assertThat(seen).containsExactlyElementsOf(expected);

    MvcResult badCursor = list(admin, "?cursor=%25%25");
    assertThat(status(badCursor)).isEqualTo(422);
    assertThat(body(badCursor).path("errors").get(0).path("field").asString()).isEqualTo("cursor");
  }

  @Test
  void listRejectsUnknownFilters() throws Exception {
    UUID admin = admin();
    for (String bad : List.of("status=pending", "role=owner", "profession=astrologer")) {
      MvcResult r = list(admin, "?" + bad);
      assertThat(status(r)).as(bad).isEqualTo(422);
      assertThat(code(r)).isEqualTo("VALIDATION_FAILED");
      assertThat(body(r).path("errors").get(0).path("field").asString())
          .isEqualTo(bad.substring(0, bad.indexOf('=')));
    }
    assertThat(r422Content(list(admin, "?status=pending"))).contains("application/problem+json");
  }

  private String r422Content(MvcResult r) {
    return r.getResponse().getContentType();
  }

  // ---- 3. détail

  @Test
  void detailReturnsAccountDecisionsAndAppeals() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Detail", "approved");
    assertThat(status(suspend(admin, p, REASON))).isEqualTo(200);

    MvcResult r = mvc.perform(get(BASE + "/" + p).with(asAdmin(admin))).andReturn();
    assertThat(status(r)).isEqualTo(200);
    JsonNode body = body(r);
    assertThat(body.path("id").asString()).isEqualTo(p.toString());
    assertThat(body.path("verification").path("status").asString()).isEqualTo("suspended");
    assertThat(body.path("suspendedAt").asString()).isNotBlank();
    assertThat(body.path("decisions")).hasSize(1);
    JsonNode decision = body.path("decisions").get(0);
    assertThat(decision.path("kind").asString()).isEqualTo("suspended");
    assertThat(decision.path("messageToPractitioner").asString()).isEqualTo(REASON);
    assertThat(decision.path("adminId").asString()).isEqualTo(admin.toString());
    assertThat(decision.path("checklist").path("previousStatus").asString()).isEqualTo("approved");
    assertThat(body.path("appeals")).isEmpty();
    assertNoForbiddenKey(body, "$");

    UUID decisionId = UUID.fromString(decision.path("id").asString());
    UUID appealId = UUID.randomUUID();
    jdbc.update(
        "insert into practitioner_appeal (id, practitioner_id, decision_id, message) values (?, ?,"
            + " ?, 'Je conteste cette décision.')",
        appealId,
        p,
        decisionId);
    JsonNode withAppeal = body(mvc.perform(get(BASE + "/" + p).with(asAdmin(admin))).andReturn());
    assertThat(withAppeal.path("appeals")).hasSize(1);
    JsonNode appeal = withAppeal.path("appeals").get(0);
    assertThat(appeal.path("id").asString()).isEqualTo(appealId.toString());
    assertThat(appeal.path("outcome").isNull()).isTrue();
    assertThat(appeal.path("reviewedAt").isNull()).isTrue();
    assertThat(appeal.path("message").asString()).isEqualTo("Je conteste cette décision.");
    assertNoForbiddenKey(withAppeal, "$");

    MvcResult unknown =
        mvc.perform(get(BASE + "/" + UUID.randomUUID()).with(asAdmin(admin))).andReturn();
    assertThat(status(unknown)).isEqualTo(404);
    assertThat(code(unknown)).isEqualTo("ADMIN_PRACTITIONER_NOT_FOUND");
  }

  // ---- 4. suspension

  @Test
  void suspendRequiresAReasonOfAtLeastTwentyCharacters() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Motif", "approved");
    Map<Object, String> cases = new LinkedHashMap<>();
    cases.put("   ", "REQUIRED");
    cases.put("trop court", "TOO_SHORT");
    cases.put("x".repeat(19) + "   ", "TOO_SHORT");
    cases.put("y".repeat(4001), "TOO_LONG");
    for (var c : cases.entrySet()) {
      MvcResult r = suspend(admin, p, c.getKey());
      assertThat(status(r)).isEqualTo(422);
      assertThat(code(r)).isEqualTo("VALIDATION_FAILED");
      JsonNode error = body(r).path("errors").get(0);
      assertThat(error.path("field").asString()).isEqualTo("reason");
      assertThat(error.path("code").asString()).isEqualTo(c.getValue());
      assertThat(error.path("message").asString()).isNotBlank();
    }
    MvcResult missing = suspend(admin, p, null);
    assertThat(status(missing)).isEqualTo(422);
    assertThat(body(missing).path("errors").get(0).path("code").asString()).isEqualTo("REQUIRED");
    assertThat(statusOf(p)).isEqualTo("approved");
    assertThat(count("select count(*) from verification_decision where practitioner_id = ?", p))
        .isZero();
  }

  @Test
  void suspendGuardsAgainstSelfNonSuspendableAndUnknownTargets() throws Exception {
    UUID admin = admin();
    MvcResult self = suspend(admin, admin, REASON);
    assertThat(status(self)).isEqualTo(409);
    assertThat(code(self)).isEqualTo("ADMIN_SELF_SUSPEND");
    assertThat(statusOf(admin)).isEqualTo("approved");

    for (String s : List.of("suspended", "rejected", "closed")) {
      UUID p = practitioner("Dr " + s, s);
      MvcResult r = suspend(admin, p, REASON);
      assertThat(status(r)).as(s).isEqualTo(409);
      assertThat(code(r)).isEqualTo("ADMIN_ACCOUNT_NOT_SUSPENDABLE");
      assertThat(statusOf(p)).isEqualTo(s);
    }
    MvcResult unknown = suspend(admin, UUID.randomUUID(), REASON);
    assertThat(status(unknown)).isEqualTo(404);
    assertThat(code(unknown)).isEqualTo("ADMIN_PRACTITIONER_NOT_FOUND");
  }

  @Test
  void theLastActiveAdminCannotBeSuspendedButAnotherAdminCan() throws Exception {
    UUID actor = admin();
    UUID target = account("Dernier Admin", "approved", "admin", true, "fr");
    UUID spare = account("Admin de réserve", "approved", "admin", true, "fr");
    // Les autres administrateurs actifs de la base (laissés par d'autres tests) sont écartés le
    // temps du test, puis rétablis.
    Map<UUID, String> others = new LinkedHashMap<>();
    jdbc.query(
        "select id, verification_status from practitioner where role = 'admin'"
            + " and verification_status not in ('suspended','rejected','closed') and id <> ?",
        rs -> {
          others.put(rs.getObject(1, UUID.class), rs.getString(2));
        },
        target);
    try {
      jdbc.update(
          "update practitioner set verification_status = 'closed' where role = 'admin' and id <> ?",
          target);
      // L'acteur n'est plus un administrateur actif en base (session périmée) : la cible est la
      // dernière.
      MvcResult last = suspend(actor, target, REASON);
      assertThat(status(last)).isEqualTo(409);
      assertThat(code(last)).isEqualTo("ADMIN_LAST_ADMIN");
      assertThat(statusOf(target)).isEqualTo("approved");
      assertThat(
              count("select count(*) from verification_decision where practitioner_id = ?", target))
          .isZero();

      // Un autre administrateur actif existe : la suspension passe.
      jdbc.update("update practitioner set verification_status = 'approved' where id = ?", spare);
      MvcResult ok = suspend(actor, target, REASON);
      assertThat(status(ok)).isEqualTo(200);
      assertThat(statusOf(target)).isEqualTo("suspended");
    } finally {
      others.forEach(
          (id, st) ->
              jdbc.update("update practitioner set verification_status = ? where id = ?", st, id));
    }
  }

  @Test
  void suspendRevokesSessionsMailsThePractitionerAndWritesTheJournal() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Suspendu", "approved");
    Cookie session = sessionOf(p);
    assertThat(status(me(session))).isEqualTo(200);
    assertThat(count("select count(*) from spring_session where principal_name = ?", p.toString()))
        .isPositive();

    MvcResult r = suspend(admin, p, "  " + REASON + "  ");
    assertThat(status(r)).isEqualTo(200);
    JsonNode body = body(r);
    assertThat(body.path("id").asString()).isEqualTo(p.toString());
    assertThat(body.path("verificationStatus").asString()).isEqualTo("suspended");
    assertThat(body.path("suspendedAt").asString()).isNotBlank();
    UUID decisionId = UUID.fromString(body.path("decisionId").asString());
    assertNoForbiddenKey(body, "$");

    assertThat(statusOf(p)).isEqualTo("suspended");
    assertThat(
            count("select count(*) from practitioner where id = ? and suspended_at is not null", p))
        .isEqualTo(1);
    Map<String, Object> decision =
        jdbc.queryForMap(
            "select kind, admin_id, message_to_practitioner, checklist->>'previousStatus' as prev,"
                + " practitioner_id from verification_decision where id = ?",
            decisionId);
    assertThat(decision.get("kind")).isEqualTo("suspended");
    assertThat(decision.get("admin_id")).isEqualTo(admin);
    assertThat(decision.get("practitioner_id")).isEqualTo(p);
    assertThat(decision.get("message_to_practitioner")).isEqualTo(REASON);
    assertThat(decision.get("prev")).isEqualTo("approved");

    Map<String, Object> event =
        jdbc.queryForMap(
            "select actor_id, practitioner_id, details->>'reason' as reason,"
                + " details->>'summary' as summary, details->>'sessionsRevoked' as revoked,"
                + " details->>'previousStatus' as prev from security_event"
                + " where practitioner_id = ? and kind = 'admin.suspend'",
            p);
    assertThat(event.get("actor_id")).isEqualTo(admin);
    assertThat(event.get("reason")).isEqualTo(REASON);
    assertThat((String) event.get("summary"))
        .startsWith("Compte suspendu : ")
        .contains("signalements");
    assertThat(Integer.parseInt((String) event.get("revoked"))).isPositive();
    assertThat(event.get("prev")).isEqualTo("approved");

    assertThat(count("select count(*) from spring_session where principal_name = ?", p.toString()))
        .isZero();
    assertThat(status(me(session))).isEqualTo(401);
    assertThat(await(() -> recorder.suspended.contains(p))).isTrue();

    String text = mailText(emailOf(p), "PsyMate : votre compte est suspendu");
    assertThat(text).isNotNull().contains(REASON).contains("contester").contains("Dr Suspendu");

    MvcResult relogin = login(emailOf(p), PASSWORD);
    assertThat(status(relogin)).isEqualTo(403);
    assertThat(code(relogin)).isEqualTo("ACCOUNT_SUSPENDED");
  }

  @Test
  void suspendMailsEnglishSpeakingPractitionersInEnglish() throws Exception {
    UUID admin = admin();
    UUID p = account("Dr English", "approved", "practitioner", false, "en");
    assertThat(status(suspend(admin, p, REASON))).isEqualTo(200);
    String text = mailText(emailOf(p), "PsyMate: your account is suspended");
    assertThat(text).isNotNull().contains(REASON).contains("appeal");
  }

  @Test
  void aSuspendedAdminLosesTheirAdminSessionAndCannotComeBack() throws Exception {
    UUID actor = admin();
    UUID other = account("Admin Suspendu", "approved", "admin", true, "fr");
    assertThat(status(suspend(actor, other, REASON))).isEqualTo(200);
    assertThat(statusOf(other)).isEqualTo("suspended");
    MvcResult relogin = login(emailOf(other), PASSWORD);
    assertThat(status(relogin)).isEqualTo(403);
    assertThat(code(relogin)).isEqualTo("ACCOUNT_SUSPENDED");
  }

  // ---- 5. réactivation

  @Test
  void reactivateRestoresApprovedAfterASuspensionAndAllowsLoginAgain() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Revenu", "approved");
    suspend(admin, p, REASON);
    assertThat(status(login(emailOf(p), PASSWORD))).isEqualTo(403);

    String why = "Les éléments reçus ont été vérifiés, le compte peut reprendre.";
    MvcResult r = reactivate(admin, p, why);
    assertThat(status(r)).isEqualTo(200);
    assertThat(body(r).path("verificationStatus").asString()).isEqualTo("approved");
    assertNoForbiddenKey(body(r), "$");
    assertThat(statusOf(p)).isEqualTo("approved");
    assertThat(
            count(
                "select count(*) from practitioner where id = ? and suspended_at is null and purge_after is null",
                p))
        .isEqualTo(1);

    Map<String, Object> decision =
        jdbc.queryForMap(
            "select kind, admin_id, message_to_practitioner from verification_decision"
                + " where practitioner_id = ? and kind = 'reactivated'",
            p);
    assertThat(decision.get("admin_id")).isEqualTo(admin);
    assertThat(decision.get("message_to_practitioner")).isEqualTo(why);
    Map<String, Object> event =
        jdbc.queryForMap(
            "select actor_id, details->>'newStatus' as new_status, details->>'reason' as reason,"
                + " details->>'summary' as summary from security_event"
                + " where practitioner_id = ? and kind = 'admin.reactivate'",
            p);
    assertThat(event.get("actor_id")).isEqualTo(admin);
    assertThat(event.get("new_status")).isEqualTo("approved");
    assertThat(event.get("reason")).isEqualTo(why);
    assertThat((String) event.get("summary")).startsWith("Compte réactivé");

    String text = mailText(emailOf(p), "PsyMate : votre compte est réactivé");
    assertThat(text).isNotNull().contains("de nouveau actif");
    assertThat(status(login(emailOf(p), PASSWORD))).isEqualTo(200);
  }

  @Test
  void reactivateRestoresThePreviousStatusAndFallsBackToDocsPending() throws Exception {
    UUID admin = admin();
    // under_review avant la suspension : retour en under_review.
    UUID review = practitioner("Dr En Revue", "under_review");
    suspend(admin, review, REASON);
    assertThat(body(reactivate(admin, review, REASON)).path("verificationStatus").asString())
        .isEqualTo("under_review");

    // email_pending avant la suspension : docs_pending.
    UUID pending = practitioner("Dr Sans Mail", "email_pending");
    suspend(admin, pending, REASON);
    assertThat(body(reactivate(admin, pending, REASON)).path("verificationStatus").asString())
        .isEqualTo("docs_pending");

    // Suspension sans décision (insérée à la main) : approved si le dossier a été vérifié...
    UUID verified = practitioner("Dr Vérifié", "suspended");
    jdbc.update("update practitioner set verified_at = now() where id = ?", verified);
    assertThat(body(reactivate(admin, verified, REASON)).path("verificationStatus").asString())
        .isEqualTo("approved");
    // ... docs_pending sinon.
    UUID never = practitioner("Dr Jamais Vérifié", "suspended");
    assertThat(body(reactivate(admin, never, REASON)).path("verificationStatus").asString())
        .isEqualTo("docs_pending");
  }

  @Test
  void reactivateSendsARejectedAccountBackToDocsPendingAndClearsThePurge() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Refusé", "rejected");
    jdbc.update(
        "update practitioner set suspended_at = now(), purge_after = now() + interval '30 days'"
            + " where id = ?",
        p);
    MvcResult r = reactivate(admin, p, REASON);
    assertThat(status(r)).isEqualTo(200);
    assertThat(body(r).path("verificationStatus").asString()).isEqualTo("docs_pending");
    assertThat(statusOf(p)).isEqualTo("docs_pending");
    assertThat(
            count(
                "select count(*) from practitioner where id = ? and suspended_at is null and purge_after is null",
                p))
        .isEqualTo(1);
    assertThat(status(login(emailOf(p), PASSWORD))).isEqualTo(200);
  }

  @Test
  void reactivateValidatesTheReasonAndTheCurrentStatus() throws Exception {
    UUID admin = admin();
    UUID suspended = practitioner("Dr Suspendu 2", "suspended");
    MvcResult shortReason = reactivate(admin, suspended, "court");
    assertThat(status(shortReason)).isEqualTo(422);
    assertThat(body(shortReason).path("errors").get(0).path("code").asString())
        .isEqualTo("TOO_SHORT");
    assertThat(status(reactivate(admin, suspended, null))).isEqualTo(422);
    assertThat(statusOf(suspended)).isEqualTo("suspended");

    for (String s : List.of("approved", "under_review", "closed", "docs_pending")) {
      UUID p = practitioner("Dr " + s, s);
      MvcResult r = reactivate(admin, p, REASON);
      assertThat(status(r)).as(s).isEqualTo(409);
      assertThat(code(r)).isEqualTo("ADMIN_ACCOUNT_NOT_REACTIVABLE");
      assertThat(statusOf(p)).isEqualTo(s);
    }
    MvcResult unknown = reactivate(admin, UUID.randomUUID(), REASON);
    assertThat(status(unknown)).isEqualTo(404);
    assertThat(code(unknown)).isEqualTo("ADMIN_PRACTITIONER_NOT_FOUND");
  }

  @Test
  void aSuspensionAfterAReactivationCanBeReactivatedAgain() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Deux Fois", "approved");
    suspend(admin, p, REASON);
    reactivate(admin, p, REASON);
    assertThat(status(suspend(admin, p, REASON + " Seconde fois."))).isEqualTo(200);
    assertThat(body(reactivate(admin, p, REASON)).path("verificationStatus").asString())
        .isEqualTo("approved");
    assertThat(count("select count(*) from verification_decision where practitioner_id = ?", p))
        .isEqualTo(4);
  }
}
