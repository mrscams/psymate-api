package com.psymate.patients.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.patients.dto.CreatePatientProfileItemRequest;
import com.psymate.patients.dto.UpdatePatientProfileItemRequest;
import com.psymate.patients.model.PatientProfileCategory;
import com.psymate.patients.service.PatientProfileService.Fields;
import com.psymate.shared.problem.FieldProblem;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Règles de période (G Q66) et de changement de rubrique (J3), sans base. */
class PatientProfileServiceTest {

  private static final Fields CHILDHOOD =
      new Fields(PatientProfileCategory.history, "Enfance à Nantes", null, "Enfance", 1998);
  private static final Fields SISTER =
      new Fields(PatientProfileCategory.family, "Une sœur, Léa", "Très proches", null, null);

  private static Map<String, FieldProblem> problems() {
    return new LinkedHashMap<>();
  }

  private static UpdatePatientProfileItemRequest patch() {
    return new UpdatePatientProfileItemRequest();
  }

  // ---- période à la création

  @Test
  void aPeriodIsAcceptedInHistory() {
    Map<String, FieldProblem> problems = problems();
    PatientProfileService.checkPeriod(PatientProfileCategory.history, "Enfance", 1998, problems);
    assertThat(problems).isEmpty();
  }

  @Test
  void aPeriodOutsideHistoryIsInvalidValueOnEachSentField() {
    for (PatientProfileCategory category :
        new PatientProfileCategory[] {
          PatientProfileCategory.family,
          PatientProfileCategory.health,
          PatientProfileCategory.values_goals
        }) {
      Map<String, FieldProblem> both = problems();
      PatientProfileService.checkPeriod(category, "Enfance", 1998, both);
      assertThat(both.values())
          .as(category.name())
          .containsExactly(
              new FieldProblem("periodLabel", "INVALID_VALUE"),
              new FieldProblem("periodYear", "INVALID_VALUE"));

      Map<String, FieldProblem> yearOnly = problems();
      PatientProfileService.checkPeriod(category, null, 2002, yearOnly);
      assertThat(yearOnly.values())
          .containsExactly(new FieldProblem("periodYear", "INVALID_VALUE"));

      Map<String, FieldProblem> none = problems();
      PatientProfileService.checkPeriod(category, null, null, none);
      assertThat(none).isEmpty();
    }
  }

  @Test
  void anExistingErrorOnAPeriodFieldIsKept() {
    Map<String, FieldProblem> problems = problems();
    problems.put("periodYear", new FieldProblem("periodYear", "OUT_OF_RANGE"));
    PatientProfileService.checkPeriod(PatientProfileCategory.health, null, 3000, problems);
    assertThat(problems.values()).containsExactly(new FieldProblem("periodYear", "OUT_OF_RANGE"));
  }

  // ---- correction partielle

  @Test
  void anEmptyPatchKeepsEveryField() {
    Map<String, FieldProblem> problems = problems();
    assertThat(PatientProfileService.corrected(CHILDHOOD, patch(), problems)).isEqualTo(CHILDHOOD);
    assertThat(problems).isEmpty();
  }

  @Test
  void nullClearsDetailAndPeriodInHistory() {
    UpdatePatientProfileItemRequest r = patch();
    r.setPeriodLabel(null);
    r.setPeriodYear(null);
    r.setDetail(null);
    Map<String, FieldProblem> problems = problems();
    Fields f =
        PatientProfileService.corrected(
            new Fields(PatientProfileCategory.history, "T", "D", "Enfance", 1998), r, problems);
    assertThat(problems).isEmpty();
    assertThat(f).isEqualTo(new Fields(PatientProfileCategory.history, "T", null, null, null));
  }

  @Test
  void leavingHistoryClearsThePeriod() {
    UpdatePatientProfileItemRequest r = patch();
    r.setCategory("family");
    Map<String, FieldProblem> problems = problems();
    Fields f = PatientProfileService.corrected(CHILDHOOD, r, problems);
    assertThat(problems).isEmpty();
    assertThat(f)
        .isEqualTo(new Fields(PatientProfileCategory.family, "Enfance à Nantes", null, null, null));
  }

