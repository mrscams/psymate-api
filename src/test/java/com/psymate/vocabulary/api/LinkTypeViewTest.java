package com.psymate.vocabulary.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.i18n.ServedLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M16-03 (G Q98) : libellé d'un type de lien selon la langue servie. Anglais : le libellé anglais,
 * comme avant ; arabe : le libellé arabe, le français à défaut ; français : le français, comme
 * avant.
 */
class LinkTypeViewTest {

  private static LinkTypeView triggers(String labelAr, String inverseLabelAr) {
    return new LinkTypeView(
        "triggers",
        true,
        "déclenche",
        "triggers",
        labelAr,
        "déclenché par",
        "triggered by",
        inverseLabelAr,
        List.of("event"),
        List.of("feeling"),
        "solid",
        1);
  }

  @Test
  void englishAndFrenchAreUnchanged() {
    LinkTypeView type = triggers("يُثير", "يُثيره");
    assertThat(type.label(ServedLanguage.EN)).isEqualTo("triggers");
    assertThat(type.inverseLabel(ServedLanguage.EN)).isEqualTo("triggered by");
    assertThat(type.label(ServedLanguage.FR)).isEqualTo("déclenche");
    assertThat(type.inverseLabel(ServedLanguage.FR)).isEqualTo("déclenché par");
  }

  @Test
  void arabicIsServedWhenPresent() {
    LinkTypeView type = triggers("يُثير", "يُثيره");
    assertThat(type.label(ServedLanguage.AR)).isEqualTo("يُثير");
    assertThat(type.inverseLabel(ServedLanguage.AR)).isEqualTo("يُثيره");
  }

  @Test
  void arabicFallsBackToFrenchWhenMissingOrBlank() {
    assertThat(triggers(null, null).label(ServedLanguage.AR)).isEqualTo("déclenche");
    assertThat(triggers(null, null).inverseLabel(ServedLanguage.AR)).isEqualTo("déclenché par");
    assertThat(triggers(" ", "").label(ServedLanguage.AR)).isEqualTo("déclenche");
    assertThat(triggers(" ", "").inverseLabel(ServedLanguage.AR)).isEqualTo("déclenché par");
    // Seul l'inverse manque : le verbe direct reste en arabe, l'inverse passe en français.
    LinkTypeView half = triggers("يُثير", null);
    assertThat(half.label(ServedLanguage.AR)).isEqualTo("يُثير");
    assertThat(half.inverseLabel(ServedLanguage.AR)).isEqualTo("déclenché par");
  }

  @Test
  void anUndirectedTypeHasNoInverseInAnyLanguage() {
    LinkTypeView coOccurs =
        new LinkTypeView(
            "co_occurs",
            false,
            "survient avec",
            "co-occurs with",
            "يتزامن مع",
            null,
            null,
            null,
            List.of("note"),
            List.of("note"),
            "dotted",
            5);
    for (ServedLanguage language : ServedLanguage.values()) {
      assertThat(coOccurs.inverseLabel(language)).as(language.tag()).isNull();
    }
    assertThat(coOccurs.label(ServedLanguage.AR)).isEqualTo("يتزامن مع");
  }

  /** Le constructeur d'avant V17 laisse les deux libellés arabes nuls : le français est rendu. */
  @Test
  void theConstructorWithoutArabicLeavesBothArabicLabelsNull() {
    LinkTypeView type =
        new LinkTypeView(
            "follows",
            true,
            "fait suite à",
            "follows",
            "suivi de",
            "followed by",
            List.of("note"),
            List.of("note"),
            "thin",
            6);
    assertThat(type.labelAr()).isNull();
    assertThat(type.inverseLabelAr()).isNull();
    assertThat(type.label(ServedLanguage.AR)).isEqualTo("fait suite à");
    assertThat(type.inverseLabel(ServedLanguage.AR)).isEqualTo("suivi de");
  }
}
