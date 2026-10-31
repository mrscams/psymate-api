package com.psymate;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.GlobalExceptionHandler;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ArchitectureTests {

  private static final String ROOT = "com.psymate";
  private static final String FIXTURE_ROOT = "psymatefixture.badadmin";
  private static final String[] CLINICAL_MODULES = {
    "patients", "clinical", "linking", "overview", "search"
  };

  private static final JavaClasses PRODUCTION = new ClassFileImporter().importPackages(ROOT);

  /** Module name = first package segment after the root package. */
  private static String moduleOf(JavaClass c, String root) {
    String pkg = c.getPackageName();
    if (!pkg.startsWith(root + ".")) {
      return null;
    }
    String rest = pkg.substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  // allowEmptyShould(true): with zero production classes the rules are vacuously true; each rule
  // is proven non-vacuous by the fixture tests below.
  static ArchRule noRepositoryAccessAcrossModules(String root) {
    return classes()
        .should(
            new ArchCondition<JavaClass>("access a repository of another module") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                String from = moduleOf(origin, root);
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  String to = moduleOf(target, root);
                  if (from != null
                      && to != null
                      && !from.equals(to)
                      && target.getSimpleName().endsWith("Repository")) {
                    events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  static ArchRule moduleMustNotDependOn(String root, String source, String... forbidden) {
    String[] targets = new String[forbidden.length];
    for (int i = 0; i < forbidden.length; i++) {
      targets[i] = root + "." + forbidden[i] + "..";
    }
    return noClasses()
        .that()
        .resideInAPackage(root + "." + source + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(targets)
        .allowEmptyShould(true);
  }

  @Test
  void noRepositoryAccessAcrossModulesInProduction() {
    noRepositoryAccessAcrossModules(ROOT).check(PRODUCTION);
  }

  @Test
  void directoryDoesNotTouchClinicalModules() {
    moduleMustNotDependOn(ROOT, "directory", CLINICAL_MODULES).check(PRODUCTION);
  }

  @Test
  void identityAdminDoesNotTouchClinicalModules() {
    moduleMustNotDependOn(ROOT, "identity.admin", CLINICAL_MODULES).check(PRODUCTION);
  }

  @Test
  void repositoryRuleDetectsCrossModuleAccess() {
    JavaClasses fixtures = new ClassFileImporter().importPackages("psymatefixture.badarch");
    ArchRule rule = noRepositoryAccessAcrossModules("psymatefixture.badarch");
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AService")
        .hasMessageContaining("BRepository")
        .hasMessageNotContaining("ARepository");
  }

  @Test
  void directoryRuleDetectsForbiddenImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = moduleMustNotDependOn(FIXTURE_ROOT, "directory", CLINICAL_MODULES);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DirBad");
  }

  @Test
  void identityAdminRuleDetectsForbiddenImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = moduleMustNotDependOn(FIXTURE_ROOT, "identity.admin", CLINICAL_MODULES);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AdminBad");
  }

  @Test
  void reservedPackagesContainNoClassBesidesPackageInfo() {
    for (String reserved : List.of("ai", "companion")) {
      JavaClasses classes = new ClassFileImporter().importPackages(ROOT + "." + reserved);
      assertThat(classes).as(reserved).isNotEmpty();
      assertThat(classes).allMatch(c -> c.getSimpleName().equals("package-info"));
    }
  }

  /**
   * Tout advice de module doit passer strictement avant le filet global de shared (M7-02) : à
   * précédence égale, l'ordre du scan déciderait et le global attraperait tout.
   */
  @Test
  void moduleAdvicesAreOrderedBeforeTheGlobalHandler() {
    JavaClasses advices =
        new ClassFileImporter()
            .importPackages(ROOT)
            .that(annotatedWith(RestControllerAdvice.class));
    assertThat(advices).as("advices").hasSizeGreaterThan(5);
    for (JavaClass advice : advices) {
      if (advice.getName().equals(GlobalExceptionHandler.class.getName())) {
        assertThat(advice.getAnnotationOfType(Order.class).value())
            .as(advice.getName())
            .isEqualTo(Ordered.LOWEST_PRECEDENCE);
        continue;
      }
      assertThat(advice.isAnnotatedWith(Order.class))
          .as(advice.getName() + " porte @Order")
          .isTrue();
      assertThat(advice.getAnnotationOfType(Order.class).value())
          .as(advice.getName())
          .isEqualTo(AdviceOrder.MODULE)
          .isLessThan(Ordered.LOWEST_PRECEDENCE);
    }
  }
}
