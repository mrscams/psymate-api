package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** GET /api/patients/{id}/timeline/summary (M3-01) : agrégats par mois ou par année. */
class TimelineSummaryIT extends TimelineWebSupport {

  /** Exclut la séance ouverte par le test (maintenant). */
  private static final String PAST = "from=2000-01-01T00:00:00Z&to=2026-01-01T00:00:00Z";

  private ResultActions summary(String query) throws Exception {
    return get(summaryUrl(query));
  }

  @Test
  void monthSummaryCountsByTypeAndAveragesIntensity() throws Exception {
    feeling("2026-09-03T10:00:00Z", "anxiety", 4);
    feeling("2026-09-20T10:00:00Z", "sadness", 8);
    note("2026-09-21T10:00:00Z");
    event("2026-09-22T10:00:00Z", "Voyage", null);
    note("2026-07-01T00:00:00Z");

    summary("granularity=month&from=2026-01-01T00:00:00Z&to=2026-10-01T00:00:00Z")
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.granularity").value("month"))
        .andExpect(jsonPath("$.periods", hasSize(2)))
        .andExpect(jsonPath("$.periods[0].period").value("2026-07"))
        .andExpect(jsonPath("$.periods[0].from").value("2026-07-01T00:00:00Z"))
        .andExpect(jsonPath("$.periods[0].counts.note").value(1))
        .andExpect(jsonPath("$.periods[0].counts.length()").value(1))
        .andExpect(jsonPath("$.periods[0].feelingCount").value(0))
        .andExpect(jsonPath("$.periods[0].averageIntensity").value(nullValue()))
        .andExpect(jsonPath("$.periods[1].period").value("2026-09"))
        .andExpect(jsonPath("$.periods[1].from").value("2026-09-01T00:00:00Z"))
        .andExpect(jsonPath("$.periods[1].counts.feeling").value(2))
        .andExpect(jsonPath("$.periods[1].counts.note").value(1))
        .andExpect(jsonPath("$.periods[1].counts.event").value(1))
        .andExpect(jsonPath("$.periods[1].feelingCount").value(2))
        .andExpect(jsonPath("$.periods[1].averageIntensity").value(6.0));
  }

