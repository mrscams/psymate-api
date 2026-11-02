package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** État de vérification : statut, depuis quand, et retard de traitement (under_review). */
@Schema(
    name = "AccountVerificationDto",
    description =
        "État de vérification d'un compte : statut, date d'entrée dans ce statut et retard de"
            + " traitement.")
public record VerificationDto(
    @Schema(
            description =
                "Statut : email_pending (e-mail à confirmer), docs_pending (justificatifs à"
                    + " déposer), under_review (dossier soumis, en attente d'un administrateur),"
                    + " approved, needs_more (complément demandé), rejected (dossier refusé),"
                    + " suspended ou closed.",
            allowableValues = {
              "email_pending",
              "docs_pending",
              "under_review",
              "approved",
              "needs_more",
              "rejected",
              "suspended",
              "closed"
            })
        String status,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) d'entrée dans le statut courant : approbation pour"
                    + " approved, soumission pour under_review, suspension ou refus pour"
                    + " suspended, rejected et closed ; à défaut, dernière mise à jour du"
                    + " compte.",
            example = "2026-03-15T08:00:00Z")
        Instant since,
    @Schema(
            description =
                "Vrai si le dossier est under_review depuis plus de 2 jours ouvrés après sa"
                    + " soumission ; faux pour tout autre statut.")
        boolean overdue) {}
