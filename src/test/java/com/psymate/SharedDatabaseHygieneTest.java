package com.psymate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Hygiène d'une base partagée entre les tests (M9-19, revue §5 A-19, « Base partagée : risques et
 * gestion »). Le test lit les chaînes du code de test ({@code src/test/java/com/psymate}) et échoue
 * si, hors de la liste fermée des tests à base dédiée :
 *
 * <ul>
 *   <li>un {@code count(…) from <table>} n'a pas de {@code where} après lui (B1), sauf sur un
 *       catalogue ({@code pg_*}, {@code information_schema}) ou sur une table de réserve suivie de
 *       {@code isZero} dans la même instruction ;
 *   <li>{@code app.events.republish-older-than} descend sous {@code 1h} (B2), que la valeur suive
 *       la clé ({@code clé=0s}) ou soit un autre littéral de l'instruction ({@code
 *       registry.add(clé, () -> "0s")}) ;
 *   <li>un {@code truncate}, ou un {@code delete from <table>} sans {@code where}, s'exécute hors
 *       d'une instruction qui vérifie que la suppression est refusée ({@code assertThatThrownBy}).
 * </ul>
 *
 * <p>Les chaînes concaténées par {@code +} sont lues bout à bout ; une opérande qui n'est pas un
 * littéral (un nom de table en variable) devient {@code ?} et ne prouve rien : un {@code count(*)
 * from ?}, ou {@code from %s}, sans {@code where} est donc refusé. Les blocs de texte sont lus
 * comme les chaînes, les commentaires sont ignorés. Une instruction va d'un {@code ;} au suivant.
 *
 * <p>Trous connus : un {@code where} placé n'importe où après le {@code count} suffit, même celui
 * d'une autre partie de la requête, et {@code where false} aussi ; un refus attendu par {@code
 * try/catch} plutôt que par {@code assertThatThrownBy} est signalé.
 */
class SharedDatabaseHygieneTest {

  private static final Path TEST_SOURCES = Path.of("src", "test", "java", "com", "psymate");
  private static final Path FIXTURE_SOURCES =
      Path.of("src", "test", "java", "psymatefixture", "shareddb");

  /**
   * Tests à base dédiée (B2, B5, B6) : liste fermée, toute addition se justifie dans la PR. Elle
   * est aussi celle de la garde ArchUnit sur {@code PostgreSQLContainer} ({@link
   * #postgresContainersComeFromTestSupport()}).
   */
  static final Set<String> DEDICATED_DATABASE_TESTS =
      Set.of(
          "SearchSchemaIT",
          "FlywayMigrationIT",
          "EventRepublicationIT",
          "SearchPerformanceIT",
          "OverviewPerformanceIT");

  /**
   * Tables de réserve, vides en V1 : celles du compagnon (A11) et de l'IA V2 (A4.9). L'invariant «
   * table vide » vaut sur toute la table, et une base partagée le rend plus fort.
   */
  static final Set<String> RESERVE_TABLES =
      Set.of(
          "ai_feedback", "entry_embedding", "entry_chunk", "patient_device", "patient_submission");

  static final Duration MIN_REPUBLISH_AGE = Duration.ofHours(1);

  /** Base dédiée : un {@code *AppRoleIT} ou un nom de la liste fermée. */
  static boolean dedicated(String className) {
    return className.endsWith("AppRoleIT") || DEDICATED_DATABASE_TESTS.contains(className);
  }

