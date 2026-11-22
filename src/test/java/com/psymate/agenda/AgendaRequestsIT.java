package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code GET /api/agenda/requests} (M14-04, G Q74, K5.2) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) : seules les demandes en ligne à confirmer, à venir, du praticien
 * courant, triées par début puis identifiant, 100 au plus. Aucune route ne crée encore de
 * rendez-vous en ligne (M14-06) : les rendez-vous sont créés par l'API puis passés à {@code origin
 * = 'online'} en SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class AgendaRequestsIT {

  private static final String SARA =
      "{\"lastName\":\"Benali\",\"firstName\":\"Sara\",\"email\":\"sara@example.test\"}";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID bob;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet demandes')", tenant);
    alice = practitioner();
    bob = practitioner();
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void onlyUpcomingTentativeOnlineAppointmentsOfTheCurrentPractitionerSortedByStart()
      throws Exception {
    Instant base = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    // Attendues : deux demandes en ligne à venir, créées dans le désordre.
    String later = online(alice, base.plus(2, ChronoUnit.DAYS), true);
    String sooner = online(alice, base, true);
    // Exclues : à confirmer posée par le praticien, en ligne déjà confirmée, en ligne passée,
    // en ligne annulée, en ligne d'un autre praticien.
    String practitionerTentative = create(alice, base.plus(1, ChronoUnit.DAYS), true);
    String planned = online(alice, base.plus(3, ChronoUnit.DAYS), false);
    String past = online(alice, Instant.now().minus(2, ChronoUnit.DAYS), true);
    String cancelled = online(alice, base.plus(4, ChronoUnit.DAYS), true);
    post(alice, "/api/appointments/" + cancelled + "/cancel", "{}").andExpect(status().isOk());
    String bobs = online(bob, base, true);

    String body =
        get(alice, "/api/agenda/requests")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appointments.length()").value(2))
            .andExpect(jsonPath("$.appointments[0].id").value(sooner))
            .andExpect(jsonPath("$.appointments[1].id").value(later))
            .andExpect(jsonPath("$.appointments[0].origin").value("online"))
            .andExpect(jsonPath("$.appointments[0].status").value("tentative"))
            .andExpect(jsonPath("$.appointments[0].guest.lastName").value("Benali"))
            .andExpect(jsonPath("$.appointments[0].guest.email").value("sara@example.test"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<String> ids = JsonPath.read(body, "$.appointments[*].id");
    assertThat(ids).doesNotContain(practitionerTentative, planned, past, cancelled, bobs);

    get(bob, "/api/agenda/requests")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(1))
        .andExpect(jsonPath("$.appointments[0].id").value(bobs));
  }

  @Test
  void sameStartIsOrderedByIdentifier() throws Exception {
    Instant start = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    List<String> expected = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      expected.add(online(alice, start, true));
    }
    // Ordre de PostgreSQL sur uuid : octet par octet, non signé, comme le texte hexadécimal.
    expected.sort(Comparator.naturalOrder());

    String body =
        get(alice, "/api/agenda/requests")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<String> ids = JsonPath.read(body, "$.appointments[*].id");
    assertThat(ids).containsExactlyElementsOf(expected);
  }

  @Test
  void confirmedRequestLeavesTheList() throws Exception {
    String id = online(alice, Instant.now().plus(5, ChronoUnit.DAYS), true);
    get(alice, "/api/agenda/requests").andExpect(jsonPath("$.appointments.length()").value(1));

    post(alice, "/api/appointments/" + id + "/confirm", null).andExpect(status().isOk());

    get(alice, "/api/agenda/requests")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(0));
  }

  @Test
  void atMostOneHundredRequests() throws Exception {
    Instant start = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      Instant at = start.plus(i, ChronoUnit.HOURS);
      rows.add(
          new Object[] {
            UUID.randomUUID(),
            tenant,
            alice,
            Timestamp.from(at),
            Timestamp.from(at.plus(50, ChronoUnit.MINUTES))
          });
    }
    jdbc.batchUpdate(
        "insert into appointment (id, tenant_id, practitioner_id, starts_at, ends_at, modality,"
            + " status, guest_last_name, origin)"
            + " values (?, ?, ?, ?, ?, 'video', 'tentative', 'Benali', 'online')",
        rows);

    get(alice, "/api/agenda/requests")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(100))
        .andExpect(jsonPath("$.appointments[0].startsAt").value(start.toString()));
  }

  @Test
  void withoutSessionIs401() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/agenda/requests"))
        .andExpect(status().isUnauthorized());
  }

  // ---- outils

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Agenda', 'Dr Agenda', 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  /** Rendez-vous invité créé par l'API (origine practitioner), à confirmer ou planifié. */
  private String create(UUID who, Instant startsAt, boolean tentative) throws Exception {
    String body =
        "{\"startsAt\":\""
            + startsAt
            + "\",\"durationMin\":50,\"modality\":\"video\",\"tentative\":"
            + tentative
            + ",\"guest\":"
            + SARA
            + "}";
    String response =
        post(who, "/api/appointments", body)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return JsonPath.read(response, "$.appointments[0].id");
  }

  /** Comme {@link #create}, puis passé à l'origine online (ce que fera M14-06). */
  private String online(UUID who, Instant startsAt, boolean tentative) throws Exception {
    String id = create(who, startsAt, tentative);
    jdbc.update("update appointment set origin = 'online' where id = ?", UUID.fromString(id));
    return id;
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Agenda",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, UUID who, String body)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(as(who)));
  }

  private ResultActions post(UUID who, String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.post(url), who, body);
  }

  private ResultActions get(UUID who, String url) throws Exception {
    return send(MockMvcRequestBuilders.get(url), who, null);
  }
}
