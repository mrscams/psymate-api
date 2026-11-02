package com.psymate.identity.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.psymate.identity.admin.AbstractAccountsIT;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * M1-14b : journal d'administration en lecture seule (fiche 22), vue admin_journal_v : ordre,
 * filtres, curseur, absence d'adresse IP et de donnée patient, routes d'écriture refusées, tables
 * sources append-only pour psymate_app.
 */
class AdminJournalIT extends AbstractAccountsIT {

  private static final String BASE = "/api/admin/journal";
  private static final String REASON =
      "Plusieurs signalements concordants sur l'exercice du praticien.";

  /** IPv4 complète ou IPv6 complète (huit groupes ou forme compressée contenant ::). */
  private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

  private static final Pattern IPV6 =
      Pattern.compile("(?i)(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|[0-9a-f:]*::[0-9a-f:]*");

  @Autowired private DataSource dataSource;

  // ---- fixtures

  private MvcResult journal(UUID admin, String query) throws Exception {
    return mvc.perform(get(URI.create(BASE + query)).with(asAdmin(admin))).andReturn();
  }

  private JsonNode items(MvcResult r) throws Exception {
    assertThat(status(r)).isEqualTo(200);
    return body(r).path("items");
  }

  private List<String> kinds(JsonNode items) {
    List<String> kinds = new ArrayList<>();
    for (JsonNode n : items) {
      kinds.add(n.path("kind").asString());
    }
    return kinds;
  }

  private void decision(UUID practitioner, UUID admin, String kind, String message, Instant at) {
    jdbc.update(
        "insert into verification_decision (id, practitioner_id, admin_id, kind,"
            + " message_to_practitioner, decided_at) values (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        practitioner,
        admin,
        kind,
        message,
        Timestamp.from(at));
  }

