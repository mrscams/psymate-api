package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "État courant de la session du praticien connecté.")
public record SessionResponse(
    @Schema(description = "Identifiant du praticien.") UUID id,
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
                "Statut de vérification à jour (relu en base à chaque appel) : email_pending ="
                    + " e-mail non confirmé ; docs_pending = justificatifs à déposer ;"
                    + " under_review = dossier en cours d'examen ; approved = praticien vérifié ;"
                    + " needs_more = pièces complémentaires demandées ; rejected = dossier refusé"
                    + " ; suspended = compte suspendu ; closed = compte clos.",
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
                "true si l'adresse e-mail est confirmée (verificationStatus différent de"
                    + " email_pending).")
        boolean emailConfirmed) {}
