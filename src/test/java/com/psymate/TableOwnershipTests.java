package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Appartenance des tables (G Q34, Q37 ; revue ARCH-01, ARCH-02, ARCH-15). Le test lit les chaines
 * SQL (et JPQL) de {@code src/main/java} et les confronte a une carte table → module proprietaire :
 *
 * <ul>
 *   <li>ecriture ({@code insert}, {@code update}, {@code delete}) seulement par le proprietaire ;
 *   <li>lecture d'une table d'un autre module seulement par les projections (search, overview) ;
 *   <li>aucun {@code delete} hors de la liste fermee E9, y compris par les methodes {@code delete*}
 *       de Spring Data ;
 *   <li>une entite ne mappe qu'une table de son module.
 * </ul>
 *
 * Le module d'un fichier est le premier dossier sous {@code com/psymate}. Les commentaires et les
 * textes d'annotation (hors {@code @Query}) sont ignores ; les chaines d'un fichier sont lues bout
 * a bout, pour suivre les requetes concatenees.
 *
 * <p>Trous connus, non exerces par le code actuel : la deuxieme table d'une jointure par virgule
 * ({@code from a, b}), le JPQL {@code delete Entity} sans {@code from}, les suppressions par {@code
 * EntityManager.remove} ou par cascade ({@code orphanRemoval}, {@code CascadeType.REMOVE}).
 */
class TableOwnershipTests {

  private static final Path MAIN_SOURCES = Path.of("src", "main", "java", "com", "psymate");
  private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");
  private static final Path FIXTURE_SOURCES =
      Path.of("src", "test", "java", "psymatefixture", "badtables");
  private static final String FIXTURE_ROOT = "psymatefixture.badtables";

  /** Carte table (ou vue) → module proprietaire, d'apres E1. Toute nouvelle table s'y ajoute. */
  static final Map<String, String> TABLE_OWNERS =
      Map.ofEntries(
          Map.entry("tenant", "identity"),
          Map.entry("practitioner", "identity"),
          Map.entry("auth_token", "identity"),
          Map.entry("totp_recovery_code", "identity"),
          Map.entry("spring_session", "identity"),
          Map.entry("spring_session_attributes", "identity"),
          Map.entry("security_event", "identity"),
          Map.entry("verification_document", "identity"),
          Map.entry("verification_decision", "identity"),
          Map.entry("document_access_log", "identity"),
          Map.entry("practitioner_appeal", "identity"),
          Map.entry("admin_journal_v", "identity"),
          Map.entry("patient", "patients"),
          Map.entry("patient_version", "patients"),
          Map.entry("patient_flag", "patients"),
          Map.entry("patient_tag", "patients"),
          Map.entry("patient_access", "patients"),
          Map.entry("entry_type", "vocabulary"),
          Map.entry("emotion", "vocabulary"),
          Map.entry("emotion_merge", "vocabulary"),
          Map.entry("link_type", "vocabulary"),
          Map.entry("tag", "vocabulary"),
          Map.entry("entry", "clinical"),
          Map.entry("entry_version", "clinical"),
          Map.entry("entry_feeling", "clinical"),
          Map.entry("entry_medication", "clinical"),
          Map.entry("entry_session", "clinical"),
          Map.entry("entry_tag", "clinical"),
          Map.entry("export_job", "clinical"),
          Map.entry("link", "linking"),
          Map.entry("event_publication", "events"),
          Map.entry("patient_overview", "overview"),
          Map.entry("practitioner_profile", "directory"),
          Map.entry("directory_vocabulary", "directory"),
          Map.entry("contact_request", "directory"),
          Map.entry("entry_embedding", "ai"),
          Map.entry("entry_chunk", "ai"),
          Map.entry("ai_feedback", "ai"),
          Map.entry("patient_device", "companion"),
          Map.entry("patient_submission", "companion"));

  /** Modules de projection : seuls a lire en SQL les tables d'autres modules (G Q34). */
  static final Set<String> PROJECTION_MODULES = Set.of("search", "overview");

  /** Tables qui peuvent perdre des lignes : la liste fermee E9 (G Q37). */
  static final Set<String> PHYSICALLY_DELETABLE =
      Set.of(
          "entry_tag",
          "patient_tag",
          "contact_request",
          "spring_session",
          "spring_session_attributes",
          "event_publication");

