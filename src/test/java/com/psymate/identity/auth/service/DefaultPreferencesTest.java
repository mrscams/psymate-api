package com.psymate.identity.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.preferences.Preferences;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Garde-fou : les préférences posées à l'inscription (AuthService.defaultPreferences) sont un
 * sous-ensemble des défauts calculés par identity.preferences. Test placé dans identity.auth car la
 * méthode y est package-private ; aucun code de production n'a été déplacé.
 */
class DefaultPreferencesTest {

  @ParameterizedTest
  @ValueSource(strings = {"psychologist", "psychiatrist", "coach"})
  void registrationPreferencesAreSubsetOfComputedDefaults(String profession) {
    Map<String, Object> stored =
        JsonMapper.builder()
            .build()
            .readValue(
                AuthService.defaultPreferences(profession),
                new TypeReference<Map<String, Object>>() {});
    assertThat(Preferences.defaults(profession)).containsAllEntriesOf(stored);
  }
}
