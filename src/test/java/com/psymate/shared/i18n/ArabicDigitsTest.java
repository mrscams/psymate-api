package com.psymate.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * G Q101 : en arabe, dates et nombres mis en forme par le serveur en chiffres latins et en
 * calendrier grégorien. La locale servie est {@code ar-u-nu-latn} ; avec {@code ar} seule, {@code
 * MessageFormat} écrirait des chiffres arabo-indiens (témoin de chaque test).
 */
class ArabicDigitsTest {

  /** Chiffres arabo-indiens (U+0660 à U+0669) et arabo-indiens étendus (U+06F0 à U+06F9). */
  private static final Pattern ARABIC_INDIC = Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]");

  private static final Locale SERVED = ServedLanguage.AR.locale();
  private static final Locale PLAIN_ARABIC = Locale.forLanguageTag("ar");

  private static boolean arabicIndic(String s) {
    return ARABIC_INDIC.matcher(s).find();
  }

  private static ResourceBundleMessageSource source() {
    // Réglages des sources de l'application (basename de test, fichier ar seulement).
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("i18n/digits");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    s.setAlwaysUseMessageFormat(true);
    return s;
  }

  @Test
  void aMessageWithANumberOrAChoiceHasLatinDigits() {
    String number = new MessageFormat("{0,number}", SERVED).format(new Object[] {1234567});
    String plain = new MessageFormat("{0}", SERVED).format(new Object[] {1234});
    String choice =
        new MessageFormat("{0,choice,0#none|1#one|1<{0,number,integer} items}", SERVED)
            .format(new Object[] {2500});
    assertThat(number).isEqualTo("1,234,567").matches(s -> !arabicIndic(s));
    assertThat(plain).isEqualTo("1,234").matches(s -> !arabicIndic(s));
    assertThat(choice).isEqualTo("2,500 items").matches(s -> !arabicIndic(s));

    // Témoin : avec « ar » seule, des chiffres arabo-indiens.
    assertThat(new MessageFormat("{0,number}", PLAIN_ARABIC).format(new Object[] {1234567}))
        .matches(ArabicDigitsTest::arabicIndic);
  }

  /**
   * Par une source de messages réglée comme celles des modules : fichier ar trouvé, chiffres
   * latins.
   */
  @Test
  void theMessageSourceFindsTheArabicFileAndKeepsLatinDigits() {
    ResourceBundleMessageSource s = source();
    String choice = s.getMessage("digits.choice", new Object[] {12}, SERVED);
    String number = s.getMessage("digits.number", new Object[] {1234.5}, SERVED);
    String plain = s.getMessage("digits.plain", new Object[] {98765}, SERVED);
    assertThat(choice).isEqualTo("12 items");
    assertThat(number).isEqualTo("n = 1,234.5");
    assertThat(plain).isEqualTo("98,765");
    assertThat(choice + number + plain).matches(t -> !arabicIndic(t));

    assertThat(s.getMessage("digits.number", new Object[] {1234.5}, PLAIN_ARABIC))
        .matches(ArabicDigitsTest::arabicIndic);
  }

  @Test
  void aDateHasLatinDigitsAndTheGregorianCalendar() {
    ZonedDateTime at = ZonedDateTime.of(2026, 11, 23, 9, 5, 0, 0, ZoneId.of("Europe/Paris"));
    for (FormatStyle style : FormatStyle.values()) {
      String date = DateTimeFormatter.ofLocalizedDate(style).withLocale(SERVED).format(at);
      assertThat(date).as(style.name()).matches(s -> !arabicIndic(s)).contains("23");
      assertThat(date).as(style.name()).contains("2026");
    }
    // Motif des e-mails de prise de rendez-vous (booking.mail.datePattern) et heure.
    String pattern = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", SERVED).format(at);
    assertThat(pattern).matches(s -> !arabicIndic(s)).contains("23").contains("2026", "09:05");
    // Mois d'un export (« LLLL yyyy »), et une date seule.
    assertThat(DateTimeFormatter.ofPattern("LLLL yyyy", SERVED).format(LocalDate.of(2026, 1, 1)))
        .matches(s -> !arabicIndic(s))
        .contains("2026");
    // Calendrier grégorien : l'année n'est pas celle de l'hégire (1448).
    assertThat(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(SERVED).format(at))
        .doesNotContain("1448");
    // Témoin : DateTimeFormatter garde DecimalStyle.STANDARD même avec « ar » seule.
    assertThat(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(PLAIN_ARABIC).format(at))
        .matches(s -> !arabicIndic(s));
  }
}
