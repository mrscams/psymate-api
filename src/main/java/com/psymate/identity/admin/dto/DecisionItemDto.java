package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(
    description =
        "Décision ou acte d'administration enregistré sur un compte (historique append-only,"
            + " renvoyé du plus récent au plus ancien).")
public record DecisionItemDto(
    @Schema(
            description = "Identifiant de la décision.",
            example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
        UUID id,
    @Schema(
            description =
                "Nature de l'acte : approved, needs_more, rejected (vérification du dossier),"
                    + " suspended, reactivated (gestion du compte), appeal_accepted,"
                    + " appeal_rejected (recours), report_resolved (réservé, jamais produit en V1).",
            allowableValues = {
              "approved",
              "needs_more",
              "rejected",
              "suspended",
              "reactivated",
              "appeal_accepted",
              "appeal_rejected",
              "report_resolved"
            })
        String kind,
    @Schema(
            description =
                "Message transmis au praticien (complément demandé, motif du refus ou de la"
                    + " suspension, réponse au recours) ; null s'il n'y en a pas (par exemple une"
                    + " approbation sans message).")
        String messageToPractitioner,
    @Schema(
            description =
                "Note interne de l'administration, jamais transmise au praticien ; null si"
                    + " absente.")
        String internalNote,
    @Schema(
            description =
                "Liste de contrôle libre saisie avec la décision (clés et valeurs JSON). Pour les"
                    + " actes de gestion de compte, contient les statuts avant et après"
                    + " (previousStatus, newStatus) ou la décision contestée (decisionId) ;"
                    + " objet vide s'il n'y en a pas.")
        Map<String, Object> checklist,
    @Schema(
            description = "Date-heure UTC (ISO 8601) de la décision.",
            example = "2026-03-15T08:00:00Z")
        Instant decidedAt,
    @Schema(
            description = "Identifiant de l'administrateur auteur de la décision.",
            example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
        UUID adminId) {}
