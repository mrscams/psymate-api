package com.psymate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.shared.Reserved;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Pas de surface publique morte (M9-06, revue ARCH-07) : tout type public de premier niveau d'un
 * package {@code ..api..} est utilisé par le code de production d'un autre module, ou porte {@link
 * Reserved}. Un type référencé par un type d'{@code api} utilisé (ou réservé) de son module compte
 * comme utilisé ({@code LinkDirection} dans {@code LinkRequest}), quelle que soit la référence
 * (signature ou corps). La garde vise les types, pas les méthodes : une méthode morte d'une façade
 * utilisée passe. En retour, un type {@code @Reserved} utilisé par un autre module doit perdre
 * l'annotation, et une exception de la liste doit encore désigner un type existant, inutilisé et
 * non annoté.
 *
 * <p>Fichier séparé d'{@code ArchitectureTests} (réécrit par M9-05 en parallèle).
 */
class ApiUsageTests {

  private static final String ROOT = "com.psymate";
  private static final String FIXTURE_ROOT = "psymatefixture.apiusage";

  /**
   * Types d'{@code api} sans consommateur ni {@code @Reserved}, hors du périmètre de M9-06, chacun
   * avec la tâche qui le règle. La liste ne fait que diminuer.
   */
  static final Map<String, String> UNUSED_API_EXCEPTIONS =
      Map.of(
          "com.psymate.clinical.api.SessionView",
          "M9-13 : ne sert plus qu'à clinical depuis M9-06, à déplacer hors d'api",
          "com.psymate.identity.api.PractitionerRegistered",
          "M9-18 : événement sans abonné, à annoter @Reserved (G Q40)",
          "com.psymate.identity.api.PractitionerApproved",
          "M9-18 : événement sans abonné, à annoter @Reserved (G Q40)",
          "com.psymate.identity.api.VerificationSubmitted",
          "M9-18 : événement sans abonné, à annoter @Reserved (G Q40)",
          "com.psymate.identity.api.PreferencesChanged",
          "M9-18 : événement sans abonné, à annoter @Reserved (G Q40)",
          "com.psymate.patients.api.PatientArchived",
          "M9-09 : événement sans abonné, à annoter @Reserved (G Q40)",
          "com.psymate.vocabulary.api.EmotionMergeReverted",
          "M9-03 : abonnés de clinical et patients à la partie 3",
          "com.psymate.vocabulary.api.EmotionsMerged",
          "M9-03 : abonnés de clinical et patients à la partie 3",
          "com.psymate.vocabulary.api.TagsMerged",
          "M9-03 : abonnés de clinical et patients à la partie 3",
          "com.psymate.vocabulary.api.EntryTypeView",
          "M9-10 : lu par aucun autre module, à sortir d'api");

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  static ArchRule apiTypesAreUsedOrReserved(String root, Set<String> exceptions) {
    return classes()
        .that()
        .resideInAPackage(root + "..api..")
        .and()
        .arePublic()
        .should(new UsedOrReserved(root, exceptions))
        .allowEmptyShould(true);
  }

  /** Module d'une classe : premier segment sous la racine ; vide pour la racine elle-même. */
  private static String moduleOf(JavaClass c, String root) {
    String rest = c.getName().substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? "" : rest.substring(0, dot);
  }

  private static JavaClass topLevel(JavaClass c) {
    JavaClass t = c;
    while (t.getEnclosingClass().isPresent()) {
      t = t.getEnclosingClass().get();
    }
    return t;
  }

  private static final class UsedOrReserved extends ArchCondition<JavaClass> {

    private final String root;
    private final Set<String> exceptions;

    /** Type de premier niveau → lui-même et ses types imbriqués publics. */
    private final Map<JavaClass, List<JavaClass>> members = new HashMap<>();

    private final Set<String> seen = new HashSet<>();

    UsedOrReserved(String root, Set<String> exceptions) {
      super("be used by another module or be annotated @Reserved (M9-06)");
      this.root = root;
      this.exceptions = exceptions;
    }

    @Override
    public void init(Collection<JavaClass> apiTypes) {
      apiTypes.forEach(c -> members.computeIfAbsent(topLevel(c), t -> new ArrayList<>()).add(c));
    }

