package com.psymate.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.i18n.ServedLanguage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** M16-05 (G Q100, Q101) : rendu de droite à gauche d'un e-mail en arabe. */
class RightToLeftMailTest {

  private static final String RLM = String.valueOf(RightToLeftMail.RLM);

  private static final Pattern ARABIC_INDIC = Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]");

  /** Corps du HTML, entre l'ouverture du conteneur et sa fermeture. */
  private static String body(String text) {
    String html = RightToLeftMail.html(text);
    int start = html.indexOf("\">\n", html.indexOf("<div dir=\"rtl\"")) + 3;
    return html.substring(start, html.indexOf("</div>"));
  }

  // ---- en-tête du document

  @Test
  void theDocumentIsArabicAndRightToLeft() {
    String html = RightToLeftMail.html("مرحبًا،");
    assertThat(html)
        .startsWith("<!DOCTYPE html>\n<html lang=\"ar\" dir=\"rtl\">\n")
        .contains("<meta charset=\"UTF-8\">")
        .contains("<body dir=\"rtl\">")
        .contains("<div dir=\"rtl\" lang=\"ar\"")
        .endsWith("</div>\n</body>\n</html>\n");
  }

  // ---- échappement

  @Test
  void lessThanGreaterThanAndAmpersandAreEscaped() {
    String b = body("رسالة: <script>alert(\"x\") & 'y'</script> 3 < 4 > 2");
    assertThat(b)
        .doesNotContain("<script")
        .doesNotContain("</script>")
        .contains("&lt;<span dir=\"ltr\">script</span>&gt;")
        .contains("&quot;")
        .contains("&amp;")
        .contains("&#39;")
        .contains("<span dir=\"ltr\">3</span> &lt; <span dir=\"ltr\">4</span> &gt;");
    assertThat(RightToLeftMail.escape("<a href=\"x\">&'"))
        .isEqualTo("&lt;a href=&quot;x&quot;&gt;&amp;&#39;");
  }

  @Test
  void anInjectedTagCannotBreakOutOfALink() {
    String b = body("https://example.test/a\"onmouseover=\"alert(1)<b>");
    assertThat(b)
        .contains("<a href=\"https://example.test/a\" dir=\"ltr\">https://example.test/a</a>")
        .contains("&quot;")
        .doesNotContain("<b>")
        .doesNotContain("\"onmouseover");
  }

  // ---- liens

  @Test
  void urlsBecomeClickableLeftToRightLinks() {
    String b = body("يُرجى فتح هذا الرابط:\nhttps://app.psymate.test/rendez-vous/confirmer/Ab_c-9");
    assertThat(b)
        .contains(
            "<a href=\"https://app.psymate.test/rendez-vous/confirmer/Ab_c-9\" dir=\"ltr\">"
                + "https://app.psymate.test/rendez-vous/confirmer/Ab_c-9</a>");
  }

  @Test
  void aQueryStringIsEscapedInTheLinkAndTrailingPunctuationStaysOutside() {
    String b =
        body("الرابط http://localhost:5173/reset-password?token=a&b=c، ثم (https://x.test/p).");
    assertThat(b)
        .contains(
            "<a href=\"http://localhost:5173/reset-password?token=a&amp;b=c\" dir=\"ltr\">"
                + "http://localhost:5173/reset-password?token=a&amp;b=c</a>،")
        .contains("(<a href=\"https://x.test/p\" dir=\"ltr\">https://x.test/p</a>).");
  }

  @Test
  void otherSchemesAreNotLinked() {
    String b = body("javascript:alert(1) ftp://x.test mailto:a@b.test");
    assertThat(b).doesNotContain("<a ");
  }

  @Test
  void latinTextBeforeALinkDoesNotSwallowIt() {
    String b = body("voir https://x.test/a");
    assertThat(b)
        .isEqualTo(
            "<p><span dir=\"ltr\">voir</span> <a href=\"https://x.test/a\" dir=\"ltr\">"
                + "https://x.test/a</a></p>\n");
  }

  // ---- adresses, codes, dates, texte latin

  @Test
  void emailAddressesAreIsolated() {
    String b = body("يُرجى مراسلة الدعم على العنوان support@psymate.test، للمساعدة");
    assertThat(b).contains("<span dir=\"ltr\">support@psymate.test</span>،");
  }

  @Test
  void datesTimesZonesAndDurationsAreIsolatedWithLatinDigits() {
    String b =
        body(
            "التاريخ: الاثنين 11 مارس 2030 الساعة 09:00 (المنطقة الزمنية Europe/Paris)\n"
                + "المدّة (بالدقائق): 50");
    assertThat(b)
        .contains("الاثنين <span dir=\"ltr\">11</span> مارس <span dir=\"ltr\">2030</span>")
        .contains("الساعة <span dir=\"ltr\">09:00</span> (")
        .contains("<span dir=\"ltr\">Europe/Paris</span>)")
        .contains("<br>\n")
        .contains("<span dir=\"ltr\">50</span>");
    assertThat(ARABIC_INDIC.matcher(b).find()).isFalse();
  }

  @Test
  void codesAndLatinNamesAreIsolatedAsOneRun() {
    String b =
        body(
            "صاحب الطلب: Sara Benali\nالرمز: AB12-CD34\nتم تقديم ملف على PsyMate: Élodie Faure (psychologist).");
    assertThat(b)
        .contains("صاحب الطلب: <span dir=\"ltr\">Sara Benali</span><br>")
        .contains("<span dir=\"ltr\">AB12-CD34</span>")
        .contains("على <span dir=\"ltr\">PsyMate: Élodie Faure</span> (")
        .contains("(<span dir=\"ltr\">psychologist</span>).");
  }

  @Test
  void mixedArabicAndLatinKeepsTheArabicOutsideTheIsolates() {
    String b = body("PsyMate ليس خدمة طوارئ.");
    assertThat(b).isEqualTo("<p><span dir=\"ltr\">PsyMate</span> ليس خدمة طوارئ.</p>\n");
  }

  // ---- sauts de ligne

  @Test
  void blankLinesMakeParagraphsAndLineBreaksBecomeBr() {
    String b = body("مرحبًا،\n\nسطر أول\nسطر ثانٍ\n\n\nفقرة أخيرة\n");
    assertThat(b).isEqualTo("<p>مرحبًا،</p>\n<p>سطر أول<br>\nسطر ثانٍ</p>\n<p>فقرة أخيرة</p>\n");
    assertThat(body("سطر\r\nسطر")).isEqualTo("<p>سطر<br>\nسطر</p>\n");
    assertThat(body("")).isEmpty();
    assertThat(body(null)).isEmpty();
  }

  // ---- texte brut

  @Test
  void plainTextMarksArabicLinesThatStartWithLatinOrADigit() {
    String text =
        "مرحبًا،\n\nPsyMate ليس خدمة طوارئ.\n30 يومًا\n- Dr Martin — تاريخ التقديم: 23/11/2026\n"
            + "https://app.psymate.test/rendez-vous/gerer/abc\nBonjour, voici mon message.\n«PsyMate» هو\n";
    String plain = RightToLeftMail.plainText(text);
    assertThat(plain.split("\n", -1))
        .containsExactly(
            "مرحبًا،",
            "",
            RLM + "PsyMate ليس خدمة طوارئ.",
            RLM + "30 يومًا",
            RLM + "- Dr Martin — تاريخ التقديم: 23/11/2026",
            "https://app.psymate.test/rendez-vous/gerer/abc",
            "Bonjour, voici mon message.",
            RLM + "«PsyMate» هو",
            "");
    assertThat(plain.replace(RLM, "")).isEqualTo(text);
  }

  @Test
  void firstStrongCharacterDecidesTheLineDirection() {
    assertThat(RightToLeftMail.startsLeftToRight("(PsyMate)")).isTrue();
    assertThat(RightToLeftMail.startsLeftToRight("  42")).isTrue();
    assertThat(RightToLeftMail.startsLeftToRight("«مرحبًا» PsyMate")).isFalse();
    assertThat(RightToLeftMail.startsLeftToRight("- : ")).isFalse();
  }

  // ---- langue

  @Test
  void onlyArabicIsRenderedRightToLeft() {
    assertThat(RightToLeftMail.applies(ServedLanguage.AR.locale())).isTrue();
    assertThat(RightToLeftMail.applies(Locale.of("ar"))).isTrue();
    assertThat(RightToLeftMail.applies(Locale.of("ar", "MA"))).isTrue();
    assertThat(RightToLeftMail.applies(ServedLanguage.FR.locale())).isFalse();
    assertThat(RightToLeftMail.applies(ServedLanguage.EN.locale())).isFalse();
    assertThat(RightToLeftMail.applies(Locale.of("de"))).isFalse();
    assertThat(RightToLeftMail.applies(null)).isFalse();
  }

  // ---- message MIME

  @Test
  void theBodyIsMultipartAlternativePlainThenHtmlInUtf8() throws Exception {
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    message.setSubject("تأكيد طلب الموعد", "UTF-8");
    RightToLeftMail.setBody(message, "مرحبًا،\n\nhttps://x.test/a\n\nPsyMate ليس خدمة طوارئ.\n");
    message.saveChanges();

    assertThat(message.getContentType()).startsWith("multipart/alternative");
    assertThat(message.getHeader("Content-Language", null)).isEqualTo("ar");
    MimeMultipart parts = (MimeMultipart) message.getContent();
    assertThat(parts.getCount()).isEqualTo(2);
    MimeBodyPart plain = (MimeBodyPart) parts.getBodyPart(0);
    MimeBodyPart html = (MimeBodyPart) parts.getBodyPart(1);
    assertThat(plain.getContentType()).isEqualToIgnoringCase("text/plain; charset=UTF-8");
    assertThat(html.getContentType()).isEqualToIgnoringCase("text/html; charset=UTF-8");
    assertThat((String) plain.getContent()).contains(RLM + "PsyMate ليس خدمة طوارئ.");
    assertThat((String) html.getContent())
        .contains("<html lang=\"ar\" dir=\"rtl\">")
        .contains("<a href=\"https://x.test/a\" dir=\"ltr\">");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    message.writeTo(out);
    assertThat(out.toString(StandardCharsets.US_ASCII))
        .contains("Content-Type: multipart/alternative");
  }

  // ---- un vrai texte arabe de l'application, chiffres latins

  @Test
  void aRealArabicBookingEmailKeepsLatinDigitsAndGregorianDates() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    Locale ar = ServedLanguage.AR.messageLocale();
    String date =
        DateTimeFormatter.ofPattern(messages.getMessage("booking.mail.datePattern", null, ar), ar)
            .format(Instant.parse("2030-03-11T08:00:00Z").atZone(ZoneId.of("Europe/Paris")));
    String details =
        messages.getMessage(
            "booking.mail.details",
            new Object[] {date, "09:00", "Europe/Paris", "1234", "عبر الفيديو"},
            ar);
    String text =
        messages.getMessage(
            "booking.mail.verify.body",
            new Object[] {
              "Claire Lambert", details, "30", "https://x.test/rendez-vous/confirmer/t", "PsyMate"
            },
            ar);

    String html = RightToLeftMail.html(text);
    String plain = RightToLeftMail.plainText(text);
    assertThat(date).contains("2030").contains("11");
    assertThat(html)
        .contains("<span dir=\"ltr\">2030</span>")
        .contains("<span dir=\"ltr\">09:00</span>")
        .contains("<span dir=\"ltr\">1234</span>")
        .contains("<span dir=\"ltr\">Claire Lambert</span>")
        .contains("<a href=\"https://x.test/rendez-vous/confirmer/t\" dir=\"ltr\">");
    assertThat(ARABIC_INDIC.matcher(html).find()).isFalse();
    assertThat(ARABIC_INDIC.matcher(plain).find()).isFalse();
    assertThat(plain).contains("\n" + RLM + "PsyMate ليس خدمة طوارئ.");
  }
}
