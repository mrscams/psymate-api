package com.psymate.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.i18n.ServedLanguage;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M16-05 (G Q100, Q101) : lecture d'un e-mail dans Mailpit ({@link SharedMailpit}) avec son source
 * brut, et les vérifications communes aux e-mails en arabe et aux e-mails en français ou en
 * anglais. Chaque test n'interroge que ses propres destinataires.
 */
public final class RightToLeftMails {

  /** Marque de droite à gauche (U+200F), posée par le texte brut arabe. */
  public static final String RLM = Character.toString(0x200F);

  /** Marque de gauche à droite (U+200E). */
  public static final String LRM = Character.toString(0x200E);

  /** Lettres arabes (hors chiffres). */
  private static final Pattern ARABIC_LETTER = Pattern.compile("[\\u0621-\\u064A]");

  /** Chiffres arabo-indiens (U+0660 à U+0669) et arabo-indiens étendus (U+06F0 à U+06F9). */
  public static final Pattern ARABIC_INDIC_DIGIT =
      Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]");

  /** Lien cliquable isolé de gauche à droite dans le HTML. */
  private static final Pattern LTR_LINK =
      Pattern.compile("<a href=\"(https?://[^\"]+)\" dir=\"ltr\">");

  private static final ResourceBundleMessageSource MESSAGES = messages();

  private final GenericContainer<?> mailpit = SharedMailpit.container();
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * Un e-mail reçu.
   *
   * @param subject objet décodé
   * @param text corps texte décodé
   * @param html corps HTML décodé (vide sans partie HTML)
   * @param contentType en-tête {@code Content-Type} du message (premier niveau)
   * @param raw source brut
   */
  public record Mail(String subject, String text, String html, String contentType, String raw) {}

  private static ResourceBundleMessageSource messages() {
    // Réglages des sources de l'application.
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    return s;
  }

  /** Texte d'une clé dans la locale servie de cette langue (objet attendu d'un e-mail). */
  public static String message(ServedLanguage language, String key, Object... args) {
    return MESSAGES.getMessage(key, args, language.messageLocale());
  }

  /** Texte arabe d'une clé. */
  public static String arabic(String key, Object... args) {
    return message(ServedLanguage.AR, key, args);
  }

  /** Locale servie de l'arabe, {@code ar-u-nu-latn}. */
  public static Locale arabicLocale() {
    return ServedLanguage.AR.locale();
  }

  /** Adresse de destinataire unique. */
  public static String recipient(String prefix) {
    return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 12) + "@example.test";
  }

  private String get(String path) throws Exception {
    URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
    return http.send(
            HttpRequest.newBuilder(uri).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        .body();
  }

  /** Premier message de ce destinataire (30 s au plus) ; null sinon. */
  public Mail await(String to) throws Exception {
    return await(to, null);
  }

  /**
   * Premier message de ce destinataire dont l'objet vaut exactement {@code subject} (tout objet si
   * null), 30 s au plus ; null sinon.
   */
  public Mail await(String to, String subject) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (true) {
      JsonNode found =
          json.readTree(
              get("/api/v1/search?query=" + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8)));
      for (JsonNode m : found.path("messages")) {
        if (subject == null || subject.equals(m.path("Subject").asString())) {
          return read(m.path("ID").asString());
        }
      }
      if (System.nanoTime() > deadline) {
        return null;
      }
      Thread.sleep(100);
    }
  }

  private Mail read(String id) throws Exception {
    JsonNode message = json.readTree(get("/api/v1/message/" + id));
    String raw = get("/api/v1/message/" + id + "/raw");
    return new Mail(
        message.path("Subject").asString(),
        message.path("Text").asString(),
        message.path("HTML").asString(),
        contentType(raw),
        raw);
  }

  /** En-tête {@code Content-Type} du message lui-même (avant la première ligne vide). */
  static String contentType(String raw) {
    int end = raw.indexOf("\r\n\r\n");
    if (end < 0) {
      end = raw.indexOf("\n\n");
    }
    String headers = (end < 0 ? raw : raw.substring(0, end)).replaceAll("\r?\n[ \t]+", " ");
    for (String line : headers.split("\r?\n")) {
      if (line.regionMatches(true, 0, "Content-Type:", 0, "Content-Type:".length())) {
        return line.substring("Content-Type:".length()).strip();
      }
    }
    return "";
  }

  // ---- vérifications

  /**
   * E-mail en arabe, de droite à gauche (G Q100, Q101) : objet arabe attendu, texte arabe, {@code
   * multipart/alternative} avec texte brut et HTML, HTML en {@code lang="ar" dir="rtl"}, « PsyMate
   * » isolé en {@code ltr} quand le texte le cite, aucun chiffre arabo-indien ; avec {@code link},
   * au moins un lien cliquable isolé en {@code ltr}, qui figure aussi dans le texte brut.
   */
  public static void assertArabic(Mail mail, String expectedSubject, boolean link) {
    assertThat(mail).as("e-mail reçu").isNotNull();
    assertThat(mail.subject()).isEqualTo(expectedSubject);
    assertThat(ARABIC_LETTER.matcher(mail.subject()).find()).as("objet arabe").isTrue();
    assertThat(ARABIC_LETTER.matcher(mail.text()).find()).as("texte arabe").isTrue();
    assertThat(mail.contentType()).startsWith("multipart/alternative");
    assertThat(mail.raw()).contains("Content-Language: ar");
    assertThat(mail.raw()).containsIgnoringCase("Content-Type: text/plain; charset=UTF-8");
    assertThat(mail.raw()).containsIgnoringCase("Content-Type: text/html; charset=UTF-8");
    assertThat(mail.html())
        .contains("<html lang=\"ar\" dir=\"rtl\">")
        .contains("<body dir=\"rtl\">")
        .doesNotContain("<script");
    if (mail.text().contains("PsyMate")) {
      assertThat(mail.html()).contains("dir=\"ltr\">PsyMate");
    }
    for (String part : new String[] {mail.subject(), mail.text(), mail.html()}) {
      assertThat(ARABIC_INDIC_DIGIT.matcher(part).find())
          .as("chiffre arabo-indien dans %s", part)
          .isFalse();
    }
    Matcher l = LTR_LINK.matcher(mail.html());
    if (link) {
      assertThat(l.find()).as("lien cliquable isolé en ltr").isTrue();
      assertThat(mail.text()).contains(l.group(1).replace("&amp;", "&"));
    } else {
      assertThat(l.find()).as("aucun lien").isFalse();
    }
  }

  /**
   * E-mail en français ou en anglais : un seul corps {@code text/plain} en UTF-8, aucune partie
   * HTML, aucun {@code multipart}, aucune marque de direction (comportement d'avant M16-05).
   */
  public static void assertPlainTextOnly(Mail mail, String expectedSubject) {
    assertThat(mail).as("e-mail reçu").isNotNull();
    assertThat(mail.subject()).isEqualTo(expectedSubject);
    assertThat(mail.contentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
    assertThat(mail.html()).isEmpty();
    assertThat(mail.raw()).doesNotContainIgnoringCase("multipart").doesNotContain("text/html");
    assertThat(mail.raw()).doesNotContain("Content-Language");
    assertThat(mail.text()).doesNotContain(RLM).doesNotContain(LRM);
  }
}
