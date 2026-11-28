package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.psymate.testsupport.SharedPostgres;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Coherence du contrat : le fichier contract/openapi.yaml commite doit etre identique au contrat
 * genere par l'application. Avec -Dcontract.write=true (make contract), le fichier est reecrit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class ContractIT {

  private static final Path CONTRACT = Path.of("contract", "openapi.yaml");

  @Autowired private MockMvc mockMvc;

  @Value("${app.contract.version}")
  private String contractVersion;

  @Test
  void contractFileMatchesGeneratedContract() throws Exception {
    String expected = generatedContract();

    if (Boolean.getBoolean("contract.write")) {
      Files.createDirectories(CONTRACT.getParent());
      Files.writeString(CONTRACT, expected, StandardCharsets.UTF_8);
      return;
    }

    assertThat(CONTRACT)
        .as("contract/openapi.yaml n'est pas à jour : lance `make contract` et commite le résultat")
        .exists();
    String actual = Files.readString(CONTRACT, StandardCharsets.UTF_8).replace("\r\n", "\n");
    assertThat(actual)
        .as("contract/openapi.yaml n'est pas à jour : lance `make contract` et commite le résultat")
        .isEqualTo(expected);
  }

  @Test
  void contractExposesExpectedRoutesAndVersion() throws Exception {
    Map<String, Object> doc = new Yaml().load(generatedContract());
    @SuppressWarnings("unchecked")
    Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
    assertThat(paths.keySet())
        .contains(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/me",
            "/api/vocabulary/emotions",
            "/api/tags",
            "/api/me/emotions",
            "/api/me/billing-services",
            "/api/me/billing-services/{id}",
            "/api/patients/{id}/billing-profile",
            "/api/me/billing-settings",
            "/api/me/billing-settings/iban",
            "/api/billing/billable-sessions",
            "/api/billing/billable-sessions/dismissed",
            "/api/billing/billable-sessions/{sessionEntryId}",
            "/api/billing/billable-sessions/{sessionEntryId}/dismiss",
            "/api/billing/billable-sessions/{sessionEntryId}/restore",
            "/api/invoices",
            "/api/invoices/batch",
            "/api/invoices/{id}");
    @SuppressWarnings("unchecked")
    Map<String, Object> info = (Map<String, Object>) doc.get("info");
    assertThat(info.get("version")).isEqualTo(contractVersion);
    // Plan des tags D6-A4 : v0.11.0 = sessions actives (M10-06) ; 0.11.1 = preferences agenda.*
    // (M11-04) ; 0.12.0 = rendez-vous et lectures de l'agenda (M11-05, M11-06) ; 0.12.1 = séries
    // de rendez-vous (M11-07) ; 0.13.0 = rendez-vous invité et rattachement (M11-10) ; 0.14.0 =
    // Profil 360 (M13-02) ; 0.15.0 = prise de rendez-vous en ligne (M14, premier pas M14-04 :
    // origin, guest.email, GET /api/agenda/requests) ; 0.16.0 = version arabe (M16, premier pas
    // M16-02 : locale = ar dans les préférences et la demande de rendez-vous en ligne) ; 0.17.0 =
    // facturation (M15, premier pas M15-05 : prestations et profil de facturation du patient).
    assertThat(contractVersion).isEqualTo("0.17.0");
    assertThat(info.get("title")).isEqualTo("PsyMate API");
  }

  /** Contrat genere, normalise : cles triees, listes dans l'ordre, LF, un seul \n final. */
  private String generatedContract() throws Exception {
    String raw =
        mockMvc
            .perform(get("/v3/api-docs.yaml"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    Object parsed = new Yaml().load(raw);
    assertThat(parsed).as("réponse /v3/api-docs.yaml").isInstanceOf(Map.class);

    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setIndent(2);
    options.setIndicatorIndent(0);
    options.setWidth(Integer.MAX_VALUE);
    options.setSplitLines(false);
    options.setLineBreak(DumperOptions.LineBreak.UNIX);
    StringWriter out = new StringWriter();
    new Yaml(options).dump(sort(parsed), out);
    return out.toString().stripTrailing() + "\n";
  }

  private static Object sort(Object node) {
    if (node instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new TreeMap<>();
      map.forEach((k, v) -> sorted.put(String.valueOf(k), sort(v)));
      return sorted;
    }
    if (node instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      list.forEach(v -> copy.add(sort(v)));
      return copy;
    }
    return node;
  }
}