  /**
   * Acces encore tolere a une table d'un autre module (lecture et ecriture), par module. Cette
   * liste ne peut que decroitre : chaque entree porte la tache qui la supprime, et {@link
   * #crossModuleExceptionsAreStillNeeded()} echoue des qu'une entree ne sert plus.
   */
  static final Map<String, Set<String>> CROSS_MODULE_EXCEPTIONS =
      Map.of(
          // M9-03 : fusions du vocabulaire par evenements, chaque module reecrit ses tables.
          "vocabulary", Set.of("entry_feeling", "entry_tag", "patient_tag"));

  enum Access {
    READ,
    WRITE,
    DELETE
  }

  record SqlReference(String module, String file, Access access, String table) {
    @Override
    public String toString() {
      return file + " : " + access.name().toLowerCase(Locale.ROOT) + " " + table;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Lecture des sources
  // ---------------------------------------------------------------------------------------------

  /**
   * Les litteraux chaine d'un source Java (blocs de texte compris), hors commentaires et hors
   * arguments d'annotation : les textes OpenAPI ({@code @Operation(description = """…""")}) sont de
   * la prose ou « from patient » n'est pas du SQL. Seules les annotations {@code @Query} et
   * {@code @NativeQuery} (Spring Data) gardent leurs chaines. Les echappements {@code \n}, {@code
   * \t} et {@code \r} deviennent des espaces, pour ne pas coller deux mots SQL.
   */
  static List<String> stringLiterals(String source) {
    List<String> literals = new ArrayList<>();
    int i = 0;
    int n = source.length();
    int depth = 0;
    int skippedAnnotationDepth = -1;
    while (i < n) {
      char c = source.charAt(i);
      boolean keep = skippedAnnotationDepth < 0;
      if (source.startsWith("//", i)) {
        int end = source.indexOf('\n', i);
        i = end < 0 ? n : end;
      } else if (source.startsWith("/*", i)) {
        int end = source.indexOf("*/", i + 2);
        i = end < 0 ? n : end + 2;
      } else if (c == '@') {
        int j = i + 1;
        while (j < n
            && (Character.isJavaIdentifierPart(source.charAt(j)) || source.charAt(j) == '.')) {
          j++;
        }
        String name = source.substring(i + 1, j);
        int k = j;
        while (k < n && Character.isWhitespace(source.charAt(k))) {
          k++;
        }
        if (keep && k < n && source.charAt(k) == '(' && !name.endsWith("Query")) {
          skippedAnnotationDepth = depth;
        }
        i = j;
      } else if (c == '(') {
        depth++;
        i++;
      } else if (c == ')') {
        depth--;
        if (depth == skippedAnnotationDepth) {
          skippedAnnotationDepth = -1;
        }
        i++;
      } else if (source.startsWith("\"\"\"", i)) {
        int end = source.indexOf("\"\"\"", i + 3);
        if (keep) {
          literals.add(source.substring(i + 3, end));
        }
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
        if (keep) {
          literals.add(literal.toString());
        }
        i = j + 1;
      } else if (c == '\'') {
        int j = i + 1;
        if (source.charAt(j) == '\\') {
          j++;
        }
        i = j + 2;
      } else {
        i++;
      }
    }
    return literals;
  }

  private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.$]*|[(),;]");

  private static List<String> tokens(String sql) {
    List<String> tokens = new ArrayList<>();
    Matcher m = TOKEN.matcher(sql);
    while (m.find()) {
      tokens.add(m.group());
    }
    return tokens;
  }

