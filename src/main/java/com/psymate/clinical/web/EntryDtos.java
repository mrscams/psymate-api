package com.psymate.clinical.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO REST du module clinical (camelCase). Les requêtes n'ont ni authorType ni submissionId (A11) :
 * ces champs sont refusés avant la désérialisation ({@link ForbiddenFieldsAdvice}).
 */
final class EntryDtos {

  private EntryDtos() {}

  // ---- marqueurs des corps de requête contrôlés par ForbiddenFieldsAdvice

  /** Corps de requête du module clinical : authorType et submissionId y sont refusés. */
  interface ClinicalRequest {}

  /** Corps de création ou de modification d'entrée : links y est refusé aussi (M2-02). */
  interface EntryWriteRequest extends ClinicalRequest {}

  // ---- réponses

  record EntryOccurredDto(Instant from, Instant to, String precision, String label) {}

  record EntryFeelingDto(
      String emotionCode, String family, Integer intensity, Integer valence, Integer arousal) {}

  record EntryMedicationDto(
      String molecule,
      String dosage,
      String frequency,
      String prescriber,
      LocalDate periodFrom,
      LocalDate periodTo) {}

  record EntrySessionDto(String kind, String modality, Integer durationMin, Instant closedAt) {}

  /**
   * Entrée complète. {@code authorType} est en lecture seule et vaut toujours « practitioner » en
   * V1 (A11). {@code linkCount} vaut 0 tant que les liens ne sont pas livrés (M2-02).
   */
  record EntryDto(
      UUID id,
      UUID patientId,
      String type,
      String status,
      String visibility,
      String authorType,
      EntryOccurredDto occurred,
      Instant recordedAt,
      UUID sessionId,
      int version,
      String title,
      String body,
      List<String> tags,
      EntryFeelingDto feeling,
      EntryMedicationDto medication,
      EntrySessionDto session,
      int linkCount,
      Instant archivedAt) {}

  record EntryVersionDto(
      int version,
      String title,
      String body,
      String reason,
      String authorType,
      UUID authorId,
      Instant createdAt) {}

  // ---- requêtes d'entrée

  record EntryOccurredRequest(Instant from, Instant to, String precision, String label) {}

  record EntryFeelingRequest(String emotionCode, Integer intensity) {}

  record EntryMedicationRequest(
      String molecule,
      String dosage,
      String frequency,
      String prescriber,
      LocalDate periodFrom,
      LocalDate periodTo) {}

  record EntrySessionRequest(String modality, Integer durationMin) {}

  /** Création : le détail fourni doit correspondre au type. */
  record CreateEntryRequest(
      String type,
      String status,
      EntryOccurredRequest occurred,
      UUID sessionId,
      String visibility,
      String title,
      String body,
      List<String> tags,
      EntryFeelingRequest feeling,
      EntryMedicationRequest medication,
      EntrySessionRequest session)
      implements EntryWriteRequest {}

  /**
   * Modification partielle : un champ absent est inchangé ; {@code occurred} et {@code tags} sont
   * remplacés en entier ; un détail fourni est fusionné champ par champ avec le détail courant.
   */
  record PatchEntryRequest(
      String status,
      EntryOccurredRequest occurred,
      String visibility,
      String title,
      String body,
      List<String> tags,
      EntryFeelingRequest feeling,
      EntryMedicationRequest medication,
      EntrySessionRequest session,
      String reason)
      implements EntryWriteRequest {}

  // ---- requêtes de séance

  record StartSessionRequest(
      @NotBlank @Pattern(regexp = "in_person|video|phone|email") String modality)
      implements ClinicalRequest {}

  record CloseSessionRequest(@Min(1) @Max(600) Integer durationMin, String summary)
      implements ClinicalRequest {}

  record RecordContactRequest(
      @NotBlank @Pattern(regexp = "phone|email") String modality,
      @NotNull Instant occurredFrom,
      @NotBlank String body)
      implements ClinicalRequest {}
}
