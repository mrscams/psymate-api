package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  private static final Set<String> EXPECTED_MODULES =
      Set.of(
          "identity",
          "patients",
          "vocabulary",
          "clinical",
          "linking",
          "search",
          "overview",
          "events",
          "directory",
          "shared",
          "ai",
          "companion");

  /**
   * Modules reserves (A11 et CLAUDE.md : IA V2 et compagnon), vides par construction : leurs
   * dependances declarees decrivent la V2 et ne peuvent pas etre utilisees en V1. Exception
   * permanente a « autorise ⊆ utilise ».
   */
  static final Set<String> RESERVED_MODULES = Set.of("ai", "companion");

  /**
   * Cible jamais tenue pour inutilisee : le module ouvert {@code shared}. Pour vocabulary,
   * patients, linking, search et overview, sa seule reference est la constante {@code
   * AdviceOrder.MODULE} de leur {@code @Order}, recopiee a la compilation donc absente du bytecode
   * que lit Modulith : la dependance est reelle mais invisible. Exception permanente.
   */
  static final Set<String> OPEN_TARGETS = Set.of("shared");

  /** Dossier du tableau des modules genere (ARCH-08, remplace la tenue a la main de A5). */
  static final Path GENERATED_DOCS = Path.of("docs", "generated");

  // Modulith ignores test classes by default; the fixtures live under src/test.
  private static final ImportOption INCLUDE_TESTS = location -> true;

  private final ApplicationModules modules = ApplicationModules.of(App.class);

  @Test
  void applicationModulesAreValid() {
    modules.verify();
  }

  @Test
  void allExpectedModulesAreDetected() {
    Set<String> names = new HashSet<>();
    modules.forEach(m -> names.add(m.getIdentifier().toString()));
    assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
  }

  @Test
  void forbiddenDependencyMakesVerifyFail() {
    ApplicationModules bad =
        ApplicationModules.of(psymatefixture.badmodule.FixtureApp.class, INCLUDE_TESTS);
    assertThatThrownBy(bad::verify)
        .isInstanceOf(Violations.class)
        .hasMessageContaining("psymatefixture.badmodule.a")
        .hasMessageContaining("psymatefixture.badmodule.b");
  }

  @Test
  void allowedDependencyPassesVerify() {
    ApplicationModules.of(psymatefixture.good.FixtureApp.class, INCLUDE_TESTS).verify();
  }

  // ---------------------------------------------------------------------------------------------
  // « Autorise ⊆ utilise » (ARCH-08, M9-02)
  // ---------------------------------------------------------------------------------------------

  /**
   * Modules cibles de {@code allowedDependencies} dans le {@code package-info.java} d'un module
   * ({@code "clinical :: api"} donne {@code clinical}) ; vide pour un module sans declaration.
   */
  static List<String> declaredDependencies(ApplicationModule module) {
    org.springframework.modulith.ApplicationModule annotation;
    try {
      annotation =
          Class.forName(module.getBasePackage().getName() + ".package-info")
              .getAnnotation(org.springframework.modulith.ApplicationModule.class);
    } catch (ClassNotFoundException e) {
      return List.of();
    }
    if (annotation == null) {
      return List.of();
    }
    List<String> allowed = Arrays.asList(annotation.allowedDependencies());
    if (allowed.contains(org.springframework.modulith.ApplicationModule.OPEN_TOKEN)) {
      return List.of();
    }
    return allowed.stream().map(d -> d.split("::")[0].trim()).distinct().sorted().toList();
  }

  /** Modules dont le module utilise au moins un type, tels que Modulith les voit. */
  static Set<String> usedDependencies(ApplicationModule module, ApplicationModules modules) {
    return module
        .getDirectDependencies(modules)
        .uniqueModules()
        .map(m -> m.getIdentifier().toString())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * Dependances declarees mais jamais utilisees, sous la forme {@code module -> cible}. Les modules
   * {@code ignoredModules} ne sont pas verifies ; les cibles {@code ignoredTargets} non plus.
   */
  static List<String> unusedDeclaredDependencies(
      ApplicationModules modules, Set<String> ignoredModules, Set<String> ignoredTargets) {
    List<String> unused = new ArrayList<>();
    for (ApplicationModule module : modules) {
      String id = module.getIdentifier().toString();
      if (ignoredModules.contains(id)) {
        continue;
      }
      Set<String> used = usedDependencies(module, modules);
      for (String declared : declaredDependencies(module)) {
        if (!used.contains(declared) && !ignoredTargets.contains(declared)) {
          unused.add(id + " -> " + declared);
        }
      }
    }
    return unused;
  }

  @Test
  void everyAllowedDependencyIsUsed() {
    assertThat(unusedDeclaredDependencies(modules, RESERVED_MODULES, OPEN_TARGETS))
        .as("dependances declarees dans package-info.java mais inutilisees")
        .isEmpty();
  }

  @Test
  void unusedAllowedDependencyIsDetected() {
    ApplicationModules fixture =
        ApplicationModules.of(psymatefixture.unuseddep.FixtureApp.class, INCLUDE_TESTS);
    fixture.verify();
    assertThat(unusedDeclaredDependencies(fixture, Set.of(), Set.of())).containsExactly("a -> c");
  }

  // ---------------------------------------------------------------------------------------------
  // Documentation generee (ARCH-08, M9-02)
  // ---------------------------------------------------------------------------------------------

  /** Tableau Markdown des modules : dependances declarees et utilisees, interfaces nommees. */
  static String modulesTable(ApplicationModules modules) {
    StringBuilder md = new StringBuilder();
    md.append("# Modules de psymate-api\n\n")
        .append("Fichier genere par `ModularityTests.writesModuleDocumentation` ")
        .append("(`./mvnw verify`) : ne pas modifier a la main. ")
        .append("Les dependances autorisees viennent des `package-info.java` ; ")
        .append("les dependances utilisees, du bytecode (Spring Modulith) : une constante ")
        .append("recopiee a la compilation, comme `AdviceOrder.MODULE`, n'y apparait pas.\n\n")
        .append("| Module | Package | Dependances autorisees | Dependances utilisees ")
        .append("| Interfaces nommees |\n")
        .append("| --- | --- | --- | --- | --- |\n");
    List<ApplicationModule> sorted = new ArrayList<>();
    modules.forEach(sorted::add);
    sorted.sort(Comparator.comparing(m -> m.getIdentifier().toString()));
    for (ApplicationModule module : sorted) {
      List<String> declared = declaredDependencies(module);
      Set<String> used = usedDependencies(module, modules);
      List<String> interfaces =
          module.getNamedInterfaces().stream()
              .map(NamedInterface::getName)
              .filter(n -> !n.startsWith("<<"))
              .sorted()
              .toList();
      md.append("| ")
          .append(module.getIdentifier())
          .append(" | `")
          .append(module.getBasePackage().getName())
          .append("` | ")
          .append(declared.isEmpty() ? "—" : String.join(", ", declared))
          .append(" | ")
          .append(used.isEmpty() ? "—" : String.join(", ", used))
          .append(" | ")
          .append(interfaces.isEmpty() ? "—" : String.join(", ", interfaces))
          .append(" |\n");
    }
    return md.toString();
  }

  private static String itemKind(String line) {
    if (line.startsWith("Rel(")) {
      return "relation";
    }
    if (line.trim().startsWith("Component(")) {
      return "component";
    }
    return line.startsWith("* ") || line.startsWith("|* ") ? "item" : null;
  }

  /**
   * Rend la sortie du {@code Documenter} stable d'une execution a l'autre : Modulith ecrit les
   * relations PlantUML et les listes des fiches dans l'ordre d'ensembles non ordonnes, et les
   * fiches avec le separateur de lignes du systeme. On trie chaque suite de lignes {@code Rel(…)}
   * ou {@code Component(…)} et chaque liste a puces d'une cellule ({@code |* a}, {@code * b}…), et
   * on passe en LF. Le {@code Documenter} vide le dossier avant d'ecrire : un module disparu n'y
   * laisse pas de fichier.
   */
  static String normalizeGenerated(String text) {
    String[] lines = text.replace("\r\n", "\n").split("\n", -1);
    List<String> out = new ArrayList<>();
    int i = 0;
    while (i < lines.length) {
      String kind = itemKind(lines[i]);
      if (kind == null) {
        out.add(lines[i++]);
        continue;
      }
      boolean cell = lines[i].startsWith("|");
      List<String> run = new ArrayList<>();
      run.add(cell ? lines[i].substring(1) : lines[i]);
      int j = i + 1;
      while (j < lines.length && kind.equals(itemKind(lines[j])) && !lines[j].startsWith("|")) {
        run.add(lines[j++]);
      }
      Collections.sort(run);
      if (cell) {
        run.set(0, "|" + run.get(0));
      }
      out.addAll(run);
      i = j;
    }
    return String.join("\n", out);
  }

  /**
   * Regenere {@code docs/generated/} : diagrammes PlantUML et fiches de modules de Spring Modulith
   * (sortie normalisee), puis {@code modules.md}. Un changement d'architecture apparait dans le
   * diff de la PR.
   */
  @Test
  void writesModuleDocumentation() throws IOException {
    new Documenter(
            modules, Documenter.Options.defaults().withOutputFolder(GENERATED_DOCS.toString()))
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml()
        .writeModuleCanvases();
    try (Stream<Path> files = Files.list(GENERATED_DOCS)) {
      for (Path file : files.toList()) {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, normalizeGenerated(text), StandardCharsets.UTF_8);
      }
    }
    Path table = GENERATED_DOCS.resolve("modules.md");
    Files.writeString(table, modulesTable(modules), StandardCharsets.UTF_8);
    assertThat(Files.readString(table, StandardCharsets.UTF_8))
        .contains("| clinical | `com.psymate.clinical` |");
    assertThat(GENERATED_DOCS.resolve("components.puml")).exists();
  }
}
