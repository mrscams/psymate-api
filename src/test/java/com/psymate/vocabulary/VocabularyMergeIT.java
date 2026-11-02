package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.vocabulary.api.EmotionMergeReverted;
import com.psymate.vocabulary.api.EmotionsMerged;
import com.psymate.vocabulary.api.TagsMerged;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
 * <p>État au 2 novembre 2026 (partie 2/4) : vocabulary publie {@code EmotionsMerged}, {@code
 * EmotionMergeReverted} et {@code TagsMerged}, sans abonné encore ; les tables sont toujours
 * réécrites en SQL natif par vocabulary, sans décalage de {@code clinical_rev} ; la recherche et la
 * vue d'ensemble restent périmées. Les assertions attendues sont en commentaire « ATTENDU »,
 * remplacées par une assertion « ÉTAT ACTUEL » qui sera inversée à la partie 3 (abonnés des modules
 * propriétaires). Elles sont regroupées dans {@code assertFollowsMerge} et {@code
 * assertEmotionServed}, déjà exécutées une fois après {@code simulateOwnerSubscribers} pour prouver
 * qu'elles sont atteignables.
 *
 * <p>La vue d'ensemble suit l'horloge réelle : si minuit UTC tombait entre deux lectures, la
 * projection serait recalculée et une assertion « ÉTAT ACTUEL » échouerait (risque accepté,
 * quelques secondes par jour ; l'horloge fixe d'overview est privée à son package).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VocabularyTestConfig.class)
