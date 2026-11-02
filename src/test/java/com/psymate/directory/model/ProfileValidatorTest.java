package com.psymate.directory.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.directory.dto.UpdateProfileRequest;
import com.psymate.directory.model.ProfileException.FieldError;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileValidatorTest {

  private static final Set<String> SPECIALTIES = Set.of("anxiety", "emdr");
  private static final Set<String> AUDIENCES = Set.of("adults");

  private static UpdateProfileRequest request(
      List<String> specialties,
      List<String> languages,
      List<String> modalities,
      String availability,
      String website) {
    return new UpdateProfileRequest(
        "Claire Lambert",
        null,
        null,
        null,
        specialties,
        List.of("adults"),
        modalities,
        languages,
        "Lyon",
        null,
        null,
        null,
        null,
        website,
        null,
        null,
        null,
        null,
        null,
        null,
        availability,
        null);
  }

  private static List<String> fields(UpdateProfileRequest r) {
    return ProfileValidator.validate(r, SPECIALTIES, AUDIENCES).stream()
        .map(FieldError::field)
        .toList();
  }

  @Test
  void validRequestHasNoError() {
    assertThat(
            fields(
                request(
                    List.of("anxiety"),
                    List.of("fr", "en"),
                    List.of("video"),
                    "accepting",
                    "https://exemple.fr")))
        .isEmpty();
  }

  @Test
  void specialtyOutsideClosedListIsRejected() {
    assertThat(fields(request(List.of("libre"), null, null, null, null)))
        .containsExactly("specialties");
  }

  @Test
  void languageMustBeIso6391() {
    assertThat(fields(request(null, List.of("fra"), null, null, null)))
        .containsExactly("languages");
    assertThat(fields(request(null, List.of("xx"), null, null, null))).containsExactly("languages");
  }

  @Test
  void modalityAndAvailabilityAreClosed() {
    assertThat(fields(request(null, null, List.of("phone"), "maybe", null)))
        .containsExactlyInAnyOrder("modalities", "availability");
  }

  @Test
  void websiteMustBeHttpOrHttps() {
    assertThat(fields(request(null, null, null, null, "ftp://exemple.fr")))
        .containsExactly("websiteUrl");
    assertThat(fields(request(null, null, null, null, "javascript:alert(1)")))
        .containsExactly("websiteUrl");
  }

  @Test
  void displayNameIsRequired() {
    UpdateProfileRequest r =
        new UpdateProfileRequest(
            " ", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    assertThat(fields(r)).containsExactly("displayName");
  }
}
