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
        String verificationStatus,
    @Schema(
            description =
                "Rôle du compte, relu en base : admin pour un administrateur, practitioner pour"
                    + " tous les autres. Lecture seule.",
            allowableValues = {"practitioner", "admin"},
            example = "practitioner")
        String role,
    @Schema(
            description =
                "Vrai si la double authentification est active (activation vérifiée par"
                    + " POST /api/me/2fa/verify, pas encore désactivée). Lecture seule.")
        boolean twoFactorEnabled,
    @Schema(
            description =
                "Vrai tant que le mot de passe à usage unique d'un compte créé par make admin n'a"
                    + " pas été remplacé via POST /api/auth/password/change (G Q5) ; faux sinon."
                    + " Lecture seule.")
        boolean passwordChangeRequired) {}
