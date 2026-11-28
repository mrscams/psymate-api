package com.psymate.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.billing.dto.BillingProfileRequest;
import com.psymate.billing.dto.CreateBillingServiceRequest;
import com.psymate.shared.problem.FieldProblem;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Forme des corps de la facturation (G Q84 : centimes entiers ; L5 : champs inconnus refusés). */
class BillingBodyAdviceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static List<String> problems(Class<?> type, String body) {
    JsonNode root = JSON.readTree(body);
    return BillingBodyAdvice.problems(root, BillingBodyAdvice.FIELDS.get(type)).stream()
        .map(p -> p.field() + ":" + p.code())
        .toList();
  }

  @Test
  void integerAmountsPass() {
    assertThat(
            problems(
                CreateBillingServiceRequest.class,
                "{\"label\":\"Séance\",\"price\":6000,\"durationMin\":50,\"isDefault\":true}"))
        .isEmpty();
  }

  @Test
  void decimalStringOrOversizedAmountsAreRefused() {
    assertThat(problems(CreateBillingServiceRequest.class, "{\"price\":60.5}"))
        .containsExactly("price:INVALID_FORMAT");
    assertThat(problems(CreateBillingServiceRequest.class, "{\"price\":6000.0}"))
        .containsExactly("price:INVALID_FORMAT");
    assertThat(problems(CreateBillingServiceRequest.class, "{\"price\":\"6000\"}"))
        .containsExactly("price:INVALID_FORMAT");
    assertThat(problems(CreateBillingServiceRequest.class, "{\"price\":3000000000}"))
        .containsExactly("price:OUT_OF_RANGE");
    assertThat(problems(BillingProfileRequest.class, "{\"rate\":55.5}"))
        .containsExactly("rate:INVALID_FORMAT");
  }

  @Test
  void nullPassesItsRuleBelongsToTheRequest() {
    assertThat(problems(BillingProfileRequest.class, "{\"rate\":null,\"addressLines\":null}"))
        .isEmpty();
  }

  @Test
  void unknownAndServerFieldsAreRefusedInBodyOrder() {
    assertThat(
            problems(
                CreateBillingServiceRequest.class,
                "{\"practitionerId\":\"x\",\"label\":\"A\",\"price\":1,\"archivedAt\":null}"))
        .containsExactly("practitionerId:UNKNOWN_FIELD", "archivedAt:UNKNOWN_FIELD");
    assertThat(problems(BillingProfileRequest.class, "{\"patientId\":\"x\"}"))
        .containsExactly("patientId:UNKNOWN_FIELD");
  }

  @Test
  void booleansAndTextsKeepTheirJsonType() {
    assertThat(problems(CreateBillingServiceRequest.class, "{\"isDefault\":\"true\"}"))
        .containsExactly("isDefault:INVALID_FORMAT");
    assertThat(problems(CreateBillingServiceRequest.class, "{\"label\":12}"))
        .containsExactly("label:INVALID_FORMAT");
  }

  @Test
  void problemCarriesFieldAndCode() {
    FieldProblem p =
        BillingBodyAdvice.problems(
                JSON.readTree("{\"foo\":1}"),
                BillingBodyAdvice.FIELDS.get(CreateBillingServiceRequest.class))
            .getFirst();
    assertThat(p.field()).isEqualTo("foo");
    assertThat(p.code()).isEqualTo("UNKNOWN_FIELD");
  }
}