  private void suspend(UUID admin, UUID target) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/admin/practitioners/" + target + "/suspend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"" + REASON + "\"}")
                    .with(asAdmin(admin)))
            .andReturn();
    assertThat(status(r)).isEqualTo(200);
  }

  /** Praticien approuvé par B il y a une heure, suspendu par A à l'instant. */
  private record Scenario(UUID practitioner, UUID suspender, UUID approver, Instant before) {}

  private Scenario scenario() throws Exception {
    UUID practitioner = practitioner("Dr Journal", "approved");
    UUID suspender = admin();
    UUID approver = account("Admin Approbateur", "approved", "admin", true, "fr");
    Instant before = Instant.now().minus(Duration.ofSeconds(5));
    decision(practitioner, approver, "approved", null, Instant.now().minus(Duration.ofHours(1)));
    suspend(suspender, practitioner);
    return new Scenario(practitioner, suspender, approver, before);
  }

  // ---- accès

  @Test
  void onlyAdminsWithTwoFactorCanReadTheJournal() throws Exception {
    UUID p = practitioner("Dr Intrus", "approved");
    MvcResult role =
        mvc.perform(get(BASE).with(as(p, "ROLE_PRACTITIONER", "approved"))).andReturn();
    assertThat(status(role)).isEqualTo(403);
    assertThat(code(role)).isEqualTo("ADMIN_ROLE_REQUIRED");
    MvcResult noTotp = journal(adminWithoutTwoFactor(), "");
    assertThat(status(noTotp)).isEqualTo(403);
    assertThat(code(noTotp)).isEqualTo("TWO_FACTOR_REQUIRED");
    assertThat(status(mvc.perform(get(BASE)).andReturn())).isEqualTo(401);
  }

  @Test
  void thereIsNoWriteRoute() throws Exception {
    UUID admin = admin();
    for (var request :
        List.of(
            post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"),
            put(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"),
            patch(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"),
            delete(BASE))) {
      assertThat(status(mvc.perform(request.with(asAdmin(admin))).andReturn())).isEqualTo(405);
    }
    // Aucune route par identifiant non plus.
    assertThat(
            status(
                mvc.perform(delete(BASE + "/" + UUID.randomUUID()).with(asAdmin(admin)))
                    .andReturn()))
        .isIn(404, 405);
    assertThat(
            status(
                mvc.perform(
                        patch(BASE + "/" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(asAdmin(admin)))
                    .andReturn()))
        .isIn(404, 405);
  }

  // ---- contenu et ordre

  @Test
  void journalListsDecisionsAndEventsNewestFirst() throws Exception {
    Scenario s = scenario();
    JsonNode items = items(journal(s.suspender(), "?practitionerId=" + s.practitioner()));
    List<String> kinds = kinds(items);
    assertThat(kinds).hasSize(3);
    // Suspension (événement et décision, même instant) d'abord, approbation d'il y a une heure
    // ensuite.
    assertThat(kinds.subList(0, 2)).containsExactlyInAnyOrder("admin.suspend", "admin.suspended");
    assertThat(kinds.get(2)).isEqualTo("admin.approved");

    for (JsonNode n : items) {
      assertThat(n.path("id").asString()).isNotBlank();
      assertThat(n.path("at").asString()).isNotBlank();
      assertThat(n.path("actorType").asString()).isEqualTo("admin");
      assertThat(n.path("practitionerId").asString()).isEqualTo(s.practitioner().toString());
      assertThat(n.path("practitionerDisplayName").asString()).isEqualTo("Dr Journal");
      assertThat(n.path("summary").asString()).isNotBlank();
    }
    JsonNode event = items.get(kinds.indexOf("admin.suspend"));
    assertThat(event.path("source").asString()).isEqualTo("security_event");
    assertThat(event.path("actor").path("id").asString()).isEqualTo(s.suspender().toString());
    assertThat(event.path("actor").path("displayName").asString()).isEqualTo("Admin Test");
    assertThat(event.path("summary").asString()).startsWith("Compte suspendu : ");
    assertThat(event.path("details").path("reason").asString()).isEqualTo(REASON);
    assertThat(event.path("details").path("previousStatus").asString()).isEqualTo("approved");

    JsonNode suspendedDecision = items.get(kinds.indexOf("admin.suspended"));
    assertThat(suspendedDecision.path("source").asString()).isEqualTo("verification_decision");
    assertThat(suspendedDecision.path("summary").asString()).isEqualTo(REASON);

    JsonNode approved = items.get(2);
    assertThat(approved.path("source").asString()).isEqualTo("verification_decision");
    assertThat(approved.path("actor").path("id").asString()).isEqualTo(s.approver().toString());
    assertThat(approved.path("summary").asString()).isEqualTo("approved");

    // Plus récent d'abord sur l'ensemble du journal (au tri près à la milliseconde, comme le
    // curseur).
    JsonNode all = items(journal(s.suspender(), "?limit=200"));
    long previous = Long.MAX_VALUE;
    for (JsonNode n : all) {
      long at = Instant.parse(n.path("at").asString()).toEpochMilli();
      assertThat(at).isLessThanOrEqualTo(previous);
      previous = at;
    }
  }

  @Test
  void actorTypeTellsAdminPractitionerAndSystemApart() throws Exception {
    UUID admin = admin();
    UUID p = practitioner("Dr Acteur", "approved");
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, ip, user_agent, details)"
            + " values (?, ?, 'login_ok', '203.0.113.7', 'Mozilla/5.0 (X11; Linux x86_64)',"
            + " '{\"mode\":\"password\"}')",
        UUID.randomUUID(),
        p);
    String systemKind =
        "system.purge" + UUID.randomUUID().toString().substring(0, 6).replace("-", "");
    jdbc.update(
        "insert into security_event (id, kind, details) values (?, ?, '{\"purged\":3}')",
        UUID.randomUUID(),
        systemKind);

    JsonNode own = items(journal(admin, "?kind=login_ok&practitionerId=" + p));
    assertThat(own).hasSize(1);
    assertThat(own.get(0).path("actorType").asString()).isEqualTo("practitioner");
    assertThat(own.get(0).path("actor").isNull()).isTrue();
    assertThat(own.get(0).path("practitionerDisplayName").asString()).isEqualTo("Dr Acteur");
    assertThat(own.get(0).path("details").path("mode").asString()).isEqualTo("password");

    JsonNode system = items(journal(admin, "?kind=" + systemKind));
    assertThat(system).hasSize(1);
    assertThat(system.get(0).path("actorType").asString()).isEqualTo("system");
    assertThat(system.get(0).path("actor").isNull()).isTrue();
    assertThat(system.get(0).path("practitionerId").isNull()).isTrue();
  }

  // ---- filtres

  @Test
  void kindFilterMatchesExactlyOrByPrefix() throws Exception {
    Scenario s = scenario();
    String practitioner = "&practitionerId=" + s.practitioner();
    assertThat(kinds(items(journal(s.suspender(), "?kind=admin.suspend" + practitioner))))
        .containsExactly("admin.suspend");
    assertThat(kinds(items(journal(s.suspender(), "?kind=admin.suspended" + practitioner))))
        .containsExactly("admin.suspended");
    assertThat(kinds(items(journal(s.suspender(), "?kind=admin.approved" + practitioner))))
        .containsExactly("admin.approved");
    assertThat(kinds(items(journal(s.suspender(), "?kind=admin" + practitioner))))
        .containsExactlyInAnyOrder("admin.suspend", "admin.suspended", "admin.approved");
    assertThat(items(journal(s.suspender(), "?kind=login" + practitioner))).isEmpty();

    MvcResult bad =
        journal(s.suspender(), "?kind=" + URLEncoder.encode("admin%", StandardCharsets.UTF_8));
    assertThat(status(bad)).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString()).isEqualTo("kind");
    assertThat(body(bad).path("errors").get(0).path("code").asString()).isEqualTo("INVALID_KIND");
  }

  @Test
  void practitionerAndActorFiltersNarrowTheJournal() throws Exception {
    Scenario s = scenario();
    UUID other = practitioner("Dr Autre", "approved");
    suspend(s.suspender(), other);

    JsonNode onlyFirst = items(journal(s.suspender(), "?practitionerId=" + s.practitioner()));
    assertThat(onlyFirst).hasSize(3);
    for (JsonNode n : onlyFirst) {
      assertThat(n.path("practitionerId").asString()).isEqualTo(s.practitioner().toString());
    }
    JsonNode byApprover = items(journal(s.suspender(), "?actorId=" + s.approver()));
    assertThat(kinds(byApprover)).contains("admin.approved");
    for (JsonNode n : byApprover) {
      assertThat(n.path("actor").path("id").asString()).isEqualTo(s.approver().toString());
    }
    JsonNode both =
        items(journal(s.suspender(), "?actorId=" + s.suspender() + "&practitionerId=" + other));
    assertThat(kinds(both)).containsExactlyInAnyOrder("admin.suspend", "admin.suspended");

    MvcResult bad = journal(s.suspender(), "?practitionerId=pas-un-uuid");
    assertThat(status(bad)).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString())
        .isEqualTo("practitionerId");
    assertThat(status(journal(s.suspender(), "?actorId=zzz"))).isEqualTo(422);
  }

  @Test
  void dateRangeFiltersTheJournal() throws Exception {
    Scenario s = scenario();
    String practitioner = "&practitionerId=" + s.practitioner();
    String halfHourAgo =
        URLEncoder.encode(
            Instant.now().minus(Duration.ofMinutes(30)).toString(), StandardCharsets.UTF_8);
    String inAnHour =
        URLEncoder.encode(
            Instant.now().plus(Duration.ofHours(1)).toString(), StandardCharsets.UTF_8);

    assertThat(kinds(items(journal(s.suspender(), "?to=" + halfHourAgo + practitioner))))
        .containsExactly("admin.approved");
    assertThat(kinds(items(journal(s.suspender(), "?from=" + halfHourAgo + practitioner))))
        .containsExactlyInAnyOrder("admin.suspend", "admin.suspended");
    assertThat(items(journal(s.suspender(), "?from=" + inAnHour + practitioner))).isEmpty();
    assertThat(
            items(
                journal(s.suspender(), "?from=" + halfHourAgo + "&to=" + inAnHour + practitioner)))
        .hasSize(2);

    // Une date seule couvre la journée entière (UTC).
    String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
    assertThat(
            items(
                journal(
                    s.suspender(),
                    "?from=" + today + "&to=" + today + "&kind=admin.suspend" + practitioner)))
        .hasSize(1);

    MvcResult bad = journal(s.suspender(), "?from=hier");
    assertThat(status(bad)).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString()).isEqualTo("from");
    assertThat(body(bad).path("errors").get(0).path("code").asString()).isEqualTo("INVALID_DATE");
    MvcResult badTo = journal(s.suspender(), "?to=2026-13-45T00:00:00Z");
    assertThat(status(badTo)).isEqualTo(422);
    assertThat(body(badTo).path("errors").get(0).path("code").asString()).isEqualTo("INVALID_DATE");
  }

  // ---- pagination

  @Test
  void cursorPaginationWalksTheWholeJournalWithoutRepeats() throws Exception {
    Scenario s = scenario();
    String practitioner = "practitionerId=" + s.practitioner();
    List<String> full = new ArrayList<>();
    for (JsonNode n : items(journal(s.suspender(), "?" + practitioner))) {
      full.add(n.path("id").asString());
    }
    assertThat(full).hasSize(3);

    List<String> walked = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      MvcResult r =
          journal(
              s.suspender(),
              "?" + practitioner + "&limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
      JsonNode page = body(r);
      assertThat(page.path("items").size()).isLessThanOrEqualTo(1);
      for (JsonNode n : page.path("items")) {
        walked.add(n.path("id").asString());
      }
      cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asString();
      pages++;
    } while (cursor != null && pages < 10);
    assertThat(pages).isEqualTo(3);
    assertThat(walked).containsExactlyElementsOf(full);

    MvcResult bad = journal(s.suspender(), "?cursor=%25%25");
    assertThat(status(bad)).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString()).isEqualTo("cursor");
    // limit est borné à 200 sans erreur.
    assertThat(status(journal(s.suspender(), "?limit=100000"))).isEqualTo(200);
  }

  // ---- confidentialité

  @Test
  void journalNeverExposesIpAddressesUserAgentsOrPatientData() throws Exception {
    Scenario s = scenario();
    UUID admin = s.suspender();
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, ip, user_agent, details)"
            + " values (?, ?, 'login_failed', '198.51.100.23', 'Agent/1.0 (203.0.113.9)',"
            + " '{\"reason\":\"bad_password\"}')",
        UUID.randomUUID(),
        s.practitioner());
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, ip, details) values (?, ?,"
            + " 'login_ok', '2001:db8:85a3:0:0:8a2e:370:7334', null)",
        UUID.randomUUID(),
        s.practitioner());
    // Audit applicatif patient (M7-02) : jamais exposé par le journal d'administration.
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, details) values (?, ?,"
            + " 'patient.read', ?::jsonb)",
        UUID.randomUUID(),
        s.practitioner(),
        "{\"patientId\":\"" + UUID.randomUUID() + "\"}");

    MvcResult r = journal(admin, "?practitionerId=" + s.practitioner() + "&limit=200");
    assertThat(status(r)).isEqualTo(200);
    String text = r.getResponse().getContentAsString();
    JsonNode items = body(r).path("items");
    assertThat(kinds(items)).contains("login_failed", "login_ok").doesNotContain("patient.read");
    assertThat(IPV4.matcher(text).find()).as("IPv4 dans " + text).isFalse();
    assertThat(IPV6.matcher(text).find()).as("IPv6 dans " + text).isFalse();
    assertThat(text)
        .doesNotContain("127.0.0.1")
        .doesNotContain("Agent/1.0")
        .doesNotContain("patientId");
    assertThat(text.toLowerCase())
        .doesNotContain("\"ip\"")
        .doesNotContain("user_agent")
        .doesNotContain("useragent");
    for (JsonNode n : items) {
      assertThat(n.has("ip")).isFalse();
      assertThat(n.has("userAgent")).isFalse();
    }
    assertNoForbiddenKey(body(r), "$");
    assertThat(kinds(items(journal(admin, "?kind=patient&limit=200")))).isEmpty();
  }

  // ---- append-only côté base

  @Test
  void appRoleCannotUpdateOrDeleteTheJournalSourceTablesButCanReadTheView() throws Exception {
    Scenario s = scenario();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("set role psymate_app");
      try {
        for (String table : List.of("security_event", "verification_decision")) {
          assertThatThrownBy(() -> statement.execute("update " + table + " set id = id"))
              .as("update " + table)
              .isInstanceOf(SQLException.class)
              .extracting(e -> ((SQLException) e).getSQLState())
              .isEqualTo("42501");
          assertThatThrownBy(() -> statement.execute("delete from " + table))
              .as("delete " + table)
              .isInstanceOf(SQLException.class)
              .extracting(e -> ((SQLException) e).getSQLState())
              .isEqualTo("42501");
        }
        // La vue est en lecture seule : elle ne s'écrit pas.
        assertThatThrownBy(() -> statement.execute("update admin_journal_v set summary = 'x'"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> statement.execute("delete from admin_journal_v"))
            .isInstanceOf(SQLException.class);
        statement.execute("select count(*) from admin_journal_v");
      } finally {
        statement.execute("reset role");
      }
    }
    assertThat(
            count(
                "select count(*) from admin_journal_v where practitioner_id = ?", s.practitioner()))
        .isEqualTo(3);
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and kind = 'admin.suspend'",
                s.practitioner()))
        .isEqualTo(1);
  }
}
