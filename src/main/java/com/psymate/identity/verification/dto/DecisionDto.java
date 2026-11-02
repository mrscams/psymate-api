package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Message reçu d'un administrateur ; la note interne n'est jamais exposée. */
@Schema(
    description =
        "Décision de l'administration accompagnée d'un message au praticien. La note interne de"
            + " l'administrateur n'est jamais exposée.")
public record DecisionDto(
    @Schema(
            description =
                "Nature de la décision : approved = dossier approuvé, needs_more = pièces"
                    + " complémentaires demandées, rejected = dossier refusé, suspended = compte"
                    + " suspendu, reactivated = compte réactivé, appeal_accepted = recours"
                    + " accepté, appeal_rejected = recours refusé, report_resolved = signalement"
                    + " traité.",
            allowableValues = {
              "approved",
              "needs_more",
              "rejected",
              "suspended",
              "reactivated",
              "appeal_accepted",
              "appeal_rejected",
              "report_resolved"
            },
            example = "needs_more")
        String kind,
    @Schema(
            description = "Message de l'administrateur au praticien.",
            example = "Merci de joindre un diplôme lisible.")
        String message,
    @Schema(description = "Date-heure de la décision, en UTC.", example = "2026-03-15T14:05:00Z")
        Instant decidedAt) {}
