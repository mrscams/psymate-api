package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fusions du vocabulaire de bout en bout (M9-03, revue ARCH-01) : après la fusion de deux émotions
 * et de deux tags, les tables des modules propriétaires ({@code entry_feeling}, {@code entry_tag}
 * de clinical, {@code patient_tag} de patients), {@code patient.clinical_rev}, la recherche et la
 * vue d'ensemble doivent refléter la cible. Les entrées sont créées par l'API de clinical, comme en
 * production, pour que l'index de recherche et la vue d'ensemble soient calculés par leurs abonnés
 * avant la fusion.
 *
 * <p>État au 2 novembre 2026 (partie 1/4) : les tables sont réécrites en SQL natif par vocabulary,
 * mais sans événement ni décalage de {@code clinical_rev} ; la recherche et la vue d'ensemble
 * restent périmées. Les assertions attendues sont en commentaire « ATTENDU », remplacées par une
 * assertion « ÉTAT ACTUEL » qui sera inversée à la partie 3 (abonnés des modules propriétaires).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VocabularyTestConfig.class)
class VocabularyMergeIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private RequestPostProcessor auth;
  private UUID tenant;
  private UUID me;
  private UUID patient;

  @BeforeEach
  void setUp() throws Exception {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    me = UUID.randomUUID();
    String email = me + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        me,
        tenant,
        email,
        email);
    patient = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Patient')",
        patient,
        tenant,
        me);
    CurrentPractitioner principal =
        new CurrentPractitioner(me, tenant, email, "Dr Test", "psychologist", "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    send(
            MockMvcRequestBuilders.post("/api/patients/{id}/sessions/start", patient),
            Map.of("modality", "in_person"))
        .andExpect(status().isCreated());
  }

  @Test
  void mergingEmotionsAndTagsReachesOwnerTablesSearchAndOverview() throws Exception {
    String lassitude = createEmotion("Lassitude", "sadness");
    UUID boulot = createTag("boulot");
    UUID travail = createTag("travail");
    UUID feeling1 = entry("feeling", "Soirée calme à la maison", 1, List.of(), lassitude);
    UUID feeling2 = entry("feeling", "Réveil difficile ce matin", 2, List.of(), lassitude);
    UUID noteSource = entry("note", "Échange sur la semaine", 3, List.of("boulot"), null);
    UUID noteBoth = entry("note", "Retour sur les vacances", 4, List.of("boulot", "travail"), null);
    send(
            MockMvcRequestBuilders.put("/api/patients/{id}/tags", patient),
            Map.of("tags", List.of("boulot")))
        .andExpect(status().isOk());
    awaitPatientEventsSettled();

    // Avant la fusion : index et vue d'ensemble calculés sur les libellés d'origine.
    assertThat(found("lassitude")).containsExactlyInAnyOrder(feeling1, feeling2);
    assertThat(found("tristesse")).isEmpty();
    assertThat(found("boulot")).containsExactlyInAnyOrder(noteSource, noteBoth);
    assertThat(found("travail")).containsExactly(noteBoth);
    assertThat(topEmotionCodes()).containsExactly(lassitude);
    long revBefore = clinicalRev();

    String merge =
        body(
            send(
                    MockMvcRequestBuilders.post("/api/me/emotions/{code}/merge", lassitude),
                    Map.of("targetCode", "sadness"))
                .andExpect(status().isOk()));
    assertThat((Integer) JsonPath.read(merge, "$.affectedCount")).isEqualTo(2);
    send(
            MockMvcRequestBuilders.post("/api/tags/{id}/merge", boulot),
            Map.of("targetId", travail.toString()))
        .andExpect(status().isOk());

    // Tables des modules propriétaires : réécrites (aujourd'hui en SQL natif par vocabulary).
    assertThat(emotionOf(feeling1)).isEqualTo(List.of("sadness", lassitude));
    assertThat(emotionOf(feeling2)).isEqualTo(List.of("sadness", lassitude));
    assertThat(tagsOf(noteSource)).containsExactly(travail);
    assertThat(tagsOf(noteBoth)).containsExactly(travail);
    assertThat(
            jdbc.queryForList(
                "select tag_id from patient_tag where patient_id = ?", UUID.class, patient))
        .containsExactly(travail);
    assertThat(patientTagLabels()).containsExactly("travail");

    // ATTENDU (partie 3) : une écriture clinique décale clinical_rev.
    // assertThat(clinicalRev()).isGreaterThan(revBefore);
    // ÉTAT ACTUEL : la fusion ne touche pas clinical_rev.
    assertThat(clinicalRev()).isEqualTo(revBefore);

    // ATTENDU (partie 3) : le vecteur suit les nouveaux libellés.
    // assertThat(found("tristesse")).containsExactlyInAnyOrder(feeling1, feeling2);
    // assertThat(found("lassitude")).isEmpty();
    // assertThat(found("travail")).containsExactlyInAnyOrder(noteSource, noteBoth);
    // assertThat(found("boulot")).isEmpty();
    // ÉTAT ACTUEL : search_vector n'est pas recalculé, la recherche sert les anciens libellés.
    assertThat(found("tristesse")).isEmpty();
    assertThat(found("lassitude")).containsExactlyInAnyOrder(feeling1, feeling2);
    assertThat(found("travail")).containsExactly(noteBoth);
    assertThat(found("boulot")).containsExactlyInAnyOrder(noteSource, noteBoth);

    // ATTENDU (partie 3) : l'émotion dominante est la cible.
    // assertThat(topEmotionCodes()).containsExactly("sadness");
    // ÉTAT ACTUEL : la projection, à source_rev = clinical_rev, est servie telle quelle.
    assertThat(topEmotionCodes()).containsExactly(lassitude);
  }

  @Test
  void undoingAnEmotionMergeRestoresFeelingsSearchAndOverview() throws Exception {
    String lassitude = createEmotion("Lassitude", "sadness");
    UUID feeling = entry("feeling", "Soirée calme à la maison", 1, List.of(), lassitude);
    awaitPatientEventsSettled();
    long revBefore = clinicalRev();

    String merge =
        body(
            send(
                    MockMvcRequestBuilders.post("/api/me/emotions/{code}/merge", lassitude),
                    Map.of("targetCode", "sadness"))
                .andExpect(status().isOk()));
    send(
            MockMvcRequestBuilders.post(
                "/api/me/emotions/merges/{id}/undo", (String) JsonPath.read(merge, "$.id")),
            Map.of())
        .andExpect(status().isOk());

    assertThat(emotionOf(feeling)).isEqualTo(Arrays.asList(lassitude, null));
    // ATTENDU (partie 3) : la fusion puis son annulation décalent clinical_rev.
    // assertThat(clinicalRev()).isGreaterThan(revBefore);
    // ÉTAT ACTUEL : ni la fusion ni l'annulation ne touchent clinical_rev.
    assertThat(clinicalRev()).isEqualTo(revBefore);
    // Correct aujourd'hui par accident (rien n'a été recalculé depuis la création) ; doit le
    // rester quand les abonnés recalculeront après la fusion puis après l'annulation.
    assertThat(found("lassitude")).containsExactly(feeling);
    assertThat(found("tristesse")).isEmpty();
    assertThat(topEmotionCodes()).containsExactly(lassitude);
  }

  // ---- données

  private String createEmotion(String label, String family) throws Exception {
    return JsonPath.read(
        body(
            send(
                    MockMvcRequestBuilders.post("/api/me/emotions"),
                    Map.of("labelFr", label, "family", family))
                .andExpect(status().isCreated())),
        "$.code");
  }

  private UUID createTag(String label) throws Exception {
    return UUID.fromString(
        JsonPath.read(
            body(
                send(MockMvcRequestBuilders.post("/api/tags"), Map.of("label", label))
                    .andExpect(status().isCreated())),
            "$.id"));
  }

  /** Crée une entrée par l'API de clinical ; {@code emotionCode} non nul pour un ressenti. */
  private UUID entry(String type, String body, int daysAgo, List<String> tags, String emotionCode)
      throws Exception {
    Instant from = Instant.now().minus(daysAgo, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", type);
    payload.put("body", body);
    payload.put("occurred", Map.of("from", from.toString(), "precision", "exact"));
    payload.put("visibility", "clinical");
    payload.put("tags", tags);
    if (emotionCode != null) {
      payload.put("feeling", Map.of("emotionCode", emotionCode, "intensity", 6));
    }
    return UUID.fromString(
        JsonPath.read(
            body(
                send(MockMvcRequestBuilders.post("/api/patients/{id}/entries", patient), payload)
                    .andExpect(status().isCreated())),
            "$.id"));
  }

  /**
   * Attend que toutes les publications qui citent le patient (création des entrées, tags du
   * patient) soient traitées par leurs abonnés : index de recherche et vue d'ensemble calculés.
   */
  private void awaitPatientEventsSettled() {
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                jdbc.queryForObject(
                        "select count(*) from event_publication where serialized_event like ?"
                            + " and completion_date is null",
                        Integer.class,
                        "%" + patient + "%")
                    == 0);
  }

  // ---- lectures

  private List<UUID> found(String q) throws Exception {
    MockHttpServletRequestBuilder get =
        MockMvcRequestBuilders.get("/api/patients/{id}/search", patient).queryParam("q", q);
    DocumentContext doc =
        JsonPath.parse(body(mvc.perform(get.with(auth)).andExpect(status().isOk())));
    List<String> ids = doc.read("$.items[*].entryId");
    return ids.stream().map(UUID::fromString).toList();
  }

  private List<String> topEmotionCodes() throws Exception {
    DocumentContext doc =
        JsonPath.parse(
            body(
                mvc.perform(
                        MockMvcRequestBuilders.get("/api/patients/{id}/overview", patient)
                            .with(auth))
                    .andExpect(status().isOk())));
    return doc.read("$.emotions.top[*].code");
  }

  private List<String> patientTagLabels() throws Exception {
    DocumentContext doc =
        JsonPath.parse(
            body(
                mvc.perform(MockMvcRequestBuilders.get("/api/patients/{id}", patient).with(auth))
                    .andExpect(status().isOk())));
    return doc.read("$.tags[*].label");
  }

  /** [emotion_code, original_emotion_code] du ressenti de l'entrée. */
  private List<String> emotionOf(UUID entryId) {
    return jdbc.queryForObject(
        "select emotion_code, original_emotion_code from entry_feeling where entry_id = ?",
        (rs, i) -> Arrays.asList(rs.getString(1), rs.getString(2)),
        entryId);
  }

  private List<UUID> tagsOf(UUID entryId) {
    return jdbc.queryForList(
        "select tag_id from entry_tag where entry_id = ?", UUID.class, entryId);
  }

  private long clinicalRev() {
    return jdbc.queryForObject(
        "select clinical_rev from patient where id = ?", Long.class, patient);
  }

  // ---- HTTP

  private ResultActions send(MockHttpServletRequestBuilder builder, Object json) throws Exception {
    builder
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(json).getBytes(StandardCharsets.UTF_8));
    return mvc.perform(builder.with(auth));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
