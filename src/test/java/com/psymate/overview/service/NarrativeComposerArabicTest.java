package com.psymate.overview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.overview.dto.NarrativeDto;
import com.psymate.overview.model.NarrativeFacts;
import com.psymate.overview.model.NarrativeFacts.Dominant;
import com.psymate.overview.model.NarrativeFacts.FlagFact;
import com.psymate.overview.model.NarrativeFacts.MedicationFact;
import com.psymate.overview.model.NarrativeFacts.Trend;
import com.psymate.overview.model.NarrativeFacts.UncommentedEvent;
import com.psymate.shared.i18n.ServedLanguage;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** G Q102 : le résumé « En bref » demandé en arabe sort en français, toujours. */
class NarrativeComposerArabicTest {

  private final NarrativeComposer composer = new NarrativeComposer();

  /** Un dossier nourri : les cinq phrases et un point d'attention. */
  private static NarrativeFacts fullFacts() {
    return new NarrativeFacts(
        LocalDate.of(2026, 3, 3),
        12,
        14,
        Trend.of(7.5, 3.5, 8, 8),
        new Dominant("anxiety", "anxiété", "anxiety", 6),
        List.of("Conflit au travail", "Déménagement"),
        new UncommentedEvent("Dispute avec le frère", LocalDate.of(2026, 10, 24)),
        List.of(new MedicationFact("sertraline", "50 mg", LocalDate.of(2026, 9, 1))),
        List.of(new FlagFact("watch", "Sommeil", LocalDate.of(2026, 10, 1))));
  }

  @Test
  void arabicGivesTheFrenchNarrative() {
    NarrativeDto french = composer.compose(fullFacts(), Locale.FRENCH);
    assertThat(french.sentences()).hasSize(5);
    assertThat(french.attention()).isNotBlank();

    for (Locale arabic :
        List.of(
            ServedLanguage.AR.locale(),
            Locale.forLanguageTag("ar"),
            Locale.forLanguageTag("ar-MA"),
            ServedLanguage.AR.messageLocale())) {
      assertThat(composer.compose(fullFacts(), arabic))
          .as(arabic.toLanguageTag())
          .isEqualTo(french);
    }
    assertThat(String.join(" ", french.sentences())).contains("Suivi depuis le 3 mars 2026");
  }

  @Test
  void arabicResolvesToFrenchEvenWithArabicMessages() {
    // Indépendant de messages_ar.properties (M16-04) : la résolution elle-même rend le français.
    assertThat(NarrativeComposer.normalize(ServedLanguage.AR.locale())).isEqualTo(Locale.FRENCH);
    assertThat(NarrativeComposer.normalize(Locale.ENGLISH)).isEqualTo(Locale.ENGLISH);
    assertThat(NarrativeComposer.normalize(Locale.FRENCH)).isEqualTo(Locale.FRENCH);
  }
}