  record Violation(String file, String rule, String sql) {
    @Override
    public String toString() {
      return file + " : " + rule + " : " + sql;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Lecture des sources
  // ---------------------------------------------------------------------------------------------

  /** Source sans commentaires, chaque littéral remplacé par un repère {@code \0n\0}. */
  record Skeleton(String code, List<String> literals) {}

  static Skeleton skeleton(String source) {
    StringBuilder code = new StringBuilder();
    List<String> literals = new ArrayList<>();
    int i = 0;
    int n = source.length();
    while (i < n) {
      char c = source.charAt(i);
      if (source.startsWith("//", i)) {
        int end = source.indexOf('\n', i);
        i = end < 0 ? n : end;
      } else if (source.startsWith("/*", i)) {
        int end = source.indexOf("*/", i + 2);
        i = end < 0 ? n : end + 2;
        code.append(' ');
      } else if (source.startsWith("\"\"\"", i)) {
        int end = source.indexOf("\"\"\"", i + 3);
        code.append('\0').append(literals.size()).append('\0');
        literals.add(source.substring(i + 3, end));
        i = end + 3;
      } else if (c == '"') {
        StringBuilder literal = new StringBuilder();
        int j = i + 1;
        while (source.charAt(j) != '"') {
          char next = source.charAt(j);
          if (next == '\\') {
            j++;
            char escaped = source.charAt(j);
            next = escaped == 'n' || escaped == 't' || escaped == 'r' ? ' ' : escaped;
          }
          literal.append(next);
          j++;
        }
        code.append('\0').append(literals.size()).append('\0');
        literals.add(literal.toString());
        i = j + 1;
      } else if (c == '\'') {
        int j = i + 1;
        if (source.charAt(j) == '\\') {
          j++;
        }
        code.append("' '");
        i = j + 2;
      } else {
        code.append(c);
        i++;
      }
    }
    return new Skeleton(code.toString(), literals);
  }

  private static final Pattern LITERAL = Pattern.compile("\0(\\d+)\0");

  /**
   * Les chaînes d'une instruction : chaque littéral suivi de ses {@code + …}, une opérande non
   * littérale devenant {@code ?}.
   */
  static List<String> chains(String statement, List<String> literals) {
    List<String> chains = new ArrayList<>();
    Matcher m = LITERAL.matcher(statement);
    int consumed = 0;
    while (m.find(consumed)) {
      StringBuilder chain = new StringBuilder(literals.get(Integer.parseInt(m.group(1))));
      int i = m.end();
      while (true) {
        int j = skipSpaces(statement, i);
        if (j >= statement.length() || statement.charAt(j) != '+') {
          break;
        }
        j = skipSpaces(statement, j + 1);
        Matcher next = LITERAL.matcher(statement).region(j, statement.length());
        if (next.lookingAt()) {
          chain.append(literals.get(Integer.parseInt(next.group(1))));
          i = next.end();
        } else {
          chain.append(" ? ");
          i = skipOperand(statement, j);
        }
      }
      chains.add(chain.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
      consumed = Math.max(i, m.end());
    }
    return chains;
  }

  private static int skipSpaces(String s, int i) {
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i;
  }

  /** Une opérande : identifiants, points, appels et index équilibrés ({@code a.b(c)[0]}). */
  private static int skipOperand(String s, int i) {
    int start = i;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (Character.isJavaIdentifierPart(c) && c != '\0' || c == '.') {
        i++;
      } else if (c == '(' || c == '[') {
        int depth = 0;
        do {
          char d = s.charAt(i);
          if (d == '(' || d == '[') {
            depth++;
          } else if (d == ')' || d == ']') {
            depth--;
          }
          i++;
        } while (depth > 0 && i < s.length());
      } else {
        break;
      }
    }
    return i == start ? i + 1 : i;
  }

  // ---------------------------------------------------------------------------------------------
  // Règles
  // ---------------------------------------------------------------------------------------------

  private static final String TABLE = "([a-z_][a-z0-9_.]*|\\?)";
  private static final Pattern COUNT =
      Pattern.compile("\\bcount\\((?:\\*|1|distinct [^)]*|[a-z_.]+)\\) from (\\S+)");
  private static final Pattern DELETE = Pattern.compile("\\bdelete from " + TABLE);
  private static final Pattern TRUNCATE = Pattern.compile("\\btruncate (?:table )?" + TABLE);
  private static final Pattern WHERE = Pattern.compile("\\bwhere\\b");
  private static final Pattern REPUBLISH =
      Pattern.compile(
          "(?:app\\.events\\.republish-?older-?than|app_events_republish_older_than)"
              + "(?: ?[=:] ?([^\\s,\"]+))?");
  private static final Pattern REFUSAL =
      Pattern.compile("assertThatThrownBy|assertThatExceptionOfType|assertThrows");

  /** Toutes les infractions d'un fichier, sans tenir compte de la liste dédiée. */
  static List<Violation> violationsIn(String file, String source) {
    Skeleton skeleton = skeleton(source);
    List<Violation> found = new ArrayList<>();
    for (String statement : skeleton.code().split(";")) {
      boolean refusal = REFUSAL.matcher(statement).find();
      boolean isZero = statement.contains(".isZero()");
      List<String> chains = chains(statement, skeleton.literals());
      for (String chain : chains) {
        Matcher count = COUNT.matcher(chain);
        while (count.find()) {
          String table = count.group(1);
          boolean filtered = WHERE.matcher(chain).region(count.end(), chain.length()).find();
          boolean catalog = table.startsWith("pg_") || table.startsWith("information_schema.");
          boolean reserve = RESERVE_TABLES.contains(table) && isZero;
          if (!filtered && !catalog && !reserve) {
            found.add(new Violation(file, "count sans where (B1)", chain.trim()));
          }
        }
        Matcher delete = DELETE.matcher(chain);
        while (delete.find()) {
          boolean filtered = WHERE.matcher(chain).region(delete.end(), chain.length()).find();
          if (!filtered && !refusal) {
            found.add(new Violation(file, "delete sans where (B3)", chain.trim()));
          }
        }
        if (TRUNCATE.matcher(chain).find() && !refusal) {
          found.add(new Violation(file, "truncate (B3)", chain.trim()));
        }
        Matcher republish = REPUBLISH.matcher(chain);
        while (republish.find()) {
          // Clé seule : la valeur est un autre littéral de l'instruction.
          boolean safe =
              republish.group(1) != null
                  ? atLeastOneHour(republish.group(1))
                  : chains.stream()
                      .map(String::trim)
                      .anyMatch(SharedDatabaseHygieneTest::atLeastOneHour);
          if (!safe) {
            found.add(new Violation(file, "republish-older-than sous 1h (B2)", chain.trim()));
          }
        }
      }
    }
    return found;
  }

  private static boolean atLeastOneHour(String value) {
    try {
      return DurationStyle.detectAndParse(value).compareTo(MIN_REPUBLISH_AGE) >= 0;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** Infractions des fichiers de {@code root}, hors fichiers à base dédiée et hors ce test. */
  static List<Violation> scan(Path root, Predicate<String> dedicated) {
    List<Violation> found = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        String name = file.getFileName().toString().replace(".java", "");
        if (dedicated.test(name) || name.equals(SharedDatabaseHygieneTest.class.getSimpleName())) {
          continue;
        }
        String relative = root.relativize(file).toString().replace('\\', '/');
        found.addAll(violationsIn(relative, Files.readString(file, StandardCharsets.UTF_8)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return found;
  }

  // ---------------------------------------------------------------------------------------------
  // Code de test
  // ---------------------------------------------------------------------------------------------

  @Test
  void testSourcesRespectTheSharedDatabaseRules() {
    assertThat(scan(TEST_SOURCES, SharedDatabaseHygieneTest::dedicated)).isEmpty();
  }

  @Test
  void scannerSeesTheTestSql() throws IOException {
    // Garde contre une règle vide : les tests à base dédiée, eux, comptent des tables entières.
    String flyway =
        Files.readString(TEST_SOURCES.resolve("FlywayMigrationIT.java"), StandardCharsets.UTF_8);
    assertThat(violationsIn("FlywayMigrationIT.java", flyway))
        .extracting(Violation::sql)
        .contains("select count(*) from entry_type");
    String republication =
        Files.readString(
            TEST_SOURCES.resolve("events/EventRepublicationIT.java"), StandardCharsets.UTF_8);
    assertThat(violationsIn("EventRepublicationIT.java", republication))
        .extracting(Violation::rule)
        .contains("republish-older-than sous 1h (B2)");
  }

  // ---------------------------------------------------------------------------------------------
  // Garde ArchUnit : un conteneur Postgres vient de testsupport, sauf pour une base dédiée
  // ---------------------------------------------------------------------------------------------

  private static final String TEST_SUPPORT = "com.psymate.testsupport";

  /**
   * Nom simple de la classe de premier niveau ({@code IdentityAppRoleIT$Config} →
   * IdentityAppRoleIT).
   */
  private static String topLevelName(JavaClass c) {
    String name = c.getName().substring(c.getPackageName().length() + 1);
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  /**
   * Aucune classe hors de {@code support} et des tests à base dédiée ne dépend de {@code
   * PostgreSQLContainer} : les autres tests passent par la base partagée ({@code SharedPostgres}).
   */
  static ArchRule postgresContainersOnlyInTestSupport(String support, Predicate<String> dedicated) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(support + "..")
        .and(
            DescribedPredicate.describe(
                "hors des tests à base dédiée",
                (JavaClass c) ->
                    !dedicated.test(topLevelName(c))
                        && !topLevelName(c)
                            .equals(SharedDatabaseHygieneTest.class.getSimpleName())))
        .should()
        .dependOnClassesThat()
        .areAssignableTo(PostgreSQLContainer.class)
        .because("la base est partagée par JVM (M9-19) ; une base dédiée se justifie dans la PR");
  }

  @Test
  void postgresContainersComeFromTestSupport() {
    JavaClasses tests =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("com.psymate");
    postgresContainersOnlyInTestSupport(TEST_SUPPORT, SharedDatabaseHygieneTest::dedicated)
        .check(tests);
  }

  @Test
  void containerGuardDetectsAStrayContainer() {
    JavaClasses fixtures = new ClassFileImporter().importPackages("psymatefixture.shareddb");
    ArchRule rule =
        postgresContainersOnlyInTestSupport(
            "psymatefixture.shareddb.support", "DedicatedBase"::equals);
    assertThatThrownBy(() -> rule.check(fixtures))
        .hasMessageContaining("StrayContainer")
        .hasMessageNotContaining("FixtureContainers")
        .hasMessageNotContaining("DedicatedBase");
  }

  @Test
  void dedicatedListIsClosed() {
    assertThat(dedicated("IdentityAppRoleIT")).isTrue();
    assertThat(dedicated("SearchSchemaIT")).isTrue();
    assertThat(dedicated("OverviewPerformanceIT")).isTrue();
    assertThat(dedicated("SearchControllerIT")).isFalse();
    assertThat(dedicated("AppRoleHelper")).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // Fixture psymatefixture.shareddb
  // ---------------------------------------------------------------------------------------------

  @Test
  void ruleDetectsEachCaseOfTheFixture() {
    String report =
        String.join(
            "\n",
            scan(FIXTURE_SOURCES, "DedicatedBase"::equals).stream()
                .map(Violation::toString)
                .toList());
    assertThat(report)
        .contains("CountsWholeTable.java : count sans where (B1) : select count(*) from tenant")
        .contains("CountsWholeTable.java : count sans where (B1) : select count(1) from patient")
        .contains("ReserveNotZero.java : count sans where (B1) : select count(*) from ai_feedback")
        .contains("DynamicCount.java : count sans where (B1) : select count(*) from ?")
        .contains("FormattedCount.java : count sans where (B1) : select count(*) from %s")
        .contains("TextBlockCount.java : count sans where (B1) :")
        .contains("RegistryRepublish.java : republish-older-than sous 1h (B2)")
        .contains("DeletesWholeTable.java : delete sans where (B3) : delete from contact_request")
        .contains("Truncates.java : truncate (B3) : truncate table tenant")
        .contains("FastRepublish.java : republish-older-than sous 1h (B2)")
        .doesNotContain("CountsOwnRows")
        .doesNotContain("DeletesOwnRows")
        .doesNotContain("ReserveEmpty")
        .doesNotContain("RefusedDelete")
        .doesNotContain("SlowRepublish")
        .doesNotContain("DedicatedBase")
        .doesNotContain("Comments");
    assertThat(report.lines()).hasSize(10);
  }
}
