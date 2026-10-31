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
   * V1 (A11). {@code links} n'est renseigné que dans la réponse de création (POST, liste vide si
   * aucun lien demandé) ; il vaut null sur GET, PATCH et restauration, où les liens se lisent par
   * GET /api/entries/{id}/links (M2-01). {@code linkCount} est le nombre de liens acceptés.
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
      List<EntryLinkDto> links,
      Instant archivedAt) {}

  /**
   * Lien posé à la création, dans son sens réel (source vers cible). {@code label} est le verbe vu
   * depuis la nouvelle entrée : libellé inverse si elle est la cible d'un type dirigé ; anglais si
   * Accept-Language: en, français sinon.
   */
  record EntryLinkDto(UUID id, UUID sourceId, UUID targetId, String linkType, String label) {}

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

  /**
   * Lien demandé avec une nouvelle entrée. {@code direction} : outgoing (défaut, la nouvelle entrée
   * est la source) ou incoming (elle est la cible).
   */
  record EntryLinkRequest(UUID targetId, String linkType, String direction) {}

  /**
   * Création : le détail fourni doit correspondre au type. {@code links} pose des liens manuels
   * acceptés avec l'entrée, tout ou rien (M2-02) ; refusé pour une séance.
   */
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
      EntrySessionRequest session,
      List<EntryLinkRequest> links)
      implements ClinicalRequest {}

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
      implements ClinicalRequest {}

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