  /**
   * Nom de table d'un identifiant SQL ou JPQL : schema retire, nom d'entite traduit par {@code
   * entityTables} ; null si ce n'est pas une table connue.
   */
  private static String resolve(
      String identifier, Map<String, String> owners, Map<String, String> entityTables) {
    String bare = identifier.substring(identifier.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    String table = entityTables.getOrDefault(bare, bare);
    return owners.containsKey(table) ? table : null;
  }

  /** Les acces aux tables connues d'un texte SQL : {@code from}, {@code join}, {@code into}… */
  static List<SqlReference> references(
      String module,
      String file,
      String sql,
      Map<String, String> owners,
      Map<String, String> entityTables) {
    List<String> t = tokens(sql);
    List<SqlReference> refs = new ArrayList<>();
    for (int k = 0; k + 1 < t.size(); k++) {
      String word = t.get(k).toLowerCase(Locale.ROOT);
      String previous = k > 0 ? t.get(k - 1).toLowerCase(Locale.ROOT) : "";
      int target = k + 1;
      if (t.get(target).equalsIgnoreCase("only") && target + 1 < t.size()) {
        target++;
      }
      Access access = null;
      switch (word) {
        case "from" -> access = previous.equals("delete") ? Access.DELETE : Access.READ;
        case "join", "using" -> access = Access.READ;
        case "into" ->
            access = previous.equals("insert") || previous.equals("merge") ? Access.WRITE : null;
        case "truncate" -> access = Access.DELETE;
        case "update" -> {
          // update <table> [[as] alias] set ; « for update » n'est pas suivi de set.
          for (int s = target + 1; s <= target + 3 && s < t.size(); s++) {
            if (t.get(s).equalsIgnoreCase("set")) {
              access = Access.WRITE;
            }
          }
        }
        default -> {}
      }
      if (word.equals("truncate") && t.get(target).equalsIgnoreCase("table")) {
        target++;
      }
      if (access == null || target >= t.size()) {
        continue;
      }
      String table = resolve(t.get(target), owners, entityTables);
      if (table != null) {
        refs.add(new SqlReference(module, file, access, table));
      }
    }
    return refs;
  }

  /** Les acces aux tables de tous les fichiers Java sous {@code sourceRoot}, par module. */
  static List<SqlReference> scan(
      Path sourceRoot, Map<String, String> owners, Map<String, String> entityTables) {
    List<SqlReference> refs = new ArrayList<>();
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        Path relative = sourceRoot.relativize(file);
        if (relative.getNameCount() < 2) {
          continue;
        }
        String module = relative.getName(0).toString();
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String sql = String.join(" ", stringLiterals(source));
        String name = relative.toString().replace('\\', '/');
        refs.addAll(references(module, name, sql, owners, entityTables));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return refs;
  }

  /** Nom d'entite (JPQL, en minuscules) → table, pour les entites des classes donnees. */
  static Map<String, String> entityTables(JavaClasses classes) {
    Map<String, String> tables = new HashMap<>();
    for (JavaClass c : classes) {
      if (c.isAnnotatedWith("jakarta.persistence.Entity")) {
        String name =
            c.getAnnotationOfType("jakarta.persistence.Entity")
                .get("name")
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElse(c.getSimpleName());
        tables.put(name.toLowerCase(Locale.ROOT), ArchitectureTests.tableOf(c));
      }
    }
    return tables;
  }

  // ---------------------------------------------------------------------------------------------
  // Regles
  // ---------------------------------------------------------------------------------------------

  private static boolean excepted(
      SqlReference ref, Map<String, Set<String>> exceptions, Map<String, String> owners) {
    return !owners.get(ref.table()).equals(ref.module())
        && exceptions.getOrDefault(ref.module(), Set.of()).contains(ref.table());
  }

  /** Violations d'appartenance et de suppression parmi des acces lus dans les sources. */
  static List<String> violations(
      List<SqlReference> refs,
      Map<String, String> owners,
      Set<String> projections,
      Set<String> deletable,
      Map<String, Set<String>> exceptions) {
    Set<String> found = new TreeSet<>();
    for (SqlReference ref : refs) {
      String owner = owners.get(ref.table());
      boolean own = owner.equals(ref.module());
      if (ref.access() == Access.DELETE && !deletable.contains(ref.table())) {
        found.add(ref + " : physical delete outside E9 (G Q37)");
      }
      if (own || excepted(ref, exceptions, owners)) {
        continue;
      }
      if (ref.access() != Access.READ) {
        found.add(ref + " : table owned by " + owner + " (G Q34)");
      } else if (!projections.contains(ref.module())) {
        found.add(ref + " : read of a table owned by " + owner + ", projections only (G Q34)");
      }
    }
    return new ArrayList<>(found);
  }

  /** Entree d'exception qu'aucun acces n'utilise plus. */
  static List<String> staleExceptions(
      List<SqlReference> refs, Map<String, Set<String>> exceptions, Map<String, String> owners) {
    List<String> stale = new ArrayList<>();
    exceptions.forEach(
        (module, tables) -> {
          for (String table : tables) {
            boolean used =
                refs.stream()
                    .anyMatch(
                        r ->
                            r.module().equals(module)
                                && r.table().equals(table)
                                && excepted(r, exceptions, owners));
            if (!used) {
              stale.add(module + " -> " + table);
            }
          }
        });
    return stale;
  }

  private static String moduleOf(JavaClass c, String root) {
    String pkg = c.getPackageName();
    if (!pkg.startsWith(root + ".")) {
      return null;
    }
    String rest = pkg.substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  private static final String SPRING_DATA_REPOSITORY =
      "org.springframework.data.repository.Repository";

  /**
   * Vrai pour une suppression ecrite par Spring Data sur l'entite du repository : methode heritee
   * ({@code CrudRepository.deleteById}…) ou requete derivee ({@code deleteByX}, sans
   * {@code @Query}). Une methode {@code @Query} ou d'un fragment {@code *NativeQueries} porte son
   * propre SQL, lu par le scan des chaines. Methode non resolue : tenue pour une suppression Spring
   * Data (la garde echoue fermee).
   */
  private static boolean isSpringDataDelete(JavaMethodCall call) {
    return call.getTarget()
        .resolveMember()
        .map(
            method -> {
              JavaClass owner = method.getOwner();
              if (owner.getPackageName().startsWith("org.springframework.data")) {
                return true;
              }
              return owner.isAssignableTo(SPRING_DATA_REPOSITORY)
                  && !method.isAnnotatedWith("org.springframework.data.jpa.repository.Query")
                  && !method.isAnnotatedWith("org.springframework.data.jpa.repository.NativeQuery");
            })
        .orElse(true);
  }

  /** Entite geree par un repository Spring Data (premier argument generique), ou null. */
  private static JavaClass repositoryEntity(JavaClass repository) {
    for (JavaType type : repository.getInterfaces()) {
      if (type instanceof JavaParameterizedType parameterized
          && !parameterized.getActualTypeArguments().isEmpty()) {
        JavaClass argument = parameterized.getActualTypeArguments().get(0).toErasure();
        if (argument.isAnnotatedWith("jakarta.persistence.Entity")) {
          return argument;
        }
      }
      JavaClass inherited = repositoryEntity(type.toErasure());
      if (inherited != null) {
        return inherited;
      }
    }
    return null;
  }

  /**
   * Entites et suppressions JPA : une entite mappe une table de son module, et aucune methode
   * {@code delete*} de Spring Data ne vise une table hors E9.
   */
  static List<String> jpaViolations(
      JavaClasses classes,
      String root,
      Map<String, String> owners,
      Set<String> deletable,
      Map<String, Set<String>> exceptions) {
    Set<String> found = new TreeSet<>();
    for (JavaClass c : classes) {
      String module = moduleOf(c, root);
      if (module == null) {
        continue;
      }
      if (c.isAnnotatedWith("jakarta.persistence.Entity")) {
        String table = ArchitectureTests.tableOf(c);
        String owner = owners.get(table);
        if (owner == null) {
          found.add(c.getName() + " maps " + table + ", missing from TABLE_OWNERS");
        } else if (!owner.equals(module)
            && !exceptions.getOrDefault(module, Set.of()).contains(table)) {
          found.add(c.getName() + " maps " + table + ", owned by " + owner + " (G Q34)");
        }
      }
      for (JavaMethodCall call : c.getMethodCallsFromSelf()) {
        JavaClass target = call.getTargetOwner();
        if (!call.getName().startsWith("delete")
            || !target.isAssignableTo(SPRING_DATA_REPOSITORY)
            || !isSpringDataDelete(call)) {
          continue;
        }
        JavaClass entity = repositoryEntity(target);
        if (entity == null) {
          found.add(
              call.getDescription()
                  + " : Spring Data delete whose entity is not resolved, cannot check E9");
        } else if (!deletable.contains(ArchitectureTests.tableOf(entity))) {
          found.add(
              call.getDescription()
                  + " : physical delete of "
                  + ArchitectureTests.tableOf(entity)
                  + " outside E9 (G Q37)");
        }
      }
    }
    return new ArrayList<>(found);
  }

  private static final Pattern CREATE =
      Pattern.compile(
          "create\\s+(?:or\\s+replace\\s+)?(?:unlogged\\s+)?(?:table|view)\\s+"
              + "(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_.]*)",
          Pattern.CASE_INSENSITIVE);

  private static Set<String> migratedTables() throws IOException {
    Set<String> tables = new TreeSet<>();
    try (Stream<Path> files = Files.list(MIGRATIONS)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
        Matcher m = CREATE.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) {
          String name = m.group(1).toLowerCase(Locale.ROOT);
          tables.add(name.substring(name.lastIndexOf('.') + 1));
        }
      }
    }
    return tables;
  }

