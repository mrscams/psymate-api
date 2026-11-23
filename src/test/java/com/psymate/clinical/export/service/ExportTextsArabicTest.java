package com.psymate.clinical.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.i18n.ServedLanguage;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** G Q102 : l'export demandé en arabe sort en français, toujours, quels que soient les messages. */
class ExportTextsArabicTest {

  @Test
  void arabicExportTextsAreFrench() {
    ExportTexts french = ExportTexts.of(Locale.FRENCH);
    for (Locale arabic :
        List.of(
            ServedLanguage.AR.locale(),
            Locale.forLanguageTag("ar"),
            Locale.forLanguageTag("ar-SA"),
            ServedLanguage.AR.messageLocale())) {
      ExportTexts texts = ExportTexts.of(arabic);
      assertThat(texts.locale()).as(arabic.toLanguageTag()).isEqualTo(Locale.FRENCH);
      assertThat(texts.get("noSession")).isEqualTo(french.get("noSession"));
      assertThat(texts.appName()).isEqualTo("PsyMate");
    }
    assertThat(ExportTexts.of(Locale.ENGLISH).locale()).isEqualTo(Locale.ENGLISH);
  }
}
