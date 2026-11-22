package com.psymate.identity.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** M14-02 : clés booking.* (Q75) de GET / PATCH /api/me/preferences. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Même configuration que PreferencesIT (abonné à PreferencesChanged) : contexte Spring partagé.
@Import({SharedPostgres.class, PreferencesIT.ListenerConfig.class})
class PreferencesBookingIT {

  private static final String MONDAY_MORNING = "{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\"}";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PractitionerFacade facade;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  private UUID insertPractitioner(String preferencesJson) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, preferences)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist',"
            + " cast(? as jsonb))",
        id,
        tenant,
        email,
        email,
        preferencesJson);
    return id;
  }

  private RequestPostProcessor as(UUID id) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test", "psychologist", "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions patchJson(UUID id, String body) throws Exception {
    return mvc.perform(
        patch("/api/me/preferences")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(as(id)));
  }

  private ResultActions getPrefs(UUID id) throws Exception {
    return mvc.perform(get("/api/me/preferences").with(as(id)));
  }

  private String stored(UUID id) {
    return jdbc.queryForObject(
        "select preferences::text from practitioner where id = ?", String.class, id);
  }

  private int publishedChanges(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where event_type like ? and serialized_event"
            + " like ?",
        Integer.class,
        "%PreferencesChanged",
        "%" + id + "%");
  }

  @Test
  void freshAccountReadsTheEightBookingDefaults() throws Exception {
    UUID id = insertPractitioner("{}");
    getPrefs(id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(21))
        .andExpect(jsonPath("$['booking.enabled']").value(false))
        .andExpect(jsonPath("$['booking.confirmation']").value("manual"))
        .andExpect(jsonPath("$['booking.windows']").isArray())
        .andExpect(jsonPath("$['booking.windows'].length()").value(0))
        .andExpect(jsonPath("$['booking.slotDurationMin']").value(60))
        .andExpect(jsonPath("$['booking.minNoticeHours']").value(24))
        .andExpect(jsonPath("$['booking.horizonDays']").value(28))
        .andExpect(jsonPath("$['booking.modalities']", contains("in_person")))
        .andExpect(jsonPath("$['booking.timezone']").value("Europe/Paris"))
        // Les clés existantes gardent leurs défauts.
        .andExpect(jsonPath("$.locale").value("fr"))
        .andExpect(jsonPath("$['agenda.workingDays']", contains(1, 2, 3, 4, 5, 6)))
        .andExpect(jsonPath("$['sessions.defaultDurationMin']").value(60));
  }

  @Test
  void enabledAutoAndOneWindowAreReadBackIdentically() throws Exception {
    UUID id = insertPractitioner("{}");
    patchJson(
            id,
            "{\"booking.enabled\":true,\"booking.confirmation\":\"auto\",\"booking.windows\":["
                + MONDAY_MORNING
                + "]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.enabled']").value(true))
        .andExpect(jsonPath("$['booking.confirmation']").value("auto"))
        .andExpect(jsonPath("$['booking.windows'].length()").value(1))
        .andExpect(jsonPath("$['booking.windows'][0].day").value(1))
        .andExpect(jsonPath("$['booking.windows'][0].start").value("09:00"))
        .andExpect(jsonPath("$['booking.windows'][0].end").value("12:00"));
    getPrefs(id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.enabled']").value(true))
        .andExpect(jsonPath("$['booking.confirmation']").value("auto"))
        .andExpect(jsonPath("$['booking.windows'].length()").value(1))
        .andExpect(jsonPath("$['booking.windows'][0].day").value(1))
        .andExpect(jsonPath("$['booking.windows'][0].start").value("09:00"))
        .andExpect(jsonPath("$['booking.windows'][0].end").value("12:00"));
    assertThat(facade.preferencesOf(id))
        .containsEntry("booking.enabled", true)
        .containsEntry("booking.confirmation", "auto")
        .containsEntry(
            "booking.windows", List.of(Map.of("day", 1, "start", "09:00", "end", "12:00")));
    String serialized =
        jdbc.queryForObject(
            "select serialized_event from event_publication where event_type like ? and"
                + " serialized_event like ?",
            String.class,
            "%PreferencesChanged",
            "%" + id + "%");
    assertThat(serialized)
        .contains("booking.enabled")
        .contains("booking.confirmation")
        .contains("booking.windows");
  }

  @Test
  void windowsSentOutOfOrderAreStoredAndReadSorted() throws Exception {
    UUID id = insertPractitioner("{}");
    patchJson(
            id,
            "{\"booking.windows\":["
                + "{\"day\":3,\"start\":\"14:00\",\"end\":\"18:00\"},"
                + "{\"end\":\"12:00\",\"start\":\"09:00\",\"day\":3},"
                + MONDAY_MORNING
                + "]}")
        .andExpect(status().isOk());
    getPrefs(id)
        .andExpect(jsonPath("$['booking.windows'][*].day", contains(1, 3, 3)))
        .andExpect(jsonPath("$['booking.windows'][*].start", contains("09:00", "09:00", "14:00")))
        .andExpect(jsonPath("$['booking.windows'][*].end", contains("12:00", "12:00", "18:00")));
    // Stockées triées, pas seulement relues triées.
    String raw = stored(id);
    assertThat(raw.indexOf("\"day\": 1")).isLessThan(raw.indexOf("\"day\": 3"));
    assertThat(raw.indexOf("\"09:00\"", raw.indexOf("\"day\": 3")))
        .isLessThan(raw.indexOf("\"14:00\""));
    // Les mêmes plages dans un autre ordre ne changent rien et ne publient rien de plus.
    int before = publishedChanges(id);
    assertThat(before).isEqualTo(1);
    patchJson(
            id,
            "{\"booking.windows\":["
                + MONDAY_MORNING
                + ",{\"day\":3,\"start\":\"09:00\",\"end\":\"12:00\"},"
                + "{\"day\":3,\"start\":\"14:00\",\"end\":\"18:00\"}]}")
        .andExpect(status().isOk());
    assertThat(publishedChanges(id)).isEqualTo(before);
  }

  @Test
  void contiguousWindowsAreAcceptedButOverlappingOnesRejected() throws Exception {
    UUID id = insertPractitioner("{}");
    patchJson(
            id,
            "{\"booking.windows\":["
                + MONDAY_MORNING
                + ",{\"day\":1,\"start\":\"11:00\",\"end\":\"13:00\"}]}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("booking.windows"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(stored(id)).isEqualTo("{}");
    patchJson(
            id,
            "{\"booking.windows\":["
                + MONDAY_MORNING
                + ",{\"day\":1,\"start\":\"12:00\",\"end\":\"14:00\"}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.windows'].length()").value(2))
        .andExpect(jsonPath("$['booking.windows'][1].start").value("12:00"))
        .andExpect(jsonPath("$['booking.windows'][1].end").value("14:00"));
  }

  @Test
  void enabledWithoutAnyWindowIsAccepted() throws Exception {
    UUID id = insertPractitioner("{}");
    patchJson(id, "{\"booking.enabled\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.enabled']").value(true))
        .andExpect(jsonPath("$['booking.windows'].length()").value(0));
  }

  @Test
  void otherBookingSettingsAreReadBack() throws Exception {
    UUID id = insertPractitioner("{}");
    patchJson(
            id,
            "{\"booking.slotDurationMin\":45,\"booking.minNoticeHours\":168,"
                + "\"booking.horizonDays\":7,\"booking.modalities\":[\"video\",\"phone\"],"
                + "\"booking.timezone\":\"America/Montreal\"}")
        .andExpect(status().isOk());
    getPrefs(id)
        .andExpect(jsonPath("$['booking.slotDurationMin']").value(45))
        .andExpect(jsonPath("$['booking.minNoticeHours']").value(168))
        .andExpect(jsonPath("$['booking.horizonDays']").value(7))
        .andExpect(jsonPath("$['booking.modalities']", contains("video", "phone")))
        .andExpect(jsonPath("$['booking.timezone']").value("America/Montreal"));
    patchJson(id, "{\"booking.timezone\":\"Europe/Paris\",\"booking.modalities\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.timezone']").value("Europe/Paris"))
        .andExpect(jsonPath("$['booking.modalities']", contains("in_person")));
  }

  static Stream<Arguments> faultyValues() {
    return Stream.of(
        Arguments.of("booking.enabled", "\"yes\"", "INVALID_VALUE"),
        Arguments.of("booking.confirmation", "\"always\"", "INVALID_VALUE"),
        Arguments.of("booking.windows", "{\"day\":1}", "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":0,\"start\":\"09:00\",\"end\":\"12:00\"}]",
            "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":8,\"start\":\"09:00\",\"end\":\"12:00\"}]",
            "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":1,\"start\":\"9:00\",\"end\":\"12:00\"}]",
            "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":1,\"start\":\"09:07\",\"end\":\"12:00\"}]",
            "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":1,\"start\":\"12:00\",\"end\":\"12:00\"}]",
            "INVALID_VALUE"),
        Arguments.of("booking.windows", "[{\"day\":1,\"start\":\"09:00\"}]", "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\",\"label\":\"matin\"}]",
            "INVALID_VALUE"),
        Arguments.of(
            "booking.windows",
            "[{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\"},"
                + "{\"day\":1,\"start\":\"11:00\",\"end\":\"13:00\"}]",
            "INVALID_VALUE"),
        Arguments.of("booking.windows", twentyTwoWindows(), "OUT_OF_RANGE"),
        Arguments.of("booking.slotDurationMin", "10", "OUT_OF_RANGE"),
        Arguments.of("booking.slotDurationMin", "185", "OUT_OF_RANGE"),
        Arguments.of("booking.slotDurationMin", "62", "OUT_OF_RANGE"),
        Arguments.of("booking.slotDurationMin", "\"60\"", "INVALID_VALUE"),
        Arguments.of("booking.minNoticeHours", "0", "OUT_OF_RANGE"),
        Arguments.of("booking.minNoticeHours", "169", "OUT_OF_RANGE"),
        Arguments.of("booking.horizonDays", "6", "OUT_OF_RANGE"),
        Arguments.of("booking.horizonDays", "91", "OUT_OF_RANGE"),
        Arguments.of("booking.horizonDays", "28.5", "INVALID_VALUE"),
        Arguments.of("booking.modalities", "[]", "INVALID_VALUE"),
        Arguments.of("booking.modalities", "[\"video\",\"video\"]", "INVALID_VALUE"),
        Arguments.of("booking.modalities", "[\"visio\"]", "INVALID_VALUE"),
        Arguments.of("booking.timezone", "\"Paris\"", "INVALID_VALUE"),
        Arguments.of("booking.timezone", "\"+02:00\"", "INVALID_VALUE"),
        Arguments.of("booking.timezone", "\"UTC+1\"", "INVALID_VALUE"));
  }

  private static String twentyTwoWindows() {
    StringBuilder json = new StringBuilder("[");
    for (int day = 1; day <= 7; day++) {
      for (String[] slot :
          new String[][] {{"08:00", "10:00"}, {"10:00", "12:00"}, {"14:00", "18:00"}}) {
        json.append("{\"day\":")
            .append(day)
            .append(",\"start\":\"")
            .append(slot[0])
            .append("\",\"end\":\"")
            .append(slot[1])
            .append("\"},");
      }
    }
    return json.append("{\"day\":1,\"start\":\"19:00\",\"end\":\"20:00\"}]").toString();
  }

  /**
   * Chaque règle : 422 avec le bon code sur la bonne clé, et aucune écriture partielle (les clés
   * valides du même PATCH ne sont pas enregistrées, aucun événement publié).
   */
  @ParameterizedTest(name = "{0} = {1} -> {2}")
  @MethodSource("faultyValues")
  void eachRuleRejectsWith422AndWritesNothing(String key, String json, String code)
      throws Exception {
    UUID id = insertPractitioner("{}");
    // Une clé booking.* valide et une clé d'affichage valide accompagnent la clé fautive.
    String validBookingKey =
        "booking.enabled".equals(key)
            ? "\"booking.confirmation\":\"auto\""
            : "\"booking.enabled\":true";
    patchJson(id, "{\"theme\":\"dark\"," + validBookingKey + ",\"" + key + "\":" + json + "}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value(key))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(stored(id)).isEqualTo("{}");
    assertThat(publishedChanges(id)).isZero();
    getPrefs(id)
        .andExpect(jsonPath("$.theme").value("light"))
        .andExpect(jsonPath("$['booking.enabled']").value(false))
        .andExpect(jsonPath("$['booking.confirmation']").value("manual"));
  }

  @Test
  void errorMessageIsLocalised() throws Exception {
    UUID id = insertPractitioner("{}");
    patchIn(id, "en")
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.errors[0].message").value("An integer between 7 and 90 is expected."));
    patchIn(id, "fr")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].message").value("Un entier entre 7 et 90 est attendu."));
  }

  /** PreferencesSchema documente les huit clés booking.* et la plage BookingWindow. */
  @Test
  void contractDocumentsTheEightBookingKeys() throws Exception {
    String p = "$.components.schemas.Preferences.properties";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(p + ".length()").value(21))
        .andExpect(jsonPath(p + "['booking.enabled'].type").value("boolean"))
        .andExpect(jsonPath(p + "['booking.confirmation'].enum", contains("manual", "auto")))
        .andExpect(jsonPath(p + "['booking.windows'].type").value("array"))
        .andExpect(jsonPath(p + "['booking.windows'].maxItems").value(21))
        .andExpect(jsonPath(p + "['booking.windows'].description").isNotEmpty())
        .andExpect(jsonPath(p + "['booking.windows'].example[0].start").value("09:00"))
        .andExpect(jsonPath(p + "['booking.modalities'].description").isNotEmpty())
        .andExpect(jsonPath(p + "['booking.modalities'].example[0]").value("in_person"))
        .andExpect(
            jsonPath(p + "['booking.windows'].items['$ref']")
                .value("#/components/schemas/BookingWindow"))
        .andExpect(jsonPath(p + "['booking.slotDurationMin'].minimum").value(15))
        .andExpect(jsonPath(p + "['booking.slotDurationMin'].maximum").value(180))
        .andExpect(jsonPath(p + "['booking.slotDurationMin'].multipleOf").value(5))
        .andExpect(jsonPath(p + "['booking.minNoticeHours'].maximum").value(168))
        .andExpect(jsonPath(p + "['booking.horizonDays'].minimum").value(7))
        .andExpect(jsonPath(p + "['booking.modalities'].type").value("array"))
        .andExpect(jsonPath(p + "['booking.modalities'].minItems").value(1))
        .andExpect(jsonPath(p + "['booking.modalities'].uniqueItems").value(true))
        .andExpect(
            jsonPath(
                p + "['booking.modalities'].items.enum", contains("in_person", "video", "phone")))
        .andExpect(jsonPath(p + "['booking.timezone'].type").value("string"))
        .andExpect(jsonPath("$.components.schemas.BookingWindow.properties.day.maximum").value(7))
        .andExpect(
            jsonPath("$.components.schemas.BookingWindow.properties.start.type").value("string"))
        .andExpect(
            jsonPath("$.components.schemas.BookingWindow.properties.end.type").value("string"));
  }

  private ResultActions patchIn(UUID id, String language) throws Exception {
    return mvc.perform(
        patch("/api/me/preferences")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Language", language)
            .content("{\"booking.horizonDays\":100}")
            .with(as(id)));
  }

  @Test
  void parisAndMontrealAreAcceptedTimezones() throws Exception {
    UUID id = insertPractitioner("{}");
    patchJson(id, "{\"booking.timezone\":\"America/Montreal\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.timezone']").value("America/Montreal"));
    patchJson(id, "{\"booking.timezone\":\"Europe/Paris\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.timezone']").value("Europe/Paris"));
    patchJson(id, "{\"booking.timezone\":\"Paris\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("booking.timezone"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    getPrefs(id).andExpect(jsonPath("$['booking.timezone']").value("Europe/Paris"));
  }

  @Test
  void invalidStoredBookingValuesFallBackToDefaults() throws Exception {
    UUID id =
        insertPractitioner(
            "{\"booking.windows\":[{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\"},"
                + "{\"day\":1,\"start\":\"11:00\",\"end\":\"13:00\"}],"
                + "\"booking.timezone\":\"Paris\",\"booking.slotDurationMin\":62,"
                + "\"booking.horizonDays\":30}");
    getPrefs(id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['booking.windows'].length()").value(0))
        .andExpect(jsonPath("$['booking.timezone']").value("Europe/Paris"))
        .andExpect(jsonPath("$['booking.slotDurationMin']").value(60))
        .andExpect(jsonPath("$['booking.horizonDays']").value(30));
  }
}
