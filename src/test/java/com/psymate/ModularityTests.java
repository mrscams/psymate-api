package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

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
}
