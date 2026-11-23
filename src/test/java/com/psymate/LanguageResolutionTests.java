package com.psymate;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.servlet.ServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * M16-02 (G Q96) : la langue servie se résout en un seul endroit, {@code com.psymate.shared.i18n}.
 * Hors de {@code shared}, aucune classe ne lit {@code Locale.FRENCH} ni {@code Locale.ENGLISH},
 * n'appelle {@code Locale.forLanguageTag} ni {@code Locale.LanguageRange.parse}, ni ne lit la
 * locale d'une requête ({@code getLocale}, {@code getLocales}) : elle passe par {@code
 * ServedLanguage}. Les comparaisons littérales à une langue ({@code "en".equals(…)}) sont cherchées
 * dans les sources.
 */
class LanguageResolutionTests {

  private static final Path MAIN_SOURCES = Path.of("src", "main", "java", "com", "psymate");
  private static final Path SHARED_SOURCES = MAIN_SOURCES.resolve("shared");

  /** {@code "en".equals(…)}, {@code .equals("fr")}, {@code "ar".equalsIgnoreCase(…)}… */
  private static final Pattern LITERAL_LANGUAGE_TEST =
      Pattern.compile(
          "\"(fr|en|ar)\"\\s*\\.\\s*equals|equals(IgnoreCase)?\\(\\s*\"(fr|en|ar)\"\\s*\\)");

  private static DescribedPredicate<JavaAccess<?>> localeField(String field) {
    return target(owner(JavaClass.Predicates.type(Locale.class)))
        .and(target(name(field)))
        .as("Locale." + field);
  }

  private static DescribedPredicate<JavaAccess<?>> call(Class<?> owner, String method) {
    return target(owner(JavaClass.Predicates.assignableTo(owner)))
        .and(target(name(method)))
        .as(owner.getSimpleName() + "." + method);
  }

  static ArchRule languageIsResolvedInSharedOnly(String root) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(root + ".shared..")
        .should()
        .accessTargetWhere(localeField("FRENCH"))
        .orShould()
        .accessTargetWhere(localeField("ENGLISH"))
        .orShould()
        .accessTargetWhere(call(Locale.class, "forLanguageTag"))
        .orShould()
        .accessTargetWhere(call(Locale.LanguageRange.class, "parse"))
        .orShould()
        .accessTargetWhere(call(ServletRequest.class, "getLocale"))
        .orShould()
        .accessTargetWhere(call(ServletRequest.class, "getLocales"))
        .as("la langue servie se résout dans shared seulement (G Q96, ServedLanguage)");
  }

  @Test
  void languageIsResolvedInSharedOnly() {
    languageIsResolvedInSharedOnly("com.psymate").check(ArchitectureTests.MAIN);
  }

  @Test
  void ruleDetectsLocalResolutions() {
    JavaClasses fixtures = new ClassFileImporter().importPackages("psymatefixture.badlanguage");
    assertThatThrownBy(
            () -> languageIsResolvedInSharedOnly("psymatefixture.badlanguage").check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("LocalLanguageRules.english()")
        .hasMessageContaining("LocalLanguageRules.french()")
        .hasMessageContaining("LocalLanguageRules.tag(java.lang.String)")
        .hasMessageContaining("LocalLanguageRules.ranges(java.lang.String)")
        .hasMessageContaining("LocalLanguageRules.request(jakarta.servlet.http.HttpServletRequest)")
        .hasMessageContaining("LocalLanguageRules.requestLocales(")
        .hasMessageNotContaining("AllowedLanguageRules")
        .hasMessageNotContaining("LanguageAgnostic");
  }

  @Test
  void noLiteralLanguageComparisonOutsideShared() throws IOException {
    List<String> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        if (file.startsWith(SHARED_SOURCES)) {
          continue;
        }
        String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
          Matcher m = LITERAL_LANGUAGE_TEST.matcher(lines[i]);
          if (m.find()) {
            found.add(file + ":" + (i + 1) + " " + lines[i].strip());
          }
        }
      }
    }
    assertThat(found).as("comparaisons littérales à une langue hors de shared").isEmpty();
  }

  @Test
  void literalPatternMatchesTheRulesItReplaces() {
    assertThat(LITERAL_LANGUAGE_TEST.matcher("return \"en\".equals(tag) ? a : b;").find()).isTrue();
    assertThat(LITERAL_LANGUAGE_TEST.matcher("\"en\".equals(locale.getLanguage())").find())
        .isTrue();
    assertThat(LITERAL_LANGUAGE_TEST.matcher("if (lang.equals(\"ar\"))").find()).isTrue();
    assertThat(LITERAL_LANGUAGE_TEST.matcher("\"coach\".equals(profession)").find()).isFalse();
  }
}