  // ---------------------------------------------------------------------------------------------
  // Production
  // ---------------------------------------------------------------------------------------------

  private static List<SqlReference> productionReferences() {
    return scan(MAIN_SOURCES, TABLE_OWNERS, entityTables(ArchitectureTests.MAIN));
  }

  @Test
  void everyMigratedTableHasAnOwner() throws IOException {
    assertThat(TABLE_OWNERS.keySet())
        .as("TABLE_OWNERS = tables et vues des migrations Flyway")
        .containsExactlyInAnyOrderElementsOf(migratedTables());
    assertThat(TABLE_OWNERS.keySet()).containsAll(PHYSICALLY_DELETABLE);
  }

  @Test
  void scannerSeesTheProductionSql() {
    // Garde contre une regle vacue (chemin des sources, lecture des litteraux).
    assertThat(productionReferences())
        .extracting(SqlReference::table)
        .contains("entry", "patient_overview", "link", "practitioner_profile");
  }

  @Test
  void tablesAreWrittenByTheirOwnerAndReadOnlyByProjections() {
    assertThat(
            violations(
                productionReferences(),
                TABLE_OWNERS,
                PROJECTION_MODULES,
                PHYSICALLY_DELETABLE,
                CROSS_MODULE_EXCEPTIONS))
        .isEmpty();
  }

