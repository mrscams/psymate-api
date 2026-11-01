package com.psymate.clinical.export;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** DTO REST de l'export du compte (camelCase). */
final class ExportDtos {

  private ExportDtos() {}

  /** Corps optionnel de POST /api/me/export pour un compte sans session. */
  @Schema(
      description =
          "Identifiants d'un compte refusé ou suspendu, qui n'a pas de session. Ignoré (et"
              + " facultatif) quand le praticien est connecté ; obligatoire sinon (email et"
              + " password REQUIRED).")
  record AccountExportRequest(
      @Schema(
              description = "Adresse e-mail du compte. Obligatoire sans session.",
              example = "dr.martin@example.org")
          String email,
      @Schema(description = "Mot de passe du compte. Obligatoire sans session.") String password,
      @Schema(
              description =
                  "Code de double authentification du compte. Absent : si la double"
                      + " authentification est active, la réponse 202 demande le code"
                      + " (otpRequired).",
              example = "123456")
          String otp) {}

  /** 202 : job créé. {@code statusUrl} porte le jeton de suivi (compte sans session). */
  @Schema(description = "Réponse 202 : export asynchrone mis en file.")
  record ExportRequestedDto(
      @Schema(description = "Identifiant du job d'export.") UUID jobId,
      @Schema(
              description =
                  "URL relative de suivi (GET), qui porte un jeton token valable sans session."
                      + " À interroger jusqu'à ce que status vaille done ou failed.",
              example = "/api/me/export/3f8e2b1a-6c4d-4e57-9a0b-1d2c3e4f5a6b?token=Zm9v")
          String statusUrl) {}

  /** 202 : mot de passe correct, 2FA active et code manquant. */
  @Schema(
      description =
          "Réponse 202 sans job créé : le mot de passe est correct, la double authentification est"
              + " active et aucun code n'a été fourni. Renvoyer la même requête avec otp.")
  record OtpRequiredDto(
      @Schema(description = "Toujours vrai : un code de double authentification est requis.")
          boolean otpRequired) {}

  /** Statut d'un job ; {@code downloadUrl} seulement quand il est terminé et non expiré. */
  @Schema(description = "Statut d'un export de compte.")
  record ExportStatusDto(
      @Schema(description = "Identifiant du job d'export.") UUID jobId,
      @Schema(
              description =
                  "État du job : queued = en attente ; running = en cours de génération ; done ="
                      + " terminé, fichier téléchargeable tant que le lien n'a pas expiré ; failed"
                      + " = échec (voir error).",
              allowableValues = {"queued", "running", "done", "failed"},
              example = "running")
          String status,
      @Schema(
              description = "Instant de la demande, UTC ISO 8601.",
              example = "2026-10-20T19:05:00Z")
          Instant requestedAt,
      @Schema(
              description = "Instant de fin du job, UTC ISO 8601. Null tant qu'il n'est pas fini.",
              example = "2026-10-20T19:06:00Z")
          Instant finishedAt,
      @Schema(
              description =
                  "Échéance du lien de téléchargement, 7 jours après la fin. Null tant que le job"
                      + " n'est pas terminé.",
              example = "2026-10-27T19:06:00Z")
          Instant expiresAt,
      @Schema(
              description =
                  "URL relative signée du fichier zip (GET /api/me/export/{jobId}/file avec exp et"
                      + " sig). Renseignée seulement quand le job est terminé et le lien non"
                      + " expiré, null sinon.")
          String downloadUrl,
      @Schema(description = "Message d'erreur, renseigné seulement quand status vaut failed.")
          String error) {}
}
