package com.psymate.identity.verification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Corps des réponses et requêtes du dossier professionnel. */
final class VerificationDtos {

  private VerificationDtos() {}

  /** Justificatif : jamais de clé de stockage ni d'empreinte. */
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
      boolean purged) {}

  /** Message reçu d'un administrateur ; la note interne n'est jamais exposée. */
  record DecisionDto(String kind, String message, Instant decidedAt) {}

  record AddressDto(String street, String postalCode, String city) {}

  record ProfessionalInfoDto(
      String professionalNumber, String phone, AddressDto address, boolean remoteOnly) {}

  record VerificationDto(
      String status,
      String profession,
      List<DocumentDto> documents,
      List<String> missing,
      List<String> recommended,
      Instant submittedAt,
      List<DecisionDto> decisions,
      ProfessionalInfoDto professionalInfo) {}

  /** Champs absents ou null : inchangés ; chaîne vide : champ effacé. */
  record ProfessionalInfoRequest(
      String professionalNumber, String phone, AddressDto address, Boolean remoteOnly) {}

  record SignedUrlDto(String url, Instant expiresAt) {}
}
