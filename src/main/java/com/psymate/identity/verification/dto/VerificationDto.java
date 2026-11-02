package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    description =
        "Dossier professionnel du praticien : état de la vérification, justificatifs actifs,"
            + " pièces manquantes et recommandées, décisions reçues et informations"
            + " professionnelles.")
public record VerificationDto(
    @Schema(
            description =
                "État du dossier : email_pending = e-mail non confirmé ; docs_pending ="
                    + " justificatifs à déposer ; under_review = dossier soumis, en cours"
                    + " d'examen ; approved = praticien vérifié ; needs_more = pièces"
                    + " complémentaires demandées ; rejected = dossier refusé ; suspended ="
                    + " compte suspendu ; closed = compte clos. Le dossier n'est modifiable"
                    + " qu'en docs_pending et needs_more.",
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
            example = "docs_pending")
        String status,
    @Schema(
            description = "Profession du praticien : psychologist, psychiatrist ou coach.",
            allowableValues = {"psychologist", "psychiatrist", "coach"},
            example = "psychologist")
        String profession,
    @Schema(description = "Justificatifs actifs du dossier (20 au maximum), retirés exclus.")
        List<DocumentDto> documents,
    @Schema(
            description =
                "Types de justificatifs obligatoires encore absents (diploma, identity) ; le"
                    + " dossier ne peut être soumis que si la liste est vide.",
            example = "diploma")
        List<String> missing,
    @Schema(
            description =
                "Types de justificatifs conseillés encore absents (registration pour un"
                    + " psychologue ou un psychiatre, certification pour un coach) ; jamais"
                    + " bloquant.",
            example = "registration")
        List<String> recommended,
    @Schema(
            description =
                "Date-heure de la dernière soumission du dossier, en UTC ; null s'il n'a jamais"
                    + " été soumis.",
            example = "2026-03-14T09:30:00Z")
        Instant submittedAt,
    @Schema(
            description =
                "Décisions de l'administration accompagnées d'un message, de la plus récente à"
                    + " la plus ancienne.")
        List<DecisionDto> decisions,
    @Schema(description = "Informations professionnelles enregistrées.")
        ProfessionalInfoDto professionalInfo) {}
