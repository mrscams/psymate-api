package com.psymate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Les façades {@code api} des modules ne dépendent pas de la couche web (M9-04, revue ARCH-05) :
 * aucun type d'un package {@code ..api..} ne dépend de {@code org.springframework.web..} ni de
 * {@code org.springframework.http..}. Une erreur levée par une façade est une {@code
 * shared.problem.ProblemException} (ex. {@code patients.api.PatientAccessException}), rendue en
 * application/problem+json par le handler commun, jamais un {@code ResponseStatusException}.
 *
 * <p>Fichier séparé d'{@code ArchitectureTests} (réécrit par M9-02 en parallèle).
 */
class ApiBoundaryTests {

  private static final String ROOT = "com.psymate";
  private static final String FIXTURE_ROOT = "psymatefixture.apiboundary";

  private static final String[] WEB_PACKAGES = {
    "org.springframework.web..", "org.springframework.http.."
  };

  /**
   * Types d'{@code api} autorisés à dépendre de la couche web, par nom complet, chacun avec sa
   * raison. Aucun à ce jour : un ajout se justifie dans la PR qui l'introduit.
   */
  static final Set<String> API_WEB_EXCEPTIONS = Set.of();

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  // allowEmptyShould(true) : la règle est prouvée non vide par la fixture.
  static ArchRule apiDoesNotDependOnWeb(String root, Set<String> exceptions) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..api..")
        .and(notListedIn(exceptions))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(WEB_PACKAGES)
        .allowEmptyShould(true);
  }

  /** Hors liste d'exceptions (une classe imbriquée suit sa classe englobante). */
  private static DescribedPredicate<JavaClass> notListedIn(Set<String> exceptions) {
    return DescribedPredicate.describe(
        "are not listed as exceptions",
        c ->
            exceptions.stream()
                .noneMatch(e -> c.getName().equals(e) || c.getName().startsWith(e + "$")));
  }

  @Test
  void apiPackagesDoNotDependOnSpringWeb() {
    apiDoesNotDependOnWeb(ROOT, API_WEB_EXCEPTIONS).check(PRODUCTION);
  }

  @Test
  void ruleDetectsWebTypesInApiPackages() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = apiDoesNotDependOnWeb(FIXTURE_ROOT, Set.of());
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("LeakyGuard")
        .hasMessageContaining("ResponseStatusException")
        .hasMessageContaining("StatusHolder")
        .hasMessageContaining("HttpStatusCode")
        .hasMessageNotContaining("PatientWebThing");
  }

  @Test
  void listedExceptionsAreAllowed() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    apiDoesNotDependOnWeb(
            FIXTURE_ROOT,
            Set.of(
                FIXTURE_ROOT + ".patients.api.LeakyGuard",
                FIXTURE_ROOT + ".patients.api.StatusHolder"))
        .check(fixtures);
  }
}