  @Test
  void yearSummaryGroupsByCalendarYear() throws Exception {
    feeling("2022-12-31T23:59:59Z", "anxiety", 2);
    feeling("2023-01-01T00:00:00Z", "anxiety", 5);
    feeling("2023-06-15T12:00:00Z", "joy", 9);
    note("2024-02-29T08:00:00Z");

    summary("granularity=year&" + PAST)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("year"))
        .andExpect(jsonPath("$.periods", hasSize(3)))
        .andExpect(jsonPath("$.periods[0].period").value("2022"))
        .andExpect(jsonPath("$.periods[0].from").value("2022-01-01T00:00:00Z"))
        .andExpect(jsonPath("$.periods[0].counts.feeling").value(1))
        .andExpect(jsonPath("$.periods[0].averageIntensity").value(2.0))
        .andExpect(jsonPath("$.periods[1].period").value("2023"))
        .andExpect(jsonPath("$.periods[1].counts.feeling").value(2))
        .andExpect(jsonPath("$.periods[1].averageIntensity").value(7.0))
        .andExpect(jsonPath("$.periods[2].period").value("2024"))
        .andExpect(jsonPath("$.periods[2].counts.note").value(1))
        .andExpect(jsonPath("$.periods[2].feelingCount").value(0))
        .andExpect(jsonPath("$.periods[2].averageIntensity").value(nullValue()));
  }

  @Test
  void averageIsExactOverTwoFeelings() throws Exception {
    feeling("2024-03-05T10:00:00Z", "anxiety", 4);
    feeling("2024-03-25T10:00:00Z", "anxiety", 8);

    summary("granularity=month&" + PAST)
        .andExpect(jsonPath("$.periods", hasSize(1)))
        .andExpect(jsonPath("$.periods[0].period").value("2024-03"))
        .andExpect(jsonPath("$.periods[0].averageIntensity").value(6.0))
        .andExpect(jsonPath("$.periods[0].feelingCount").value(2));
  }

  @Test
  void emptyPeriodsAreOmittedAndPeriodsAreAscending() throws Exception {
    note("2024-11-10T10:00:00Z");
    note("2023-01-10T10:00:00Z");
    note("2024-01-10T10:00:00Z");

    ResultActions result = summary("granularity=month&" + PAST).andExpect(status().isOk());

    assertThat(read(result, "$.periods[*].period"))
        .containsExactly("2023-01", "2024-01", "2024-11");
  }

  @Test
  void fromIsInclusiveAndToIsExclusive() throws Exception {
    note("2024-02-29T23:59:59Z");
    note("2024-03-01T00:00:00Z");
    note("2024-03-31T23:59:59Z");
    note("2024-04-01T00:00:00Z");

    ResultActions result =
        summary("granularity=month&from=2024-03-01T00:00:00Z&to=2024-04-01T00:00:00Z")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.periods", hasSize(1)))
            .andExpect(jsonPath("$.periods[0].period").value("2024-03"))
            .andExpect(jsonPath("$.periods[0].counts.note").value(2));
    assertThat(read(result, "$.periods[*].period")).containsExactly("2024-03");
  }

  @Test
  void draftsAreCountedButArchivedAndPrivateAreNot() throws Exception {
    note("2024-03-01T10:00:00Z");
    note("2024-03-02T10:00:00Z", "\"status\":\"draft\"");
    String archived = note("2024-03-03T10:00:00Z");
    note("2024-03-04T10:00:00Z", "\"visibility\":\"private\"");
    delete("/api/entries/" + archived).andExpect(status().isNoContent());

    summary("granularity=month&" + PAST).andExpect(jsonPath("$.periods[0].counts.note").value(2));
    summary("granularity=month&includePrivate=false&" + PAST)
        .andExpect(jsonPath("$.periods[0].counts.note").value(2));
  }

  @Test
  void includePrivateAddsPrivateEntriesAndTheirFeelings() throws Exception {
    feeling("2024-03-01T10:00:00Z", "anxiety", 2);
    feeling("2024-03-02T10:00:00Z", "anxiety", 10, "\"visibility\":\"private\"");

    summary("granularity=month&" + PAST)
        .andExpect(jsonPath("$.periods[0].counts.feeling").value(1))
        .andExpect(jsonPath("$.periods[0].averageIntensity").value(2.0));
    summary("granularity=month&includePrivate=true&" + PAST)
        .andExpect(jsonPath("$.periods[0].counts.feeling").value(2))
        .andExpect(jsonPath("$.periods[0].feelingCount").value(2))
        .andExpect(jsonPath("$.periods[0].averageIntensity").value(6.0));
  }

  @Test
  void patientWithoutEntriesGivesNoPeriods() throws Exception {
    summary("granularity=year&" + PAST)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periods", hasSize(0)));
  }

  @Test
  void monthBoundariesAreUtc() throws Exception {
    note("2024-03-31T23:30:00Z");
    note("2024-04-01T00:30:00Z");

    ResultActions result = summary("granularity=month&" + PAST).andExpect(status().isOk());

    assertThat(read(result, "$.periods[*].period")).containsExactly("2024-03", "2024-04");
  }

  // ---- erreurs

  private void expectInvalid(String query, String field, String code) throws Exception {
    summary(query)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  @Test
  void unknownGranularityIs422() throws Exception {
    expectInvalid("granularity=week", "granularity", "INVALID_VALUE");
    expectInvalid("granularity=MONTH", "granularity", "INVALID_VALUE");
  }

  @Test
  void missingGranularityIs422() throws Exception {
    expectInvalid("", "granularity", "INVALID_VALUE");
    expectInvalid(PAST, "granularity", "INVALID_VALUE");
  }

  @Test
  void fromAfterToAndNonIsoDatesAre422() throws Exception {
    expectInvalid(
        "granularity=month&from=2024-05-01T00:00:00Z&to=2024-04-01T00:00:00Z",
        "from",
        "INVALID_TIME");
    expectInvalid("granularity=month&from=hier", "from", "INVALID_FORMAT");
  }

  @Test
  void anotherPractitionerGetsForbidden() throws Exception {
    UUID other = fx.newPractitioner();

    send(MockMvcRequestBuilders.get(summaryUrl("granularity=month")), null, as(other))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
  }
}