    @Override
    public void check(JavaClass c, ConditionEvents events) {
      if (!c.isTopLevelClass()) {
        return;
      }
      seen.add(c.getName());
      boolean reserved = c.isAnnotatedWith(Reserved.class);
      boolean listed = exceptions.contains(c.getName());
      Set<String> users = outsideUsers(c);
      if (reserved && !users.isEmpty()) {
        violated(c, "is @Reserved but used by " + users + " : remove the annotation", events);
      } else if (listed && (reserved || used(c, new HashSet<>()))) {
        violated(c, "is listed as an exception but is used or @Reserved : unlist it", events);
      } else if (!listed && !reserved && !used(c, new HashSet<>())) {
        violated(
            c,
            "is used by no other module : delete it, move it out of api, or annotate @Reserved",
            events);
      }
    }

    @Override
    public void finish(ConditionEvents events) {
      new TreeSet<>(exceptions)
          .stream()
              .filter(e -> !seen.contains(e))
              .forEach(
                  e ->
                      events.add(
                          SimpleConditionEvent.violated(
                              e, e + " is listed as an exception but is no public api type")));
    }

    /** Modules autres que celui du type qui en dépendent (le type ou ses types imbriqués). */
    private Set<String> outsideUsers(JavaClass c) {
      String own = moduleOf(c, root);
      Set<String> users = new TreeSet<>();
      for (Dependency d : dependenciesTo(c)) {
        JavaClass origin = d.getOriginClass();
        if (origin.getName().startsWith(root + ".") && !moduleOf(origin, root).equals(own)) {
          users.add(moduleOf(origin, root));
        }
      }
      return users;
    }

    /**
     * Utilisé hors du module, ou présent dans un type d'{@code api} du même module qui est lui-même
     * utilisé ou réservé.
     */
    private boolean used(JavaClass c, Set<JavaClass> visiting) {
      if (!visiting.add(c)) {
        return false;
      }
      if (!outsideUsers(c).isEmpty()) {
        return true;
      }
      for (Dependency d : dependenciesTo(c)) {
        JavaClass exposer = topLevel(d.getOriginClass());
        if (!exposer.equals(c)
            && members.containsKey(exposer)
            && moduleOf(exposer, root).equals(moduleOf(c, root))
            && (exposer.isAnnotatedWith(Reserved.class) || used(exposer, visiting))) {
          return true;
        }
      }
      return false;
    }

    private List<Dependency> dependenciesTo(JavaClass c) {
      List<Dependency> all = new ArrayList<>();
      members.getOrDefault(c, List.of(c)).forEach(m -> all.addAll(m.getDirectDependenciesToSelf()));
      return all;
    }

    private static void violated(JavaClass c, String why, ConditionEvents events) {
      events.add(SimpleConditionEvent.violated(c, c.getName() + " " + why));
    }
  }

  @Test
  void publicApiTypesAreUsedByAnotherModuleOrReserved() {
    apiTypesAreUsedOrReserved(ROOT, UNUSED_API_EXCEPTIONS.keySet()).check(PRODUCTION);
  }

  @Test
  void ruleDetectsDeadApiTypesAndStaleReservations() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = apiTypesAreUsedOrReserved(FIXTURE_ROOT, Set.of());
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("alpha.api.DeadFacade is used by no other module")
        .hasMessageContaining("alpha.api.InternalOnly is used by no other module")
        .hasMessageContaining("alpha.api.StaleReservation is @Reserved but used by [beta]")
        .hasMessageNotContaining("UsedFacade")
        .hasMessageNotContaining("UsedView")
        .hasMessageNotContaining("ReservedEvent")
        .hasMessageNotContaining("HiddenHelper");
  }

  @Test
  void exceptionsAreHonouredAndMustStayAccurate() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    String api = FIXTURE_ROOT + ".alpha.api.";
    ArchRule rule =
        apiTypesAreUsedOrReserved(
            FIXTURE_ROOT,
            Set.of(
                api + "DeadFacade",
                api + "InternalOnly",
                api + "UsedFacade",
                api + "ReservedEvent",
                api + "Gone"));
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("UsedFacade is listed as an exception but is used")
        .hasMessageContaining("ReservedEvent is listed as an exception but is used or @Reserved")
        .hasMessageContaining("Gone is listed as an exception but is no public api type")
        .hasMessageNotContaining("DeadFacade")
        .hasMessageNotContaining("InternalOnly");
  }
}
