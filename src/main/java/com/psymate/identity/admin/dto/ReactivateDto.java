package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Résultat d'une réactivation de compte.")
public record ReactivateDto(
    @Schema(
            description = "Identifiant du compte réactivé.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(
            description =
                "Statut de vérification rendu au compte : docs_pending pour un dossier refusé"
                    + " (le dossier doit être redéposé), sinon le statut d'avant la suspension"
                    + " (docs_pending, under_review, approved ou needs_more).",
            allowableValues = {"docs_pending", "under_review", "approved", "needs_more"})
        String verificationStatus,
    @Schema(
            description = "Identifiant de la décision de réactivation enregistrée.",
            example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
        UUID decisionId) {}
