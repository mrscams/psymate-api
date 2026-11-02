package com.psymate.clinical.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Statut d'un job ; {@code downloadUrl} seulement quand il est terminé et non expiré. */
@Schema(description = "Statut d'un export de compte.")
public record ExportStatusDto(
    @Schema(description = "Identifiant du job d'export.") UUID jobId,
    @Schema(
            description =
                "État du job : queued = en attente ; running = en cours de génération ; done ="
                    + " terminé, fichier téléchargeable tant que le lien n'a pas expiré ; failed"
                    + " = échec (voir error).",
            allowableValues = {"queued", "running", "done", "failed"},
            example = "running")
        String status,
    @Schema(description = "Instant de la demande, UTC ISO 8601.", example = "2026-10-20T19:05:00Z")
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
