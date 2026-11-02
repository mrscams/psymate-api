package com.psymate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Structure d'un module par role (G Q33, revue d'architecture §4.1 et §5 A-02) et un fichier par
 * DTO (G Q35). Les regles ne s'appliquent qu'aux modules deja restructures, listes dans {@link
 * #ROLE_FOLDER_MODULES}.
 */
class RoleFolderTests {

  private static final String ROOT = "com.psymate";
  private static final String FIXTURE_ROOT = "psymatefixture.badroles";

  /**
   * Modules et sous-domaines restructures par role (package relatif a {@code com.psymate}). Vide au
   * depart (M9-02) ; chaque tache de restructuration (M9-07 a M9-18) y ajoute son module dans la
   * meme PR. Cette liste ne fait que grandir. Une entree couvre tout son sous-arbre : pour
   * identity, ajouter les sous-domaines un par un ({@code identity.auth}…), puis {@code identity}.
   */
  static final Set<String> ROLE_FOLDER_MODULES = Set.of("directory", "linking");

  /**
   * Modules et sous-domaines de moins de 8 classes qui restent plats (G Q33, regle 1 ; revue §4.1)
   * : ni suffixe ni annotation n'y imposent de dossier, meme sous une entree de {@link
   * #ROLE_FOLDER_MODULES}. Ils restent proteges par les gardes fondees sur les annotations d'{@code
   * ArchitectureTests} (entite exposee, technologie). {@code identity.audit} arrive avec M9-05.
   */
  static final Set<String> FLAT_PACKAGES =
      Set.of("events", "identity.preferences", "identity.admin.bootstrap", "identity.audit");

  /** Suffixe de classe → dossier (revue §5 A-02, regle 1). */
  static final Map<String, List<String>> SUFFIX_FOLDERS =
      Map.of(
          "controller", List.of("Controller", "ExceptionHandler", "Advice"),
          "dto", List.of("Dto", "Request", "Mapper", "Assembler"),
          "service", List.of("Service", "Listener", "Job"),
          "repository", List.of("Repository", "Queries"),
          "config", List.of("Config", "Configuration", "Properties", "Filter"));

  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String SPRING_DATA_REPOSITORY =
      "org.springframework.data.repository.Repository";
  private static final String SERVLET_FILTER = "jakarta.servlet.Filter";

  private static final JavaClasses MAIN = ArchitectureTests.MAIN;

  private static String relative(JavaClass c, String root) {
    String pkg = c.getPackageName();
    return pkg.startsWith(root + ".") ? pkg.substring(root.length() + 1) : null;
  }

  private static boolean under(String relative, String prefix) {
    return relative != null && (relative.equals(prefix) || relative.startsWith(prefix + "."));
  }

  /** Vrai si l'un des segments du package, sous l'entree, est {@code folder}. */
  private static boolean inFolder(String relative, String entry, String folder) {
    String rest = relative.equals(entry) ? "" : relative.substring(entry.length() + 1);
    for (String segment : rest.split("\\.")) {
      if (segment.equals(folder)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isFlat(String relative, Set<String> flatPackages) {
    return flatPackages.stream().anyMatch(flat -> under(relative, flat));
  }

  /**
   * Le suffixe ne donne le dossier que d'une classe de premier niveau qui n'est ni une entite (dont
   * le dossier est model/, meme si elle s'appelle {@code ExportJob} ou {@code ContactRequest}) ni
   * un type d'{@code api} (un port peut prendre un {@code LinkRequest}). {@code *Filter} ne vise
   * que les filtres servlet ({@code NameFilter} de patients est une regle metier, dans model/).
   */
  private static boolean suffixGivesFolder(JavaClass c, String relative, String entry) {
    return c.isTopLevelClass() && !c.isAnnotatedWith(ENTITY) && !inFolder(relative, entry, "api");
  }

  /** Regles 1 et 2 : suffixe → dossier et annotation → dossier, pour une entree. */
  static ArchRule foldersMatchRoles(String root, String entry, Set<String> flatPackages) {
    return classes()
        .that()
        .resideInAPackage(root + "." + entry + "..")
        .should(
            new ArchCondition<JavaClass>("live in the folder of its role (G Q33)") {
              @Override
              public void check(JavaClass c, ConditionEvents events) {
                String relative = relative(c, root);
                if (isFlat(relative, flatPackages)) {
                  return;
                }
                if (suffixGivesFolder(c, relative, entry)) {
                  SUFFIX_FOLDERS.forEach(
                      (folder, suffixes) -> {
                        for (String suffix : suffixes) {
                          if (c.getSimpleName().endsWith(suffix)
                              && (!suffix.equals("Filter") || c.isAssignableTo(SERVLET_FILTER))
                              && !inFolder(relative, entry, folder)) {
                            violated(c, "*" + suffix + " belongs in " + folder + "/", events);
                          }
                        }
                      });
                }
                boolean endpoint =
                    c.isAnnotatedWith(RestController.class)
                        || c.isMetaAnnotatedWith(RestController.class)
                        || c.isAnnotatedWith(RestControllerAdvice.class);
                if (endpoint && !inFolder(relative, entry, "controller")) {
                  violated(
                      c, "@RestController / @RestControllerAdvice belongs in controller/", events);
                }
                if (c.isAnnotatedWith(ENTITY) && !inFolder(relative, entry, "model")) {
                  violated(c, "@Entity belongs in model/", events);
                }
                if (c.isInterface()
                    && c.isAssignableTo(SPRING_DATA_REPOSITORY)
                    && !inFolder(relative, entry, "repository")) {
                  violated(c, "a Spring Data repository belongs in repository/", events);
                }
                if (c.isAnnotatedWith(Configuration.class)
                    && !inFolder(relative, entry, "config")) {
                  violated(c, "@Configuration belongs in config/", events);
                }
              }

              private void violated(JavaClass c, String why, ConditionEvents events) {
                events.add(SimpleConditionEvent.violated(c, c.getName() + " : " + why));
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * Regle 3 : sens des dependances entre dossiers (revue §4.1, regle 3). Personne ne depend de
   * controller ; seul service appelle repository ; model ne depend que de model et d'api.
   */
  static ArchRule dependenciesFollowRoles(String root, String entry) {
    String base = root + "." + entry;
    return layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .withOptionalLayers(true)
        .layer("Api")
        .definedBy(base + "..api..")
        .layer("Controller")
        .definedBy(base + "..controller..")
        .layer("Dto")
        .definedBy(base + "..dto..")
        .layer("Service")
        .definedBy(base + "..service..")
        .layer("Repository")
        .definedBy(base + "..repository..")
        .layer("Model")
        .definedBy(base + "..model..")
        .layer("Config")
        .definedBy(base + "..config..")
        .whereLayer("Controller")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Dto")
        .mayOnlyBeAccessedByLayers("Controller", "Service")
        .whereLayer("Service")
        .mayOnlyBeAccessedByLayers("Controller", "Config")
        .whereLayer("Repository")
        .mayOnlyBeAccessedByLayers("Service")
        .whereLayer("Model")
        .mayOnlyAccessLayers("Model", "Api")
        .as("dependencies of " + entry + " follow the role folders (G Q33)");
  }

  /** Garde DTO (G Q35) : aucune classe {@code *Dtos}, aucun record public imbrique dans dto. */
  static ArchRule oneFilePerDto(String root, String entry) {
    return classes()
        .that()
        .resideInAPackage(root + "." + entry + "..dto..")
        .should(
            new ArchCondition<JavaClass>("be one DTO per file (G Q35)") {
              @Override
              public void check(JavaClass c, ConditionEvents events) {
                if (c.getSimpleName().endsWith("Dtos")) {
                  events.add(
                      SimpleConditionEvent.violated(
                          c, c.getName() + " : no *Dtos container, one file per DTO (G Q35)"));
                }
                if (c.isNestedClass()
                    && c.isRecord()
                    && c.getModifiers().contains(JavaModifier.PUBLIC)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          c, c.getName() + " : public nested record, one file per DTO (G Q35)"));
                }
              }
            })
        .allowEmptyShould(true);
  }

  /** Toutes les regles de dossiers par role et la garde DTO, pour chaque entree. */
  static List<ArchRule> roleFolderRules(String root, Set<String> entries, Set<String> flat) {
    List<ArchRule> rules = new ArrayList<>();
    for (String entry : entries) {
      rules.add(foldersMatchRoles(root, entry, flat));
      rules.add(dependenciesFollowRoles(root, entry));
      rules.add(oneFilePerDto(root, entry));
    }
    return rules;
  }

  @Test
  void restructuredModulesFollowRoleFolders() {
    List<ArchRule> rules = roleFolderRules(ROOT, ROLE_FOLDER_MODULES, FLAT_PACKAGES);
    if (!rules.isEmpty()) {
      // Une seule verification : tous les ecarts de tous les modules dans le meme message.
      CompositeArchRule.of(rules).check(MAIN);
    }
  }

  @Test
  void roleFolderEntriesExist() {
    for (String entry : ROLE_FOLDER_MODULES) {
      assertThat(MAIN.stream().anyMatch(c -> under(relative(c, ROOT), entry)))
          .as("ROLE_FOLDER_MODULES : package inconnu " + entry)
          .isTrue();
    }
  }

  @Test
  void roleFolderRuleDetectsMisplacedClassesAndWrongDependencies() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule =
        CompositeArchRule.of(
            List.of(
                foldersMatchRoles(FIXTURE_ROOT, "shop", Set.of("shop.tiny")),
                dependenciesFollowRoles(FIXTURE_ROOT, "shop")));
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        // suffixe et annotation : un controleur range dans service/
        .hasMessageContaining("MisplacedController : *Controller belongs in controller/")
        .hasMessageContaining("MisplacedController : @RestController")
        .hasMessageContaining("StrayEntity : @Entity belongs in model/")
        .hasMessageContaining("StrayJpaRepository : a Spring Data repository")
        .hasMessageContaining("StrayConfiguration : *Configuration belongs in config/")
        .hasMessageContaining("StrayConfiguration : @Configuration belongs in config/")
        .hasMessageContaining("StrayAdvice : *Advice belongs in controller/")
        .hasMessageContaining("StrayAdvice : @RestController / @RestControllerAdvice")
        .hasMessageContaining("StrayFilter : *Filter belongs in config/")
        .hasMessageContaining("MisplacedService : *Service belongs in service/")
        .hasMessageContaining("MisplacedDto : *Dto belongs in dto/")
        .hasMessageContaining("MisplacedQueries : *Queries belongs in repository/")
        // sens des dependances
        .hasMessageContaining("ItemRules")
        .hasMessageContaining("RepositoryCallingController")
        .hasMessageContaining("RepositoryBackedConfig")
        .hasMessageContaining("ItemPrinter")
        .hasMessageNotContaining("ShopController")
        .hasMessageNotContaining("ShopConfig")
        .hasMessageNotContaining("TinyController")
        .hasMessageNotContaining("LegacyController")
        // ni entite (ShopJob), ni type d'api (ShopOrderRequest), ni NameFilter non servlet
        .hasMessageNotContaining("ShopJob")
        .hasMessageNotContaining("ShopOrderRequest")
        .hasMessageNotContaining("NameFilter");
  }

  @Test
  void dtoRuleDetectsContainersAndNestedRecords() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = oneFilePerDto(FIXTURE_ROOT, "shop");
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ShopDtos : no *Dtos container")
        .hasMessageContaining("ShopDtos$NestedRequest : public nested record")
        .hasMessageNotContaining("ItemDto");
  }
}
