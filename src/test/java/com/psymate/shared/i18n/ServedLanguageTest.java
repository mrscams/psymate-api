package com.psymate.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** M16-02 (G Q96, Q97, Q101, Q102) : langue servie, français par défaut. */
class ServedLanguageTest {

  private static final Locale ARABIC_LATIN_DIGITS = Locale.forLanguageTag("ar-u-nu-latn");

  // ---- règles remplacées, recopiées telles qu'elles étaient sur main (670a375)

  /** {@code ProblemMessages.localeOf} : intervalle de plus haute qualité commençant par « en ». */
  private static Locale oldProblemMessagesRule(String header) {
    if (header == null || header.isBlank()) {
      return Locale.FRENCH;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
      if (!ranges.isEmpty() && ranges.get(0).getRange().startsWith("en")) {
        return Locale.ENGLISH;
      }
    } catch (IllegalArgumentException e) {
      // français
    }
    return Locale.FRENCH;
  }

  /** {@code AuthController.preferredLocale} : langue de l'étiquette de cet intervalle. */
  private static Locale oldRegistrationRule(String header) {
    if (header == null || header.isBlank()) {
      return Locale.FRENCH;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
      return ranges.isEmpty()
          ? Locale.FRENCH
          : oldNormalize(Locale.forLanguageTag(ranges.get(0).getRange()));
    } catch (IllegalArgumentException e) {
      return Locale.FRENCH;
    }
  }

  /** {@code normalize} des classes de messages : « en » sinon français. */
  private static Locale oldNormalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  /** {@code AgendaMessages.localeOf} et ses semblables : en-tête vide, sinon getLocale(). */
  private static Locale oldRequestLocaleRule(MockHttpServletRequest request) {
    String header = request.getHeader("Accept-Language");
    return header == null || header.isBlank() ? Locale.FRENCH : oldNormalize(request.getLocale());
  }

  /** {@code BookingMailer.localeOf}, {@code ContactRequestService.localeOf} : « en » exactement. */
  private static Locale oldTagRule(String tag) {
    return "en".equals(tag) ? Locale.ENGLISH : Locale.FRENCH;
  }

  static List<String> headers() {
    List<String> h = new ArrayList<>();
    h.add(null);
    h.addAll(
        List.of(
            "",
            "   ",
            "fr",
            "fr-FR",
            "fr-CA,fr;q=0.9,en;q=0.8",
            "en",
            "EN",
            "en-US",
            "en-GB,fr;q=0.5",
            "fr;q=0.5,en",
            "en;q=0.1,fr;q=0.2",
            "de",
            "de-DE,en;q=0.9",
            "es,fr;q=0.8",
            "*",
            "*;q=0.5,en;q=0.4",
            "en-*",
            "eng",
            "i-default",
            "x-klingon",
            "zh-Hant-TW",
            "en;q=1.5",
            ";;;",
            "fr;q=abc",
            "pas une langue !",
            "en_US",
            "ar",
            "ar-MA,ar;q=0.9",
            "ar-SA,fr;q=0.8,en;q=0.5",
            "fr;q=0.5,ar",
            "AR-EG",
            "arn",
            "ar-*"));
    return h;
  }

