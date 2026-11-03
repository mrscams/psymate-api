package com.psymate.perf;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Json;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.SeedException;
import com.psymate.seed.StepExecutor;
import com.psymate.seed.StepExecutor.Result;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Socle des tests de charge (M7-01) : Postgres pgvector (Testcontainers), un cabinet et un
 * praticien insérés en SQL (c'est un test, comme ClinicalFixtures), requêtes MockMvc authentifiées,
 * exécution d'un plan du générateur partagé {@code tools/seed} par l'API REST, puis attente du
 * drainage des événements (projections, index de recherche) et ANALYZE.
 *
 * <p>Le package {@code com.psymate.perf} n'est compilé que sous le profil Maven {@code perf} (voir
 * pom.xml) : il importe {@code com.psymate.seed}, dont les sources sont ajoutées à ce moment-là.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
abstract class PerfSupport {

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;

  protected UUID tenant;
  protected UUID practitioner;
  protected RequestPostProcessor me;

  @BeforeEach
  void perfSetUp() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet perf')", tenant);
    practitioner = UUID.randomUUID();
    String email = practitioner + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Perf', 'Praticien', 'Dr Perf', 'psychologist')",
        practitioner,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(practitioner, tenant, email, "Dr Perf", "psychologist", "none");
    me =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- requêtes

  /** Requête authentifiée ; le chemin peut porter une query string. */
  protected MvcResult perform(String method, String path, Object body) {
    try {
      MockHttpServletRequestBuilder builder =
          switch (method) {
            case "POST" -> MockMvcRequestBuilders.post(path);
            case "PATCH" -> MockMvcRequestBuilders.patch(path);
            case "GET" -> MockMvcRequestBuilders.get(path);
            default -> throw new IllegalArgumentException(method);
          };
      if (body != null) {
        builder
            .contentType(MediaType.APPLICATION_JSON)
            .content(Json.write(body).getBytes(StandardCharsets.UTF_8));
      }
      return mvc.perform(builder.with(me)).andReturn();
    } catch (Exception e) {
      throw new SeedException(method + " " + path + " : " + e, e);
    }
  }

  protected Response call(String method, String path, Object body) {
    MvcResult r = perform(method, path, body);
    try {
      return new Response(
          r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new SeedException(method + " " + path + " : " + e, e);
    }
  }

  // ---- exécution d'un plan

  /**
   * Exécute le plan par MockMvc, étape par étape, avec la même résolution de clés que le Runner
   * HTTP ; toute réponse inattendue fait échouer avec le corps problem+json.
   */
  protected Result execute(PatientPlan plan) {
    StepExecutor.ensureTags(this::call, plan.tags());
    return StepExecutor.execute(plan, this::call, null);
  }

  /**
   * Attend que les événements publiés (index de recherche, projection de vue d'ensemble, séance
   * d'accueil...) soient tous traités, puis met à jour les statistiques du planificateur.
   *
   * @return le nombre de publications restées incomplètes après l'attente (0 si tout est drainé)
   */
  protected int drainEventsAndAnalyze() {
    int pending;
    try {
      await()
          .atMost(Duration.ofMinutes(2))
          .pollInterval(Duration.ofMillis(500))
          .until(
              () ->
                  jdbc.queryForObject(
                          "select count(*) from event_publication where completion_date is null",
                          Integer.class)
                      == 0);
    } catch (org.awaitility.core.ConditionTimeoutException e) {
      // on mesure quand même : le rapport signale les publications en attente
    }
    pending =
        jdbc.queryForObject(
            "select count(*) from event_publication where completion_date is null", Integer.class);
    for (String table :
        List.of("entry", "entry_version", "entry_feeling", "link", "patient_overview")) {
      jdbc.execute("analyze " + table);
    }
    return pending;
  }
}
