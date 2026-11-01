package com.psymate.clinical.export;

import java.time.Instant;
import java.util.UUID;

/** DTO REST de l'export du compte (camelCase). */
final class ExportDtos {

  private ExportDtos() {}

  /** Corps optionnel de POST /api/me/export pour un compte sans session. */
  record AccountExportRequest(String email, String password, String otp) {}

  /** 202 : job créé. {@code statusUrl} porte le jeton de suivi (compte sans session). */
  record ExportRequestedDto(UUID jobId, String statusUrl) {}

  /** 202 : mot de passe correct, 2FA active et code manquant. */
  record OtpRequiredDto(boolean otpRequired) {}

  /** Statut d'un job ; {@code downloadUrl} seulement quand il est terminé et non expiré. */
  record ExportStatusDto(
      UUID jobId,
      String status,
      Instant requestedAt,
      Instant finishedAt,
      Instant expiresAt,
      String downloadUrl,
      String error) {}
}
