package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Deux émissions concurrentes (M15-09, G Q89, L3.3) : deux brouillons d'un même praticien émis en
 * même temps, chacun dans sa transaction et son fil, avec le même numéro attendu. Le compteur de
 * l'année est tenu verrouillé par une troisième transaction jusqu'à ce que les deux demandes
 * attendent (l'une sur la ligne du compteur, l'autre sur le verrou des réglages) : la première
 * reçoit 0001, la seconde 409 INVOICE_NUMBER_CHANGED avec 0002, puis 0002 en recommençant. Ni
 * doublon, ni trou.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class InvoiceIssueConcurrencyIT {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private Clock clock;

  private UUID tenant;
  private UUID alice;
  private UUID theo;
  private int year;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet concurrence')", tenant);
    alice = UUID.randomUUID();
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Concurrence', 'Dr Concurrence',"
            + " 'psychologist')",
        alice,
        tenant,
        alice + "@example.test",
        alice + "@example.test");
    theo = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Théo B.')",
        theo,
        tenant,
        alice);
    jdbc.update(
        "insert into billing_settings (practitioner_id, tenant_id, siret, address_lines)"
            + " values (?, ?, '73282932000074', '1 rue de la Paix')",
        alice,
        tenant);
    jdbc.update(
        "insert into billing_service (id, tenant_id, practitioner_id, label, price_cents,"
            + " is_default) values (?, ?, ?, 'Séance', 6000, true)",
        UUID.randomUUID(),
        tenant,
        alice);
    year = LocalDate.now(clock.withZone(PARIS)).getYear();
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void twoConcurrentIssuesProduceNeitherDuplicateNorGap() throws Exception {
    UUID first = draft(5000);
    UUID second = draft(7000);
    String one = number(1);
    jdbc.update(
        "insert into billing_counter (practitioner_id, kind, year, last_number)"
            + " values (?, 'invoice', ?, 0)",
        alice,
        year);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<MvcResult> results = new ArrayList<>();
    try (Connection blocker = dataSource.getConnection()) {
      blocker.setAutoCommit(false);
      try (PreparedStatement lock =
          blocker.prepareStatement(
              "select last_number from billing_counter where practitioner_id = ? and kind ="
                  + " 'invoice' and year = ? for update")) {
        lock.setObject(1, alice);
        lock.setInt(2, year);
        try (ResultSet rs = lock.executeQuery()) {
          assertThat(rs.next()).isTrue();
        }
      }
      Future<MvcResult> a = pool.submit(() -> issue(first, one));
      Future<MvcResult> b = pool.submit(() -> issue(second, one));
      // Les deux transactions attendent : l'une le compteur, l'autre le verrou des réglages.
      await().atMost(WAIT).until(() -> waitingIssues() >= 2);
      blocker.commit();
      results.add(a.get(60, TimeUnit.SECONDS));
      results.add(b.get(60, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).toList();
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    MvcResult conflict =
        results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
    String body = conflict.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("INVOICE_NUMBER_CHANGED");
    assertThat((String) JsonPath.read(body, "$.nextNumber")).isEqualTo(number(2));

    // Le second onglet réaffiche le numéro et recommence.
    UUID late = statuses.get(0) == 409 ? first : second;
    assertThat(issue(late, number(2)).getResponse().getStatus()).isEqualTo(200);

    assertThat(
            jdbc.queryForList(
                "select number from invoice where practitioner_id = ? and number is not null"
                    + " order by number",
                String.class,
                alice))
        .containsExactly(number(1), number(2));
    assertThat(
            jdbc.queryForObject(
                "select last_number from billing_counter where practitioner_id = ? and kind ="
                    + " 'invoice' and year = ?",
                Integer.class,
                alice,
                year))
        .isEqualTo(2);
  }

  /** Demandes d'émission en attente d'un verrou (ligne du compteur ou verrou consultatif). */
  private int waitingIssues() {
    return jdbc.queryForObject(
        "select count(*) from pg_stat_activity where datname = current_database()"
            + " and wait_event_type = 'Lock' and (query like '%billing_counter%'"
            + " or query like '%pg_advisory_xact_lock%')",
        Integer.class);
  }

  private String number(int n) {
    return String.format(Locale.ROOT, "F-%d-%04d", year, n);
  }

  private UUID draft(int cents) throws Exception {
    MvcResult result =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        ("{\"patientId\":\""
                                + theo
                                + "\",\"lines\":[{\"label\":\"Séance\",\"unitPrice\":"
                                + cents
                                + "}]}")
                            .getBytes(StandardCharsets.UTF_8))
                    .with(as(alice)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
  }

  private MvcResult issue(UUID invoice, String expectedNumber) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post("/api/invoices/" + invoice + "/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"expectedNumber\":\"" + expectedNumber + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                .with(as(alice)))
        .andReturn();
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Concurrence",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }
}