  @Test
  void leavingHistoryWithAnExplicitNullPeriodIsAccepted() {
    UpdatePatientProfileItemRequest r = patch();
    r.setCategory("values_goals");
    r.setPeriodLabel(null);
    r.setPeriodYear(null);
    Map<String, FieldProblem> problems = problems();
    Fields f = PatientProfileService.corrected(CHILDHOOD, r, problems);
    assertThat(problems).isEmpty();
    assertThat(f.periodLabel()).isNull();
    assertThat(f.periodYear()).isNull();
  }

  @Test
  void leavingHistoryWhileSendingAPeriodIsInvalidValue() {
    UpdatePatientProfileItemRequest r = patch();
    r.setCategory("health");
    r.setPeriodYear(2004);
    Map<String, FieldProblem> problems = problems();
    PatientProfileService.corrected(CHILDHOOD, r, problems);
    assertThat(problems.values()).containsExactly(new FieldProblem("periodYear", "INVALID_VALUE"));
  }

  @Test
  void aPeriodSentToANonHistoryItemIsInvalidValue() {
    UpdatePatientProfileItemRequest r = patch();
    r.setPeriodLabel("Vers 2000");
    Map<String, FieldProblem> problems = problems();
    PatientProfileService.corrected(SISTER, r, problems);
    assertThat(problems.values()).containsExactly(new FieldProblem("periodLabel", "INVALID_VALUE"));
  }

  @Test
  void enteringHistoryAcceptsAPeriod() {
    UpdatePatientProfileItemRequest r = patch();
    r.setCategory("history");
    r.setPeriodLabel("2002 – 2009");
    r.setPeriodYear(2002);
    Map<String, FieldProblem> problems = problems();
    Fields f = PatientProfileService.corrected(SISTER, r, problems);
    assertThat(problems).isEmpty();
    assertThat(f)
        .isEqualTo(
            new Fields(
                PatientProfileCategory.history,
                "Une sœur, Léa",
                "Très proches",
                "2002 – 2009",
                2002));
  }

  @Test
  void titleAndCategoryCannotBeCleared() {
    UpdatePatientProfileItemRequest r = patch();
    r.setCategory(null);
    r.setTitle("   ");
    Map<String, FieldProblem> problems = problems();
    Fields f = PatientProfileService.corrected(SISTER, r, problems);
    assertThat(problems.values())
        .containsExactly(
            new FieldProblem("category", "REQUIRED"), new FieldProblem("title", "REQUIRED"));
    assertThat(f.category()).isEqualTo(PatientProfileCategory.family);
  }

  @Test
  void anUnknownCategoryIsInvalidValueAndKeepsTheCurrentOne() {
    UpdatePatientProfileItemRequest r = patch();
    r.setCategory("resources");
    Map<String, FieldProblem> problems = problems();
    Fields f = PatientProfileService.corrected(CHILDHOOD, r, problems);
    assertThat(problems.values()).containsExactly(new FieldProblem("category", "INVALID_VALUE"));
    assertThat(f.category()).isEqualTo(PatientProfileCategory.history);
  }

  // ---- normalisation et codes

  @Test
  void edgeSpacesAreStrippedAndBlankOptionalsAreNull() {
    CreatePatientProfileItemRequest r =
        new CreatePatientProfileItemRequest(" history ", "  Souvenir  ", "  ", " Enfance ", null);
    assertThat(r.category()).isEqualTo("history");
    assertThat(r.title()).isEqualTo("Souvenir");
    assertThat(r.detail()).isNull();
    assertThat(r.periodLabel()).isEqualTo("Enfance");

    UpdatePatientProfileItemRequest u = patch();
    u.setDetail(" ");
    assertThat(u.detailProvided()).isTrue();
    assertThat(u.getDetail()).isNull();
  }

  @Test
  void jakartaConstraintsMapToTheCodesOfTheContract() {
    assertThat(PatientProfileService.codeOf("NotBlank")).isEqualTo("REQUIRED");
    assertThat(PatientProfileService.codeOf("NotNull")).isEqualTo("REQUIRED");
    assertThat(PatientProfileService.codeOf("Size")).isEqualTo("OUT_OF_RANGE");
    assertThat(PatientProfileService.codeOf("Min")).isEqualTo("OUT_OF_RANGE");
    assertThat(PatientProfileService.codeOf("Max")).isEqualTo("OUT_OF_RANGE");
    assertThat(PatientProfileService.codeOf("Pattern")).isEqualTo("INVALID_VALUE");
  }
}
