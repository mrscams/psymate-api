package com.psymate.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.psymate.PostgresTestConfig;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Format d'erreur unique (M7-02) : chaque famille d'erreur répond en application/problem+json avec
 * type, title, status, detail et code (et errors[] pour la validation). Les erreurs 409, 423, 413,
 * 422 de champ et 500 sont provoquées par un contrôleur de test imbriqué ici (jamais de classe de
 * premier niveau, qui polluerait le contrat OpenAPI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, ProblemDetailIT.Endpoints.class})
class ProblemDetailIT {

  static final String SECRET = "secret-internal-message-do-not-leak";
  private static final AtomicInteger IP = new AtomicInteger();

  record Body(@NotBlank String name, @Min(1) int age) {}

  @RestController
  @RequestMapping("/api/test-problems")
  static class Endpoints {

    @GetMapping("/conflict")
    void conflict() {
      throw new ProblemException(
          HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Conflict", "Conflit de test.");
    }

    @GetMapping("/locked")
    void locked() {
      throw new ProblemException(
          HttpStatus.LOCKED,
          ErrorCode.LOCKED,
          "Locked",
          "Verrouillé pour le test.",
          List.of(new FieldProblem("account", "LOCKED_UNTIL", "Réessayez plus tard.")));
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException(SECRET);
    }

    @GetMapping("/too-large")
    void tooLarge() {
      // Le plafond multipart n'est pas appliqué par MockMvc : on lève l'exception du conteneur.
      throw new MaxUploadSizeExceededException(26L * 1024 * 1024);
    }

    @PostMapping("/valid")
    void valid(@Valid @RequestBody Body body) {}

    @GetMapping("/param")
    void param(@RequestParam int n) {}
  }

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private RequestPostProcessor auth;

  @BeforeEach
  void practitioner() {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        id,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, email, "Dr Test", "psychologist", "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions perform(MockHttpServletRequestBuilder b) throws Exception {
    return mvc.perform(b.with(auth));
  }