@RecordApplicationEvents
class VocabularyMergeIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;

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
            Map.of("tags", List.of("boulot", "travail")))
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
    String tagMerge =
        body(
            send(
                    MockMvcRequestBuilders.post("/api/tags/{id}/merge", boulot),
                    Map.of("targetId", travail.toString()))
                .andExpect(status().isOk()));
    assertThat((String) JsonPath.read(tagMerge, "$.label")).isEqualTo("travail");

    // Tables des modules propriétaires : réécrites (aujourd'hui en SQL natif par vocabulary),
    // doublons compris (noteBoth et le patient portaient déjà la cible).
    assertThat(emotionOf(feeling1)).isEqualTo(List.of("sadness", lassitude));
    assertThat(emotionOf(feeling2)).isEqualTo(List.of("sadness", lassitude));
    assertThat(tagsOf(noteSource)).containsExactly(travail);
    assertThat(tagsOf(noteBoth)).containsExactly(travail);
    assertThat(
            jdbc.queryForList(
                "select tag_id from patient_tag where patient_id = ?", UUID.class, patient))
        .containsExactly(travail);
    assertThat(patientTagLabels()).containsExactly("travail");

    // ATTENDU (partie 3) : la propagation devient asynchrone. Les événements de vocabulary ne
    // citent pas le patient : attendre chaque ATTENDU par await().untilAsserted(...), jamais par
    // awaitPatientEventsSettled() seul, y compris pour les assertions négatives.
    //   await().untilAsserted(() -> assertThat(clinicalRev()).isGreaterThan(revBefore));
    //   await().untilAsserted(() -> assertFollowsMerge(feeling1, feeling2, noteSource, noteBoth));
    // ÉTAT ACTUEL : la fusion ne touche pas clinical_rev ; search_vector n'est pas recalculé et
    // la vue d'ensemble, à source_rev = clinical_rev, est servie telle quelle.
    assertThat(clinicalRev()).isEqualTo(revBefore);
    assertThat(found("tristesse")).isEmpty();
    assertThat(found("lassitude")).containsExactlyInAnyOrder(feeling1, feeling2);
    assertThat(found("travail")).containsExactly(noteBoth);
    assertThat(found("boulot")).containsExactlyInAnyOrder(noteSource, noteBoth);
    assertThat(topEmotionCodes()).containsExactly(lassitude);

    // Faisabilité de l'ATTENDU (à retirer à la partie 3) : ce que feront les abonnés, simulé en
    // SQL (recalcul des vecteurs, décalage de clinical_rev), suffit à le satisfaire.
    simulateOwnerSubscribers();
    assertFollowsMerge(feeling1, feeling2, noteSource, noteBoth);
  }

  @Test
  void undoingAnEmotionMergeRestoresTheOwnerTable() throws Exception {
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
    assertThat(emotionOf(feeling)).isEqualTo(List.of("sadness", lassitude));
    // ATTENDU (partie 3) : la fusion est propagée avant l'annulation.
    //   await().untilAsserted(() -> assertThat(clinicalRev()).isGreaterThan(revBefore));
    //   await().untilAsserted(() -> assertEmotionServed("tristesse", "lassitude", "sadness",
    //       feeling));
    // ÉTAT ACTUEL : rien n'est recalculé après la fusion.
    assertThat(clinicalRev()).isEqualTo(revBefore);
    assertEmotionServed("lassitude", "tristesse", lassitude, feeling);
    long revAfterMerge = clinicalRev();

    send(
            MockMvcRequestBuilders.post(
                "/api/me/emotions/merges/{id}/undo", (String) JsonPath.read(merge, "$.id")),
            Map.of())
        .andExpect(status().isOk());

    assertThat(emotionOf(feeling)).isEqualTo(Arrays.asList(lassitude, null));
    // ATTENDU (partie 3) : l'annulation décale clinical_rev à son tour, et un recalcul tardif de
    // la fusion n'écrase pas celui de l'annulation.
    //   await().untilAsserted(() -> assertThat(clinicalRev()).isGreaterThan(revAfterMerge));
    //   await().untilAsserted(() -> assertEmotionServed("lassitude", "tristesse", lassitude,
    //       feeling));
    // ÉTAT ACTUEL : l'annulation ne touche pas clinical_rev ; recherche et vue d'ensemble sont
    // justes par accident, car rien n'a été recalculé depuis la création de l'entrée.
    assertThat(clinicalRev()).isEqualTo(revAfterMerge);
    assertEmotionServed("lassitude", "tristesse", lassitude, feeling);
  }

  // ---- événements (partie 2/4)

  @Test
  void anEmotionMergeAndItsUndoPublishTheirEvents() throws Exception {
    String lassitude = createEmotion("Lassitude", "sadness");
    String merge =
        body(
            send(
                    MockMvcRequestBuilders.post("/api/me/emotions/{code}/merge", lassitude),
                    Map.of("targetCode", "sadness"))
                .andExpect(status().isOk()));
    UUID mergeId = UUID.fromString(JsonPath.read(merge, "$.id"));

    assertThat(events.stream(EmotionsMerged.class))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getTenantId()).isEqualTo(tenant);
              assertThat(e.getMergeId()).isEqualTo(mergeId);
              assertThat(e.getPractitionerId()).isEqualTo(me);
              assertThat(e.getSourceCode()).isEqualTo(lassitude);
              assertThat(e.getTargetCode()).isEqualTo("sadness");
            });
    assertThat(events.stream(EmotionMergeReverted.class)).isEmpty();

    send(MockMvcRequestBuilders.post("/api/me/emotions/merges/{id}/undo", mergeId), Map.of())
        .andExpect(status().isOk());

    assertThat(events.stream(EmotionMergeReverted.class))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getTenantId()).isEqualTo(tenant);
              assertThat(e.getMergeId()).isEqualTo(mergeId);
              assertThat(e.getPractitionerId()).isEqualTo(me);
              assertThat(e.getSourceCode()).isEqualTo(lassitude);
              assertThat(e.getTargetCode()).isEqualTo("sadness");
            });
    assertThat(events.stream(EmotionsMerged.class)).hasSize(1);
  }

  @Test
  void aTagMergePublishesTagsMerged() throws Exception {
    UUID boulot = createTag("boulot");
    UUID travail = createTag("travail");
    send(
            MockMvcRequestBuilders.post("/api/tags/{id}/merge", boulot),
            Map.of("targetId", travail.toString()))
        .andExpect(status().isOk());

    assertThat(events.stream(TagsMerged.class))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getTenantId()).isEqualTo(tenant);
              assertThat(e.getPractitionerId()).isEqualTo(me);
              assertThat(e.getSourceTagId()).isEqualTo(boulot);
              assertThat(e.getTargetTagId()).isEqualTo(travail);
            });
  }

  @Test
  void refusedMergesAndUndosPublishNothing() throws Exception {
    String lassitude = createEmotion("Lassitude", "sadness");
    UUID boulot = createTag("boulot");
    send(
            MockMvcRequestBuilders.post("/api/me/emotions/{code}/merge", lassitude),
            Map.of("targetCode", "inexistante"))
        .andExpect(status().isNotFound());
    send(
            MockMvcRequestBuilders.post("/api/tags/{id}/merge", boulot),
            Map.of("targetId", boulot.toString()))
        .andExpect(status().is4xxClientError());
    String merge =
        body(
            send(
                    MockMvcRequestBuilders.post("/api/me/emotions/{code}/merge", lassitude),
                    Map.of("targetCode", "sadness"))
                .andExpect(status().isOk()));
    String mergeId = JsonPath.read(merge, "$.id");
    jdbc.update(
        "update emotion_merge set undo_until = now() - interval '1 second' where id = ?::uuid",
        mergeId);
    send(MockMvcRequestBuilders.post("/api/me/emotions/merges/{id}/undo", mergeId), Map.of())
        .andExpect(status().isConflict());

    assertThat(events.stream(EmotionsMerged.class)).hasSize(1);
    assertThat(events.stream(EmotionMergeReverted.class)).isEmpty();
    assertThat(events.stream(TagsMerged.class)).isEmpty();
  }

  // ---- attendus

  /** Recherche et vue d'ensemble suivent les cibles des deux fusions du premier test. */
  private void assertFollowsMerge(UUID feeling1, UUID feeling2, UUID noteSource, UUID noteBoth)
      throws Exception {
    assertEmotionServed("tristesse", "lassitude", "sadness", feeling1, feeling2);
    assertThat(found("travail")).containsExactlyInAnyOrder(noteSource, noteBoth);
    assertThat(found("boulot")).isEmpty();
  }

  /**
   * La recherche trouve les ressentis par {@code shown} et plus par {@code gone} ; l'émotion
   * dominante de la vue d'ensemble est {@code topCode}.
   */
  private void assertEmotionServed(String shown, String gone, String topCode, UUID... feelings)
      throws Exception {
    assertThat(found(shown)).containsExactlyInAnyOrder(feelings);
    assertThat(found(gone)).isEmpty();
    assertThat(topEmotionCodes()).containsExactly(topCode);
  }

  /**
   * Ce que feront les abonnés des modules propriétaires (partie 3) : recalcul du vecteur de
   * recherche des entrées du patient et décalage de {@code clinical_rev}, qui périme la vue
   * d'ensemble.
   */
  private void simulateOwnerSubscribers() {
    jdbc.queryForList(
        "select entry_search_vector_refresh(id) from entry where patient_id = ?", patient);
    jdbc.update("update patient set clinical_rev = clinical_rev + 1 where id = ?", patient);
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
    await("publications du patient traitées")
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
