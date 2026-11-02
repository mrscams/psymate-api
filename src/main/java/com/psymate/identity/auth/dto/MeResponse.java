package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Praticien connecté, tel qu'ouvert par la session.")
public record MeResponse(
    @Schema(description = "Identifiant du praticien.") UUID id,
    @Schema(
            description =
                "Identifiant du cabinet (tenant) du praticien ; toutes ses données y sont"
                    + " rattachées.")
        UUID tenantId,
    @Schema(description = "Adresse e-mail du compte.", example = "camille.martin@example.org")
        String email,
    @Schema(
            description = "Nom affiché (précédé de Dr pour un psychiatre).",
            example = "Camille Martin")
        String displayName,
    @Schema(
            description = "Profession : psychologist, psychiatrist ou coach.",
            allowableValues = {"psychologist", "psychiatrist", "coach"},
            example = "psychologist")
        String profession,
    @Schema(
            description =
                "Statut de vérification du praticien lors de l'ouverture de la session :"
                    + " email_pending, docs_pending, under_review, approved, needs_more,"
                    + " rejected, suspended ou closed (GET /api/auth/session le relit à jour).",
            allowableValues = {
              "email_pending",
              "docs_pending",
              "under_review",
              "approved",
              "needs_more",
              "rejected",
              "suspended",
              "closed"
            },
            example = "approved")
        String verificationStatus) {}
