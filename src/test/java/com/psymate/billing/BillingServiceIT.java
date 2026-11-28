package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * Prestations du praticien (M15-05, L5.1) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) : un cas par critère d'acceptation, deux praticiens du même cabinet
 * (Alice et Bob) pour l'isolement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class BillingServiceIT {

  private static final String SERVICES = "/api/me/billing-services";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID bob;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet facturation')", tenant);
    alice = practitioner();
    bob = practitioner();
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- création et liste

  @Test
  void firstCreatedServiceIsTheDefaultAndIsStoredInCents() throws Exception {
    String id =
        idOf(
            post(alice, SERVICES, service("Séance individuelle", 6000, 50, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Séance individuelle"))
                .andExpect(jsonPath("$.price").value(6000))
                .andExpect(jsonPath("$.durationMin").value(50))
                .andExpect(jsonPath("$.isDefault").value(true)));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select tenant_id, practitioner_id, price_cents, is_default, archived_at"
                + " from billing_service where id = ?",
            UUID.fromString(id));
    assertThat(row.get("tenant_id")).isEqualTo(tenant);
    assertThat(row.get("practitioner_id")).isEqualTo(alice);
    assertThat(row.get("price_cents")).isEqualTo(6000);
    assertThat(row.get("is_default")).isEqualTo(true);
    assertThat(row.get("archived_at")).isNull();
  }

  @Test
  void firstServiceIsTheDefaultEvenWithIsDefaultFalse() throws Exception {
    post(alice, SERVICES, "{\"label\":\"Bilan\",\"price\":9000,\"isDefault\":false}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isDefault").value(true))
        .andExpect(jsonPath("$.durationMin").value(Matchers.nullValue()));
  }

  @Test
  void listShowsTheDefaultFirstThenByLabel() throws Exception {
    post(alice, SERVICES, service("Zèbre", 5000, null, null)).andExpect(status().isCreated());
    post(alice, SERVICES, service("beta", 4000, null, null)).andExpect(status().isCreated());
    post(alice, SERVICES, service("Alpha", 3000, null, false)).andExpect(status().isCreated());

    get(alice, SERVICES)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.services.length()").value(3))
        .andExpect(jsonPath("$.services[0].label").value("Zèbre"))
        .andExpect(jsonPath("$.services[0].isDefault").value(true))
        .andExpect(jsonPath("$.services[1].label").value("Alpha"))
        .andExpect(jsonPath("$.services[1].isDefault").value(false))
        .andExpect(jsonPath("$.services[2].label").value("beta"));
  }

  @Test
  void creatingWithIsDefaultTrueMovesTheDefault() throws Exception {
    String first = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));
    String second =
        idOf(
            post(alice, SERVICES, service("Couple", 8000, 75, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true)));

    assertThat(isDefault(first)).isFalse();
    assertThat(isDefault(second)).isTrue();
    assertThat(defaultsOf(alice)).isEqualTo(1);
  }

  @Test
  void fiftyActiveServicesAtMost() throws Exception {
    for (int i = 0; i < 50; i++) {
      insertService(alice, "Prestation " + i, 1000 + i, i == 0, false);
    }
    insertService(alice, "Archivée", 1000, false, true);

    post(alice, SERVICES, service("Une de trop", 6000, null, null))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SERVICE_LIMIT_REACHED"));
    assertThat(activeOf(alice)).isEqualTo(50);

    String some =
        jdbc.queryForObject(
            "select id::text from billing_service where practitioner_id = ? and not is_default"
                + " and archived_at is null limit 1",
            String.class,
            alice);
    delete(alice, some).andExpect(status().isNoContent());
    post(alice, SERVICES, service("Maintenant permise", 6000, null, null))
        .andExpect(status().isCreated());
  }

  // ---- modification

  @Test
  void patchMergesPartiallyAndNullDurationClearsIt() throws Exception {
    String id = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));

    patch(alice, id, "{\"price\":6500}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Séance"))
        .andExpect(jsonPath("$.price").value(6500))
        .andExpect(jsonPath("$.durationMin").value(50));
    patch(alice, id, "{\"label\":\"  Séance longue  \",\"durationMin\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Séance longue"))
        .andExpect(jsonPath("$.price").value(6500))
        .andExpect(jsonPath("$.durationMin").value(Matchers.nullValue()));
    patch(alice, id, "{\"label\":null,\"price\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Séance longue"))
        .andExpect(jsonPath("$.price").value(6500));
  }

  @Test
  void patchIsDefaultTrueRemovesTheDefaultFromTheOther() throws Exception {
    String first = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));
    String second = idOf(post(alice, SERVICES, service("Couple", 8000, 75, null)));

    patch(alice, second, "{\"isDefault\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isDefault").value(true));
    assertThat(isDefault(first)).isFalse();
    assertThat(isDefault(second)).isTrue();
    get(alice, SERVICES).andExpect(jsonPath("$.services[0].id").value(second));

    // Déjà par défaut : sans effet.
    patch(alice, second, "{\"isDefault\":true}").andExpect(status().isOk());
    assertThat(defaultsOf(alice)).isEqualTo(1);
  }

  @Test
  void patchIsDefaultFalseOnTheDefaultIsRefused() throws Exception {
    String id = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));
    String other = idOf(post(alice, SERVICES, service("Couple", 8000, 75, null)));

    patch(alice, id, "{\"isDefault\":false}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("isDefault"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    patch(alice, other, "{\"isDefault\":false}").andExpect(status().isOk());
    assertThat(isDefault(id)).isTrue();
  }

  @Test
  void patchRefusesBlankLabelAndWrongAmounts() throws Exception {
    String id = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));

    expectFieldError(patch(alice, id, "{\"label\":\"   \"}"), "label", "REQUIRED");
    expectFieldError(patch(alice, id, "{\"price\":0}"), "price", "OUT_OF_RANGE");
    expectFieldError(patch(alice, id, "{\"price\":60.5}"), "price", "INVALID_FORMAT");
    expectFieldError(patch(alice, id, "{\"durationMin\":601}"), "durationMin", "OUT_OF_RANGE");
    expectFieldError(patch(alice, id, "{\"archivedAt\":null}"), "archivedAt", "UNKNOWN_FIELD");
    get(alice, SERVICES).andExpect(jsonPath("$.services[0].price").value(6000));
  }

  // ---- archivage

  @Test
  void deleteArchivesAndIsIdempotent() throws Exception {
    idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));
    String other = idOf(post(alice, SERVICES, service("Couple", 8000, 75, null)));

    delete(alice, other).andExpect(status().isNoContent());
    delete(alice, other).andExpect(status().isNoContent());

    get(alice, SERVICES)
        .andExpect(jsonPath("$.services.length()").value(1))
        .andExpect(jsonPath("$.services[0].label").value("Séance"));
    assertThat(
            jdbc.queryForObject(
                "select archived_at is not null from billing_service where id = ?",
                Boolean.class,
                UUID.fromString(other)))
        .isTrue();
    // Une prestation archivée ne se modifie plus.
    patch(alice, other, "{\"price\":9000}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
  }

  @Test
  void theDefaultIsNotArchivedWhileOthersRemain() throws Exception {
    String first = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));
    String other = idOf(post(alice, SERVICES, service("Couple", 8000, 75, null)));

    delete(alice, first)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SERVICE_IS_DEFAULT"));
    assertThat(activeOf(alice)).isEqualTo(2);

    delete(alice, other).andExpect(status().isNoContent());
    // Seule, la prestation par défaut s'archive ; la suivante créée redevient le défaut.
    delete(alice, first).andExpect(status().isNoContent());
    assertThat(activeOf(alice)).isZero();
    post(alice, SERVICES, service("Nouvelle", 7000, null, null))
        .andExpect(jsonPath("$.isDefault").value(true));
  }

  // ---- isolement et erreurs

  @Test
  void anotherPractitionersServiceIsNotFound() throws Exception {
    String id = idOf(post(alice, SERVICES, service("Séance", 6000, 50, null)));

    get(bob, SERVICES).andExpect(jsonPath("$.services.length()").value(0));
    patch(bob, id, "{\"price\":1}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    delete(bob, id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    delete(alice, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    // Le défaut d'Alice ne gêne pas celui de Bob.
    post(bob, SERVICES, service("Séance", 5000, null, null))
        .andExpect(jsonPath("$.isDefault").value(true));
    assertThat(isDefault(id)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select price_cents from billing_service where id = ?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(6000);
  }

  @Test
  void zeroNegativeDecimalOrTextPricesAreRefused() throws Exception {
    expectFieldError(post(alice, SERVICES, service("A", 0, null, null)), "price", "OUT_OF_RANGE");
    expectFieldError(
        post(alice, SERVICES, service("A", -100, null, null)), "price", "OUT_OF_RANGE");
    expectFieldError(
        post(alice, SERVICES, "{\"label\":\"A\",\"price\":60.5}"), "price", "INVALID_FORMAT");
    expectFieldError(
        post(alice, SERVICES, "{\"label\":\"A\",\"price\":6000.0}"), "price", "INVALID_FORMAT");
    expectFieldError(
        post(alice, SERVICES, "{\"label\":\"A\",\"price\":\"6000\"}"), "price", "INVALID_FORMAT");
    expectFieldError(
        post(alice, SERVICES, "{\"label\":\"A\",\"price\":3000000000}"), "price", "OUT_OF_RANGE");
    expectFieldError(post(alice, SERVICES, "{\"label\":\"A\"}"), "price", "REQUIRED");
    assertThat(activeOf(alice)).isZero();
  }

  @Test
  void createRefusesMissingOrLongLabelBadDurationAndUnknownFields() throws Exception {
    expectFieldError(post(alice, SERVICES, "{\"price\":6000}"), "label", "REQUIRED");
    expectFieldError(
        post(alice, SERVICES, service("x".repeat(121), 6000, null, null)), "label", "OUT_OF_RANGE");
    expectFieldError(
        post(alice, SERVICES, service("A", 6000, 0, null)), "durationMin", "OUT_OF_RANGE");
    expectFieldError(
        post(alice, SERVICES, "{\"label\":\"A\",\"price\":6000,\"durationMin\":50.5}"),
        "durationMin",
        "INVALID_FORMAT");
    expectFieldError(
        post(
            alice, SERVICES, "{\"label\":\"A\",\"price\":6000,\"practitionerId\":\"" + bob + "\"}"),
        "practitionerId",
        "UNKNOWN_FIELD");
    expectFieldError(
        post(alice, SERVICES, "{\"label\":\"A\",\"price\":6000,\"tenantId\":null}"),
        "tenantId",
        "UNKNOWN_FIELD");
    assertThat(activeOf(alice)).isZero();
  }

  @Test
  void messagesFollowTheServedLanguage() throws Exception {
    String body = service("A", 0, null, null);
    send(MockMvcRequestBuilders.post(SERVICES).header("Accept-Language", "en"), alice, body)
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("The price is a strictly positive whole number of cents."));
    send(MockMvcRequestBuilders.post(SERVICES), alice, body)
        .andExpect(jsonPath("$.title").value("Requête invalide"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Le prix est un nombre entier de centimes strictement positif."));
  }

  @Test
  void malformedIdIs400AndUnauthenticatedIs401() throws Exception {
    patch(alice, "pas-un-uuid", "{\"price\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    mvc.perform(MockMvcRequestBuilders.get(SERVICES)).andExpect(status().isUnauthorized());
  }

  /** G Q84 : les montants sont des entiers dans le contrat, jamais des nombres décimaux. */
  @Test
  void contractDescribesAmountsAsIntegers() throws Exception {
    String docs =
        mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    for (String path :
        List.of(
            "BillingServiceDto.properties.price",
            "CreateBillingServiceRequest.properties.price",
            "UpdateBillingServiceRequest.properties.price",
            "BillingProfileDto.properties.rate",
            "BillingProfileRequest.properties.rate")) {
      Object type = JsonPath.read(docs, "$.components.schemas." + path + ".type");
      assertThat(String.valueOf(type)).as(path).contains("integer").doesNotContain("number");
      Object format = JsonPath.read(docs, "$.components.schemas." + path + ".format");
      assertThat(format).as(path).isEqualTo("int32");
    }
    Map<String, Object> properties =
        JsonPath.read(docs, "$.components.schemas.BillingServiceDto.properties");
    assertThat(properties).containsKey("isDefault").doesNotContainKey("default");
  }

  // ---- outils

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Facturation', 'Dr Facturation',"
            + " 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private void insertService(
      UUID practitionerId, String label, int priceCents, boolean isDefault, boolean archived) {
    jdbc.update(
        "insert into billing_service (id, tenant_id, practitioner_id, label, price_cents,"
            + " is_default, archived_at) values (?, ?, ?, ?, ?, ?, "
            + (archived ? "now()" : "null")
            + ")",
        UUID.randomUUID(),
        tenant,
        practitionerId,
        label,
        priceCents,
        isDefault);
  }

  private boolean isDefault(String id) {
    return jdbc.queryForObject(
        "select is_default from billing_service where id = ?", Boolean.class, UUID.fromString(id));
  }

  private int defaultsOf(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from billing_service where practitioner_id = ? and is_default"
            + " and archived_at is null",
        Integer.class,
        practitionerId);
  }

  private int activeOf(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from billing_service where practitioner_id = ? and archived_at is null",
        Integer.class,
        practitionerId);
  }

  private static String service(String label, int price, Integer durationMin, Boolean isDefault) {
    StringBuilder json =
        new StringBuilder("{\"label\":\"").append(label).append("\",\"price\":").append(price);
    if (durationMin != null) {
      json.append(",\"durationMin\":").append(durationMin);
    }
    if (isDefault != null) {
      json.append(",\"isDefault\":").append(isDefault);
    }
    return json.append('}').toString();
  }

  private static void expectFieldError(ResultActions result, String field, String code)
      throws Exception {
    result
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Facturation",
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

  private ResultActions patch(UUID who, String id, String body) throws Exception {
    return send(MockMvcRequestBuilders.patch(SERVICES + "/" + id), who, body);
  }

  private ResultActions delete(UUID who, String id) throws Exception {
    return send(MockMvcRequestBuilders.delete(SERVICES + "/" + id), who, null);
  }

  private ResultActions get(UUID who, String url) throws Exception {
    return send(MockMvcRequestBuilders.get(url), who, null);
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(
        result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
  }
}
