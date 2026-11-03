package com.psymate.identity.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.admin.AbstractAccountsIT;
import com.psymate.testsupport.TotpGenerator;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * M1-14b : recours du compte suspendu ou refusé (fiche 27, Q28), de bout en bout : dépôt public par
 * identifiants, file des administrateurs, décision par un autre administrateur.
 */
class AppealIT extends AbstractAccountsIT {

  private static final String APPEAL = "/api/me/appeal";
  private static final String ADMIN_APPEALS = "/api/admin/appeals";
  private static final String REASON =
      "Plusieurs signalements concordants sur l'exercice du praticien.";
  private static final String MESSAGE =
      "Je conteste cette décision : les éléments invoqués sont inexacts.";
  private static final String ANSWER = "Après examen des pièces, nous levons la suspension.";

  // ---- fixtures

  private MvcResult submit(String email, String password, String otp, String message, String ip)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    if (email != null) {
      body.put("email", email);
    }
    if (password != null) {
      body.put("password", password);
    }
    if (otp != null) {
      body.put("otp", otp);
    }
    if (message != null) {
      body.put("message", message);
    }
    return mvc.perform(
            post(APPEAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    }))
        .andReturn();
  }

  private MvcResult submit(UUID practitioner) throws Exception {
    return submit(emailOf(practitioner), PASSWORD, null, MESSAGE, freshIp());
  }

  private MvcResult suspend(UUID admin, UUID target) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/admin/practitioners/" + target + "/suspend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"" + REASON + "\"}")
                    .with(asAdmin(admin)))
            .andReturn();
    assertThat(status(r)).isEqualTo(200);
    return r;
  }

  private MvcResult decide(UUID admin, String appealId, String outcome, String message)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    if (outcome != null) {
      body.put("outcome", outcome);
    }
    if (message != null) {
      body.put("messageToPractitioner", message);
    }
    return mvc.perform(
            post(ADMIN_APPEALS + "/" + appealId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .with(asAdmin(admin)))
        .andReturn();
  }

  private MvcResult listAppeals(UUID admin, String query) throws Exception {
    return mvc.perform(get(URI.create(ADMIN_APPEALS + query)).with(asAdmin(admin))).andReturn();
  }

  private JsonNode find(MvcResult list, String appealId) throws Exception {
    assertThat(status(list)).isEqualTo(200);
    for (JsonNode n : body(list).path("items")) {
      if (appealId.equals(n.path("id").asString())) {
        return n;
      }
    }
    return null;
  }

  /** Praticien suspendu par A, qui a déposé son recours. */
  private record Filed(UUID practitioner, UUID suspender, String appealId) {}

  private Filed filed() throws Exception {
    UUID suspender = admin();
    UUID practitioner = practitioner("Dr Recours", "approved");
    suspend(suspender, practitioner);
    MvcResult r = submit(practitioner);
    assertThat(status(r)).isEqualTo(201);
    return new Filed(practitioner, suspender, body(r).path("id").asString());
  }

  // ---- dépôt

  @Test
  void aSuspendedPractitionerFilesOneAppealWithoutASession() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Suspendu", "approved");
    UUID decisionId = UUID.fromString(body(suspend(admin, p)).path("decisionId").asString());

    MvcResult r = submit(p);
    assertThat(status(r)).isEqualTo(201);
    JsonNode body = body(r);
    assertThat(body.path("status").asString()).isEqualTo("open");
    assertThat(body.path("submittedAt").asString()).isNotBlank();
    UUID appealId = UUID.fromString(body.path("id").asString());
    assertNoForbiddenKey(body, "$");
    assertThat(r.getResponse().getCookie("SESSION")).isNull();
    assertThat(count("select count(*) from spring_session where principal_name = ?", p.toString()))
        .isZero();

    Map<String, Object> row =
        jdbc.queryForMap(
            "select practitioner_id, decision_id, message, reviewed_at, outcome"
                + " from practitioner_appeal where id = ?",
            appealId);
    assertThat(row.get("practitioner_id")).isEqualTo(p);
    assertThat(row.get("decision_id")).isEqualTo(decisionId);
    assertThat(row.get("message")).isEqualTo(MESSAGE);
    assertThat(row.get("reviewed_at")).isNull();
    assertThat(row.get("outcome")).isNull();

    // Journal : acteur NULL, le praticien lui-même.
    Map<String, Object> event =
        jdbc.queryForMap(
            "select actor_id, details->>'summary' as summary, details->>'appealId' as appeal,"
                + " details->>'decisionId' as decision from security_event"
                + " where practitioner_id = ? and kind = 'appeal.submitted'",
            p);
    assertThat(event.get("actor_id")).isNull();
    assertThat(event.get("summary")).isEqualTo("Recours déposé");
    assertThat(event.get("appeal")).isEqualTo(appealId.toString());
    assertThat(event.get("decision")).isEqualTo(decisionId.toString());

    // Un seul recours par décision.
    MvcResult second = submit(p);
    assertThat(status(second)).isEqualTo(409);
    assertThat(code(second)).isEqualTo("APPEAL_ALREADY_OPEN");
    assertThat(count("select count(*) from practitioner_appeal where practitioner_id = ?", p))
        .isEqualTo(1);
    // Le compte reste suspendu et le login reste refusé.
    assertThat(statusOf(p)).isEqualTo("suspended");
    assertThat(status(login(emailOf(p), PASSWORD))).isEqualTo(403);
  }

  @Test
  void aRejectedAccountCanAppealTheRejectionDecision() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Refusé", "rejected");
    UUID decisionId = UUID.randomUUID();
    jdbc.update(
        "insert into verification_decision (id, practitioner_id, admin_id, kind,"
            + " message_to_practitioner) values (?, ?, ?, 'rejected', 'Diplôme illisible.')",
        decisionId,
        p,
        admin);

    MvcResult r = submit(p);
    assertThat(status(r)).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select decision_id from practitioner_appeal where id = ?",
                UUID.class,
                UUID.fromString(body(r).path("id").asString())))
        .isEqualTo(decisionId);
    JsonNode item = find(listAppeals(admin, "?limit=200"), body(r).path("id").asString());
    assertThat(item).isNotNull();
    assertThat(item.path("decisionKind").asString()).isEqualTo("rejected");
    assertThat(item.path("decisionAdminId").asString()).isEqualTo(admin.toString());
  }

  @Test
  void theAppealTargetsTheLatestRefusalOrSuspension() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Deux Décisions", "approved");
    suspend(admin, p);
    mvc.perform(
            post("/api/admin/practitioners/" + p + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + REASON + "\"}")
                .with(asAdmin(admin)))
        .andReturn();
    UUID second = UUID.fromString(body(suspend(admin, p)).path("decisionId").asString());

    MvcResult r = submit(p);
    assertThat(status(r)).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select decision_id from practitioner_appeal where practitioner_id = ?",
                UUID.class,
                p))
        .isEqualTo(second);
  }

  @Test
  void invalidRequestsAreRefusedBeforeAnyChange() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Invalide", "approved");
    suspend(admin, p);

    MvcResult shortMessage = submit(emailOf(p), PASSWORD, null, "trop court", freshIp());
    assertThat(status(shortMessage)).isEqualTo(422);
    assertThat(code(shortMessage)).isEqualTo("VALIDATION_FAILED");
    JsonNode error = body(shortMessage).path("errors").get(0);
    assertThat(error.path("field").asString()).isEqualTo("message");
    assertThat(error.path("code").asString()).isEqualTo("TOO_SHORT");
    assertThat(
            body(submit(emailOf(p), PASSWORD, null, "x".repeat(4001), freshIp()))
                .path("errors")
                .get(0)
                .path("code")
                .asString())
        .isEqualTo("TOO_LONG");

    MvcResult empty = submit(null, null, null, null, freshIp());
    assertThat(status(empty)).isEqualTo(422);
    List<String> fields = new java.util.ArrayList<>();
    for (JsonNode e : body(empty).path("errors")) {
      fields.add(e.path("field").asString());
    }
    assertThat(fields).containsExactlyInAnyOrder("email", "password", "message");

    assertThat(count("select count(*) from practitioner_appeal where practitioner_id = ?", p))
        .isZero();
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Mot De Passe", "approved");
    suspend(admin, p);
    MvcResult wrong = submit(emailOf(p), "Mauvais-Mot-De-Passe-1", null, MESSAGE, freshIp());
    MvcResult unknown = submit("inconnu@example.test", PASSWORD, null, MESSAGE, freshIp());
    assertThat(status(wrong)).isEqualTo(401);
    assertThat(status(unknown)).isEqualTo(401);
    assertThat(code(wrong)).isEqualTo("INVALID_CREDENTIALS");
    assertThat(body(wrong).path("detail").asString())
        .isEqualTo(body(unknown).path("detail").asString());
    assertThat(count("select count(*) from practitioner_appeal where practitioner_id = ?", p))
        .isZero();
  }

  @Test
  void repeatedWrongPasswordsLockTheKeyWithRetryAfter() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Verrouillé", "approved");
    suspend(admin, p);
    String ip = freshIp();
    MvcResult last = null;
    for (int i = 0; i < 6; i++) {
      last = submit(emailOf(p), "Mauvais-Mot-De-Passe-1", null, MESSAGE, ip);
      if (status(last) == 423) {
        break;
      }
      assertThat(status(last)).isEqualTo(401);
    }
    assertThat(status(last)).isEqualTo(423);
    assertThat(code(last)).isEqualTo("ACCOUNT_LOCKED");
    assertThat(last.getResponse().getHeader("Retry-After")).isNotBlank();
    // Même le bon mot de passe est refusé tant que la clé est verrouillée.
    assertThat(status(submit(emailOf(p), PASSWORD, null, MESSAGE, ip))).isEqualTo(423);
  }

  @Test
  void onlyRejectedOrSuspendedAccountsCanAppeal() throws Exception {
    UUID approved = practitioner("Dr Approuvé", "approved");
    MvcResult r = submit(approved);
    assertThat(status(r)).isEqualTo(409);
    assertThat(code(r)).isEqualTo("ACCOUNT_NOT_SUSPENDED");
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");

    UUID closed = practitioner("Dr Fermé", "closed");
    assertThat(code(submit(closed))).isEqualTo("ACCOUNT_NOT_SUSPENDED");
    UUID docs = practitioner("Dr Docs", "docs_pending");
    assertThat(code(submit(docs))).isEqualTo("ACCOUNT_NOT_SUSPENDED");

    UUID pending = practitioner("Dr Mail", "email_pending");
    MvcResult unconfirmed = submit(pending);
    assertThat(status(unconfirmed)).isEqualTo(403);
    assertThat(code(unconfirmed)).isEqualTo("EMAIL_NOT_CONFIRMED");

    // Suspendu sans aucune décision en base : rien à contester.
    UUID noDecision = practitioner("Dr Sans Décision", "suspended");
    MvcResult none = submit(noDecision);
    assertThat(status(none)).isEqualTo(409);
    assertThat(code(none)).isEqualTo("APPEAL_NOT_ALLOWED");

    assertThat(
            count(
                "select count(*) from practitioner_appeal where practitioner_id in (?, ?, ?, ?, ?)",
                approved,
                closed,
                docs,
                pending,
                noDecision))
        .isZero();
  }

  @Test
  void twoFactorIsRequiredWhenActive() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Deux Facteurs", "approved");
    Cookie session = sessionOf(p);
    String secret =
        body(mvc.perform(post("/api/me/2fa/enable").cookie(session)).andReturn())
            .path("secret")
            .asString();
    Instant activation = Instant.now();
    MvcResult verified =
        mvc.perform(
                post("/api/me/2fa/verify")
                    .cookie(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + TotpGenerator.code(secret, activation) + "\"}"))
            .andReturn();
    assertThat(status(verified)).isIn(200, 204);
    suspend(admin, p);

    MvcResult noCode = submit(emailOf(p), PASSWORD, null, MESSAGE, freshIp());
    assertThat(status(noCode)).isEqualTo(202);
    assertThat(body(noCode).path("otpRequired").asBoolean()).isTrue();
    assertThat(count("select count(*) from practitioner_appeal where practitioner_id = ?", p))
        .isZero();

    assertThat(status(submit(emailOf(p), PASSWORD, "000000", MESSAGE, freshIp()))).isEqualTo(401);

    // Le pas de l'activation est déjà consommé : le code du pas suivant est accepté. Il est
    // calculé depuis l'instant de l'activation, pas depuis maintenant, pour rester dans la fenêtre
    // de tolérance même si la frontière des 30 secondes a été franchie entre-temps.
    String next = TotpGenerator.code(secret, activation.plus(Duration.ofSeconds(30)));
    MvcResult ok = submit(emailOf(p), PASSWORD, next, MESSAGE, freshIp());
    assertThat(status(ok)).isEqualTo(201);
    assertThat(count("select count(*) from practitioner_appeal where practitioner_id = ?", p))
        .isEqualTo(1);
  }

  // ---- file des administrateurs

  @Test
  void adminAppealsAreGuardedAndListedOldestFirstWhileOpen() throws Exception {
    Filed first = filed();
    Filed second = filed();
    jdbc.update(
        "update practitioner_appeal set submitted_at = now() - interval '2 days' where id = ?",
        UUID.fromString(first.appealId()));
    UUID reader = admin();

    MvcResult practitioner =
        mvc.perform(
                get(ADMIN_APPEALS)
                    .with(
                        as(practitioner("Dr Intrus", "approved"), "ROLE_PRACTITIONER", "approved")))
            .andReturn();
    assertThat(status(practitioner)).isEqualTo(403);
    assertThat(code(practitioner)).isEqualTo("ADMIN_ROLE_REQUIRED");
    MvcResult noTotp = listAppeals(adminWithoutTwoFactor(), "");
    assertThat(code(noTotp)).isEqualTo("TWO_FACTOR_REQUIRED");
    assertThat(status(mvc.perform(get(ADMIN_APPEALS)).andReturn())).isEqualTo(401);

    MvcResult open = listAppeals(reader, "?limit=200");
    assertThat(status(open)).isEqualTo(200);
    JsonNode item = find(open, first.appealId());
    assertThat(item).isNotNull();
    assertThat(item.path("practitionerId").asString()).isEqualTo(first.practitioner().toString());
    assertThat(item.path("practitionerDisplayName").asString()).isEqualTo("Dr Recours");
    assertThat(item.path("decisionKind").asString()).isEqualTo("suspended");
    assertThat(item.path("decisionId").asString()).isNotBlank();
    assertThat(item.path("decisionAdminId").asString()).isEqualTo(first.suspender().toString());
    assertThat(item.path("message").asString()).isEqualTo(MESSAGE);
    assertThat(item.path("submittedAt").asString()).isNotBlank();
    assertThat(item.path("reviewedAt").isNull()).isTrue();
    assertThat(item.path("reviewedBy").isNull()).isTrue();
    assertThat(item.path("outcome").isNull()).isTrue();
    assertThat(item.path("responseMessage").isNull()).isTrue();
    assertNoForbiddenKey(body(open), "$");

    List<String> ids = new java.util.ArrayList<>();
    for (JsonNode n : body(open).path("items")) {
      ids.add(n.path("id").asString());
      assertThat(n.path("reviewedAt").isNull()).isTrue();
    }
    assertThat(ids).contains(first.appealId(), second.appealId());
    assertThat(ids.indexOf(first.appealId())).isLessThan(ids.indexOf(second.appealId()));
    assertThat(find(listAppeals(reader, "?status=decided&limit=200"), first.appealId())).isNull();
    assertThat(find(listAppeals(reader, "?status=all&limit=200"), second.appealId())).isNotNull();

    MvcResult page = listAppeals(reader, "?limit=1");
    assertThat(body(page).path("items")).hasSize(1);
    assertThat(body(page).path("nextCursor").isNull()).isFalse();
    String cursor = body(page).path("nextCursor").asString();
    MvcResult next = listAppeals(reader, "?limit=1&cursor=" + cursor);
    assertThat(body(next).path("items")).hasSize(1);
    assertThat(body(next).path("items").get(0).path("id").asString())
        .isNotEqualTo(body(page).path("items").get(0).path("id").asString());

    MvcResult badStatus = listAppeals(reader, "?status=pending");
    assertThat(status(badStatus)).isEqualTo(422);
    assertThat(body(badStatus).path("errors").get(0).path("field").asString()).isEqualTo("status");
    assertThat(status(listAppeals(reader, "?cursor=%25%25"))).isEqualTo(422);
  }

  @Test
  void theAuthorOfTheDecisionCannotReviewTheAppeal() throws Exception {
    Filed f = filed();
    MvcResult self = decide(f.suspender(), f.appealId(), "accepted", ANSWER);
    assertThat(status(self)).isEqualTo(409);
    assertThat(code(self)).isEqualTo("APPEAL_SELF_REVIEW");
    assertThat(statusOf(f.practitioner())).isEqualTo("suspended");
    assertThat(
            count(
                "select count(*) from practitioner_appeal where id = ? and reviewed_at is null",
                UUID.fromString(f.appealId())))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from verification_decision where practitioner_id = ?"
                    + " and kind like 'appeal_%'",
                f.practitioner()))
        .isZero();
  }

  @Test
  void decideValidatesItsInput() throws Exception {
    Filed f = filed();
    UUID other = admin();
    MvcResult badOutcome = decide(other, f.appealId(), "maybe", ANSWER);
    assertThat(status(badOutcome)).isEqualTo(422);
    assertThat(body(badOutcome).path("errors").get(0).path("field").asString())
        .isEqualTo("outcome");
    assertThat(status(decide(other, f.appealId(), null, ANSWER))).isEqualTo(422);

    MvcResult noMessage = decide(other, f.appealId(), "accepted", "  ");
    assertThat(status(noMessage)).isEqualTo(422);
    assertThat(body(noMessage).path("errors").get(0).path("field").asString())
        .isEqualTo("messageToPractitioner");
    assertThat(status(decide(other, f.appealId(), "accepted", "x".repeat(4001)))).isEqualTo(422);

    MvcResult unknown = decide(other, UUID.randomUUID().toString(), "accepted", ANSWER);
    assertThat(status(unknown)).isEqualTo(404);
    assertThat(code(unknown)).isEqualTo("APPEAL_NOT_FOUND");
    assertThat(statusOf(f.practitioner())).isEqualTo("suspended");
  }

  @Test
  void anotherAdminAcceptingTheAppealReactivatesTheAccount() throws Exception {
    Filed f = filed();
    UUID reviewer = admin();

    MvcResult r = decide(reviewer, f.appealId(), "accepted", "  " + ANSWER + "  ");
    assertThat(status(r)).isEqualTo(200);
    JsonNode item = body(r);
    assertThat(item.path("id").asString()).isEqualTo(f.appealId());
    assertThat(item.path("outcome").asString()).isEqualTo("accepted");
    assertThat(item.path("reviewedBy").asString()).isEqualTo(reviewer.toString());
    assertThat(item.path("reviewedAt").asString()).isNotBlank();
    assertThat(item.path("responseMessage").asString()).isEqualTo(ANSWER);
    assertNoForbiddenKey(item, "$");

    // Statut restauré (approved avant la suspension), suspension levée.
    assertThat(statusOf(f.practitioner())).isEqualTo("approved");
    assertThat(
            count(
                "select count(*) from practitioner where id = ? and suspended_at is null",
                f.practitioner()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select outcome from practitioner_appeal where id = ?",
                String.class,
                UUID.fromString(f.appealId())))
        .isEqualTo("accepted");

    // Une seule décision d'administration : appeal_accepted rattachée au recours, pas de
    // reactivated en plus.
    Map<String, Object> decision =
        jdbc.queryForMap(
            "select admin_id, message_to_practitioner, related_id from verification_decision"
                + " where practitioner_id = ? and kind = 'appeal_accepted'",
            f.practitioner());
    assertThat(decision.get("admin_id")).isEqualTo(reviewer);
    assertThat(decision.get("message_to_practitioner")).isEqualTo(ANSWER);
    assertThat(decision.get("related_id")).isEqualTo(UUID.fromString(f.appealId()));
    assertThat(
            count(
                "select count(*) from verification_decision where practitioner_id = ?"
                    + " and kind = 'reactivated'",
                f.practitioner()))
        .isZero();
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ?"
                    + " and kind in ('admin.appeal', 'admin.reactivate') and actor_id = ?",
                f.practitioner(),
                reviewer))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select details->>'source' from security_event where practitioner_id = ?"
                    + " and kind = 'admin.reactivate'",
                String.class,
                f.practitioner()))
        .isEqualTo("appeal");

    String text = mailText(emailOf(f.practitioner()), "PsyMate : votre recours est accepté");
    assertThat(text).isNotNull().contains(ANSWER);
    assertThat(mails.count(emailOf(f.practitioner()), "PsyMate : votre compte est réactivé"))
        .isZero();
    assertThat(status(login(emailOf(f.practitioner()), PASSWORD))).isEqualTo(200);

    // Examiné : il quitte la file ouverte et ne se décide pas deux fois.
    UUID reader = admin();
    assertThat(find(listAppeals(reader, "?limit=200"), f.appealId())).isNull();
    assertThat(find(listAppeals(reader, "?status=decided&limit=200"), f.appealId())).isNotNull();
    MvcResult again = decide(reviewer, f.appealId(), "dismissed", ANSWER);
    assertThat(status(again)).isEqualTo(409);
    assertThat(code(again)).isEqualTo("APPEAL_ALREADY_DECIDED");
  }

  @Test
  void acceptingAnAppealOfARejectedAccountSendsItBackToDocsPending() throws Exception {
    UUID author = admin();
    UUID reviewer = admin();
    UUID p = practitioner("Dr Refusé Réexaminé", "rejected");
    jdbc.update(
        "update practitioner set suspended_at = now(), purge_after = now() + interval '30 days'"
            + " where id = ?",
        p);
    jdbc.update(
        "insert into verification_decision (id, practitioner_id, admin_id, kind,"
            + " message_to_practitioner) values (?, ?, ?, 'rejected', 'Diplôme illisible.')",
        UUID.randomUUID(),
        p,
        author);
    String appealId = body(submit(p)).path("id").asString();

    assertThat(status(decide(reviewer, appealId, "accepted", ANSWER))).isEqualTo(200);
    assertThat(statusOf(p)).isEqualTo("docs_pending");
    assertThat(
            count(
                "select count(*) from practitioner where id = ? and suspended_at is null"
                    + " and purge_after is null",
                p))
        .isEqualTo(1);
  }

  @Test
  void dismissingTheAppealKeepsTheAccountSuspendedAndClosesTheDoor() throws Exception {
    Filed f = filed();
    UUID reviewer = admin();

    MvcResult r = decide(reviewer, f.appealId(), "dismissed", "La décision est maintenue.");
    assertThat(status(r)).isEqualTo(200);
    assertThat(body(r).path("outcome").asString()).isEqualTo("dismissed");
    assertThat(body(r).path("responseMessage").asString()).isEqualTo("La décision est maintenue.");
    assertThat(body(r).path("reviewedBy").asString()).isEqualTo(reviewer.toString());

    // En base, le recours écarté s'écrit 'rejected' (contrainte de V03).
    assertThat(
            jdbc.queryForObject(
                "select outcome from practitioner_appeal where id = ?",
                String.class,
                UUID.fromString(f.appealId())))
        .isEqualTo("rejected");
    assertThat(statusOf(f.practitioner())).isEqualTo("suspended");
    assertThat(
            count(
                "select count(*) from verification_decision where practitioner_id = ?"
                    + " and kind = 'appeal_rejected' and related_id = ?",
                f.practitioner(),
                UUID.fromString(f.appealId())))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ?"
                    + " and kind = 'admin.reactivate'",
                f.practitioner()))
        .isZero();

    String text = mailText(emailOf(f.practitioner()), "PsyMate : votre recours");
    assertThat(text).isNotNull().contains("La décision est maintenue.");
    MvcResult relogin = login(emailOf(f.practitioner()), PASSWORD);
    assertThat(status(relogin)).isEqualTo(403);
    assertThat(code(relogin)).isEqualTo("ACCOUNT_SUSPENDED");

    // Un seul recours par décision, même après examen.
    MvcResult second = submit(f.practitioner());
    assertThat(status(second)).isEqualTo(409);
    assertThat(code(second)).isEqualTo("APPEAL_ALREADY_USED");
    assertThat(
            count(
                "select count(*) from practitioner_appeal where practitioner_id = ?",
                f.practitioner()))
        .isEqualTo(1);

    // L'administration garde la main : la réactivation manuelle reste possible.
    MvcResult manual =
        mvc.perform(
                post("/api/admin/practitioners/" + f.practitioner() + "/reactivate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"" + ANSWER + "\"}")
                    .with(asAdmin(reviewer)))
            .andReturn();
    assertThat(status(manual)).isEqualTo(200);
  }

  @Test
  void anAppealCannotBeAcceptedOnceTheAccountIsActiveAgain() throws Exception {
    Filed f = filed();
    UUID reviewer = admin();
    mvc.perform(
            post("/api/admin/practitioners/" + f.practitioner() + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + ANSWER + "\"}")
                .with(asAdmin(reviewer)))
        .andReturn();
    assertThat(statusOf(f.practitioner())).isEqualTo("approved");

    MvcResult accepted = decide(reviewer, f.appealId(), "accepted", ANSWER);
    assertThat(status(accepted)).isEqualTo(409);
    assertThat(code(accepted)).isEqualTo("APPEAL_ACCOUNT_NOT_REACTIVABLE");
    // Le recours devenu sans objet peut être clos en le rejetant.
    assertThat(status(decide(reviewer, f.appealId(), "dismissed", ANSWER))).isEqualTo(200);
    assertThat(statusOf(f.practitioner())).isEqualTo("approved");
  }

  @Test
  void appealAuditTrailAppearsInTheJournal() throws Exception {
    Filed f = filed();
    UUID reviewer = admin();
    decide(reviewer, f.appealId(), "dismissed", ANSWER);
    MvcResult r =
        mvc.perform(
                get(URI.create(
                        "/api/admin/journal?practitionerId=" + f.practitioner() + "&limit=200"))
                    .with(asAdmin(reviewer)))
            .andReturn();
    List<String> kinds = new java.util.ArrayList<>();
    for (JsonNode n : body(r).path("items")) {
      kinds.add(n.path("kind").asString());
      if ("appeal.submitted".equals(n.path("kind").asString())) {
        assertThat(n.path("actorType").asString()).isEqualTo("practitioner");
        assertThat(n.path("actor").isNull()).isTrue();
      }
    }
    assertThat(kinds)
        .contains(
            "appeal.submitted",
            "admin.appeal",
            "admin.appeal_rejected",
            "admin.suspend",
            "admin.suspended");
  }
}