  @Test
  void entitiesAndSpringDataDeletesRespectOwnership() {
    assertThat(
            jpaViolations(
                ArchitectureTests.MAIN,
                "com.psymate",
                TABLE_OWNERS,
                PHYSICALLY_DELETABLE,
                CROSS_MODULE_EXCEPTIONS))
        .isEmpty();
  }

  @Test
  void crossModuleExceptionsAreStillNeeded() {
    assertThat(staleExceptions(productionReferences(), CROSS_MODULE_EXCEPTIONS, TABLE_OWNERS))
        .as("exceptions inutiles, a retirer de CROSS_MODULE_EXCEPTIONS")
        .isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Fixture psymatefixture.badtables
  // ---------------------------------------------------------------------------------------------

  private static final Map<String, String> FIXTURE_OWNERS =
      Map.of("alpha_item", "alpha", "beta_item", "beta", "beta_link", "beta");
  private static final Set<String> FIXTURE_DELETABLE = Set.of("beta_link");
  private static final Map<String, Set<String>> FIXTURE_EXCEPTIONS =
      Map.of("alpha", Set.of("beta_link"));

  private static List<SqlReference> fixtureReferences() {
    JavaClasses classes = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    return scan(FIXTURE_SOURCES, FIXTURE_OWNERS, entityTables(classes));
  }

  @Test
  void ownershipRuleDetectsForeignWritesReadsAndDeletes() {
    String report =
        String.join(
            "\n",
            violations(
                fixtureReferences(),
                FIXTURE_OWNERS,
                Set.of("search"),
                FIXTURE_DELETABLE,
                FIXTURE_EXCEPTIONS));
    assertThat(report)
        .contains("alpha/AlphaWritesBeta.java : write beta_item : table owned by beta")
        .contains("alpha/AlphaReadsBeta.java : read beta_item : read of a table owned by beta")
        .contains("search/SearchWritesBeta.java : write beta_item : table owned by beta")
        .contains("beta/BetaJpqlDelete.java : delete beta_item : physical delete outside E9")
        .contains("alpha/AlphaEscapedRead.java : read beta_item")
        .contains("alpha/AlphaQueryRepository.java : read beta_item")
        .doesNotContain("AlphaRepository")
        .doesNotContain("AlphaExcepted")
        .doesNotContain("SearchReadsBeta")
        .doesNotContain("BetaLinkPurge")
        .doesNotContain("BetaComments");
  }

  @Test
  void jpaRuleDetectsSpringDataDeleteAndForeignEntity() {
    JavaClasses classes = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    String report =
        String.join(
            "\n",
            jpaViolations(
                classes, FIXTURE_ROOT, FIXTURE_OWNERS, FIXTURE_DELETABLE, FIXTURE_EXCEPTIONS));
    assertThat(report)
        .contains("BetaCleaner")
        .contains("deleteById")
        .contains("deleteByName")
        .contains("AlphaShadowOfBeta maps beta_item, owned by beta")
        .doesNotContain("deleteAll")
        .doesNotContain("deleteLinkOf")
        .doesNotContain("BetaItem maps");
  }

  @Test
  void staleExceptionIsReported() {
    Map<String, Set<String>> exceptions =
        Map.of("alpha", Set.of("beta_link"), "search", Set.of("alpha_item"));
    assertThat(staleExceptions(fixtureReferences(), exceptions, FIXTURE_OWNERS))
        .containsExactly("search -> alpha_item");
  }
}