  /** Vérifie le socle commun d'une erreur : problem+json et champs obligatoires. */
  private static ResultActions problem(ResultActions r, int status, String code) throws Exception {
    return r.andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            result -> {
              // RFC 9457 : type absent = about:blank (les handlers MVC l'omettent, les filtres
              // l'écrivent). Dans les deux cas, jamais autre chose.
              String type =
                  JsonPath.using(
                          Configuration.defaultConfiguration()
                              .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))
                      .parse(result.getResponse().getContentAsString())
                      .read("$.type", String.class);
              assertThat(type).isIn(null, "about:blank");
            })
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  void malformedJsonIs400() throws Exception {
    problem(
            perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{nope")),
            400,
            "MALFORMED_JSON")
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.instance").value("/api/patients"));
  }

  @Test
  void unauthenticatedIs401() throws Exception {
    problem(mvc.perform(get("/api/patients")), 401, "UNAUTHENTICATED")
        .andExpect(jsonPath("$.title").value("Non authentifié"))
        .andExpect(jsonPath("$.detail").value("Authentification requise."));
  }

  @Test
  void nonAdminOnAdminRouteIs403() throws Exception {
    problem(perform(get("/api/admin/journal")), 403, "ADMIN_ROLE_REQUIRED")
        .andExpect(jsonPath("$.title").value("Accès réservé à l'administration"))
        .andExpect(jsonPath("$.detail").value("Cette ressource est réservée aux administrateurs."));
  }

  @Test
  void unknownRouteIs404() throws Exception {
    problem(perform(get("/api/nope")), 404, "NOT_FOUND")
        .andExpect(jsonPath("$.title").value("Not Found"))
        .andExpect(jsonPath("$.detail").value("Ressource introuvable."))
        .andExpect(jsonPath("$.instance").value("/api/nope"));
  }

  @Test
  void englishIsServedOnlyWhenAsked() throws Exception {
    problem(perform(get("/api/nope").header("Accept-Language", "en-US,en;q=0.9")), 404, "NOT_FOUND")
        .andExpect(jsonPath("$.detail").value("Resource not found."));
    problem(perform(get("/api/nope").header("Accept-Language", "de")), 404, "NOT_FOUND")
        .andExpect(jsonPath("$.detail").value("Ressource introuvable."));
  }

  @Test
  void unsupportedMethodIs405WithAllow() throws Exception {
    problem(perform(put("/api/patients")), 405, "METHOD_NOT_ALLOWED")
        .andExpect(header().exists("Allow"));
    problem(perform(delete("/api/patients")), 405, "METHOD_NOT_ALLOWED");
  }

  @Test
  void unacceptableMediaTypeIs406() throws Exception {
    problem(perform(get("/api/patients").accept(MediaType.APPLICATION_XML)), 406, "NOT_ACCEPTABLE");
  }

  @Test
  void conflictIs409() throws Exception {
    problem(perform(get("/api/test-problems/conflict")), 409, "CONFLICT")
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.detail").value("Conflit de test."))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void lockedIs423WithFieldErrors() throws Exception {
    problem(perform(get("/api/test-problems/locked")), 423, "LOCKED")
        .andExpect(jsonPath("$.errors[0].field").value("account"))
        .andExpect(jsonPath("$.errors[0].code").value("LOCKED_UNTIL"))
        .andExpect(jsonPath("$.errors[0].message").value("Réessayez plus tard."));
  }

  @Test
  void uploadTooLargeIs413() throws Exception {
    problem(perform(get("/api/test-problems/too-large")), 413, "UPLOAD_TOO_LARGE")
        .andExpect(jsonPath("$.detail").value("Le fichier dépasse la taille maximale autorisée."));
  }

  @Test
  void unsupportedMediaTypeIs415() throws Exception {
    problem(
        perform(post("/api/patients").contentType(MediaType.TEXT_PLAIN).content("bonjour")),
        415,
        "UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void invalidBodyIs422WithErrorsPerField() throws Exception {
    problem(
            perform(
                post("/api/test-problems/valid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"age\":0}")),
            422,
            "VALIDATION_FAILED")
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("INVALID_FIELD"))
        .andExpect(jsonPath("$.errors[?(@.field=='age')].code").value("INVALID_FIELD"))
        .andExpect(jsonPath("$.errors[?(@.field=='age')].message").isNotEmpty());
  }

  @Test
  void aModuleHandlerKeepsPrecedenceOverTheGlobalOne() throws Exception {
    // PatientExceptionHandler répond (VALIDATION_FAILED, errors[].field) : le global n'intervient
    // pas.
    problem(
            perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\":\"X\"}")),
            422,
            "VALIDATION_FAILED")
        .andExpect(jsonPath("$.errors[0].field").value("displayName"));
  }

  @Test
  void badParametersAre400WithTheFaultyField() throws Exception {
    problem(perform(get("/api/test-problems/param?n=abc")), 400, "BAD_REQUEST")
        .andExpect(jsonPath("$.errors[0].field").value("n"));
    problem(perform(get("/api/test-problems/param")), 400, "BAD_REQUEST")
        .andExpect(jsonPath("$.errors[0].field").value("n"));
  }

  @Test
  void rateLimitedIs429() throws Exception {
    String ip = "10.88.0." + (IP.incrementAndGet() % 250 + 1);
    for (int i = 0; i < 60; i++) {
      mvc.perform(get("/api/public/directory/practitioners/absent").with(from(ip)))
          .andExpect(status().isNotFound());
    }
    problem(mvc.perform(get("/api/public/directory").with(from(ip))), 429, "RATE_LIMITED")
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
  }

  @Test
  void unexpectedErrorIs500WithAGenericDetail() throws Exception {
    problem(perform(get("/api/test-problems/boom")), 500, "INTERNAL_ERROR")
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(content().string(not(containsString(SECRET))))
        .andExpect(content().string(not(containsString("IllegalStateException"))));
    assertThat(
            perform(get("/api/test-problems/boom")).andReturn().getResponse().getContentAsString())
        .doesNotContain(SECRET);
  }

  private static RequestPostProcessor from(String ip) {
    return r -> {
      r.setRemoteAddr(ip);
      return r;
    };
  }
}