  /** Avec les règles d'avant, l'arabe donnait le français : c'est la seule différence admise. */
  @ParameterizedTest
  @MethodSource("headers")
  void frenchAndEnglishAreResolvedExactlyAsBeforeForEveryHeader(String header) {
    assertThat(ServedLanguage.ofAcceptLanguage(header).arabicAsFrench().locale())
        .isEqualTo(oldProblemMessagesRule(header));
    assertThat(ServedLanguage.ofAcceptLanguageTag(header).arabicAsFrench().locale())
        .isEqualTo(oldRegistrationRule(header));

    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) {
      request.addHeader("Accept-Language", header);
    }
    assertThat(ServedLanguage.ofRequestLocale(request).arabicAsFrench().locale())
        .isEqualTo(oldRequestLocaleRule(request));
    assertThat(ServedLanguage.of(request.getLocale()).arabicAsFrench().locale())
        .isEqualTo(oldNormalize(request.getLocale()));
  }

  @ParameterizedTest
  @MethodSource("headers")
  void storedTagsAreResolvedExactlyAsBefore(String tag) {
    assertThat(ServedLanguage.ofTag(tag).arabicAsFrench().locale()).isEqualTo(oldTagRule(tag));
  }

  // ---- l'arabe

  @Test
  void arabicIsServedFromEverySource() {
    assertThat(ServedLanguage.ofAcceptLanguage("ar")).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.ofAcceptLanguage("ar-MA,ar;q=0.9")).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.ofAcceptLanguage("fr;q=0.5,ar")).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.ofAcceptLanguage("AR-EG")).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.ofAcceptLanguageTag("ar-SA,fr;q=0.8")).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.ofTag("ar")).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.of(Locale.forLanguageTag("ar-TN"))).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.of(ARABIC_LATIN_DIGITS)).isEqualTo(ServedLanguage.AR);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "ar-DZ,fr;q=0.5");
    assertThat(ServedLanguage.ofRequestLocale(request)).isEqualTo(ServedLanguage.AR);
    assertThat(ServedLanguage.ofRequest(request)).isEqualTo(ServedLanguage.AR);
  }

  @Test
  void unservedOrUnreadableSourcesGiveFrench() {
    for (String header : List.of("arn", "ara", "de", "", ";;;", "*")) {
      assertThat(ServedLanguage.ofAcceptLanguage(header)).as(header).isEqualTo(ServedLanguage.FR);
    }
    for (String tag : List.of("de", "AR", "ar-MA", "", "fr-FR")) {
      assertThat(ServedLanguage.ofTag(tag)).as(tag).isEqualTo(ServedLanguage.FR);
    }
    assertThat(ServedLanguage.ofTag(null)).isEqualTo(ServedLanguage.FR);
    assertThat(ServedLanguage.of(null)).isEqualTo(ServedLanguage.FR);
    assertThat(ServedLanguage.of(Locale.GERMAN)).isEqualTo(ServedLanguage.FR);
    assertThat(ServedLanguage.ofRequest(null)).isEqualTo(ServedLanguage.FR);
  }

  @Test
  void tagsAndLocales() {
    assertThat(ServedLanguage.FR.tag()).isEqualTo("fr");
    assertThat(ServedLanguage.EN.tag()).isEqualTo("en");
    assertThat(ServedLanguage.AR.tag()).isEqualTo("ar");
    assertThat(ServedLanguage.FR.locale()).isEqualTo(Locale.FRENCH);
    assertThat(ServedLanguage.EN.locale()).isEqualTo(Locale.ENGLISH);
    assertThat(ServedLanguage.AR.locale().toLanguageTag()).isEqualTo("ar-u-nu-latn");
    assertThat(ServedLanguage.AR.locale().getLanguage()).isEqualTo("ar");
  }

  /** G Q96 : l'arabe ne reçoit ses messages que si messages_ar.properties existe. */
  @Test
  void arabicMessagesOnlyWhenTheirFileExists() {
    assertThat(ServedLanguage.AR.messageLocale(false)).isEqualTo(Locale.FRENCH);
    assertThat(ServedLanguage.AR.messageLocale(true)).isEqualTo(ARABIC_LATIN_DIGITS);
    for (boolean present : new boolean[] {false, true}) {
      assertThat(ServedLanguage.FR.messageLocale(present)).isEqualTo(Locale.FRENCH);
      assertThat(ServedLanguage.EN.messageLocale(present)).isEqualTo(Locale.ENGLISH);
    }
    boolean present =
        ServedLanguage.class.getClassLoader().getResource("messages_ar.properties") != null;
    assertThat(ServedLanguage.AR.messageLocale())
        .isEqualTo(present ? ARABIC_LATIN_DIGITS : Locale.FRENCH);
    assertThat(ServedLanguage.messageLocaleOf(ARABIC_LATIN_DIGITS))
        .isEqualTo(ServedLanguage.AR.messageLocale());
    assertThat(ServedLanguage.messageLocaleOf(Locale.GERMAN)).isEqualTo(Locale.FRENCH);
    assertThat(ServedLanguage.messageLocaleOf(Locale.UK)).isEqualTo(Locale.ENGLISH);
  }

  /** G Q102 : résumé et export en français quand l'arabe est demandé. */
  @Test
  void arabicAsFrenchOnlyChangesArabic() {
    assertThat(ServedLanguage.AR.arabicAsFrench()).isEqualTo(ServedLanguage.FR);
    assertThat(ServedLanguage.FR.arabicAsFrench()).isEqualTo(ServedLanguage.FR);
    assertThat(ServedLanguage.EN.arabicAsFrench()).isEqualTo(ServedLanguage.EN);
  }
}
