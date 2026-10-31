package com.psymate.patients;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO REST du module patients (camelCase). */
final class PatientDtos {

  private PatientDtos() {}

  record FlagDto(String kind, String reason, OffsetDateTime setAt) {}

  record TagDto(UUID id, String label) {}

  record PatientDto(
      UUID id,
      String displayName,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String pronoun,
      String phone,
      String email,
      Map<String, Object> context,
      Map<String, Object> followUp,
      List<FlagDto> flags,
      List<TagDto> tags,
      int currentVersion,
      OffsetDateTime archivedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  record PatientPage(List<PatientDto> items, String nextCursor) {}

  record VersionDto(
      int version,
      Map<String, Object> snapshot,
      String reason,
      UUID authorId,
      OffsetDateTime createdAt) {}

  record FlagRequest(String kind, String reason) {}

  record TagsRequest(List<String> tags) {}

  record CreatePatientRequest(
      String displayName,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String pronoun,
      String phone,
      String email,
      Map<String, Object> context,
      Map<String, Object> followUp,
      List<FlagRequest> flags,
      List<String> tags) {}
}
