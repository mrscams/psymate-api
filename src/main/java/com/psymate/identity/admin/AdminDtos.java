package com.psymate.identity.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corps des réponses et requêtes de l'administration des dossiers. Jamais de clé de stockage,
 * d'empreinte, de hachage de mot de passe ni de donnée 2FA ; aucune donnée patient.
 */
final class AdminDtos {

  private AdminDtos() {}

  record QueueItemDto(
      UUID practitionerId,
      String displayName,
      String profession,
      String city,
      int documentsCount,
      Instant submittedAt,
      boolean overdue,
      List<String> missingHints) {}

  record QueueDto(List<QueueItemDto> items, String nextCursor) {}

  record AddressDto(String street, String postalCode, String city) {}

  record ContactDto(
      String phone, String professionalNumber, AddressDto address, boolean remoteOnly) {}

  record DocumentDto(
      UUID id,
      String type,
      String title,
      String issuer,
      Integer year,
      String mime,
      int sizeBytes,
      Instant uploadedAt,
      String reviewedStatus,
      Instant reviewedAt,
      boolean purged) {}

  record DecisionItemDto(
      UUID id,
      String kind,
      String messageToPractitioner,
      String internalNote,
      Map<String, Object> checklist,
      Instant decidedAt,
      UUID adminId) {}

  record DossierDto(
      UUID practitionerId,
      String displayName,
      String firstName,
      String lastName,
      String email,
      String profession,
      String locale,
      String verificationStatus,
      Instant createdAt,
      Instant emailConfirmedAt,
      Instant submittedAt,
      boolean overdue,
      Instant verifiedAt,
      ContactDto contact,
      List<DocumentDto> documents,
      List<DecisionItemDto> decisions) {}

  record SignedUrlDto(String url, Instant expiresAt) {}

  record ReviewRequest(String reviewedStatus) {}

  /** Champs absents : checklist = {}, force = false. */
  record DecisionRequest(
      String kind,
      String messageToPractitioner,
      String internalNote,
      Map<String, Object> checklist,
      Boolean force) {}

  record DecisionDto(
      UUID id,
      String kind,
      String messageToPractitioner,
      String internalNote,
      Map<String, Object> checklist,
      Instant decidedAt,
      UUID adminId,
      String verificationStatus) {}
}
