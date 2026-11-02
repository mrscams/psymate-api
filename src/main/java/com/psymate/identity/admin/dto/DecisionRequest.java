package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Champs absents : checklist = {}, force = false. */
@Schema(description = "Décision de l'administrateur sur un dossier en cours d'examen.")
public record DecisionRequest(
    @Schema(
            description =
                "Issue : approved (dossier validé), needs_more (complément demandé) ou rejected"
                    + " (dossier refusé).",
            allowableValues = {"approved", "needs_more", "rejected"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String kind,
    @Schema(
            description =
                "Message envoyé au praticien par e-mail ; obligatoire pour needs_more et"
                    + " rejected (422 MESSAGE_REQUIRED), 4000 caractères au plus ; facultatif"
                    + " pour approved.")
        String messageToPractitioner,
    @Schema(
            description =
                "Note interne, jamais transmise au praticien, 4000 caractères au plus ;"
                    + " obligatoire pour approved avec force=true (422 NOTE_REQUIRED).")
        String internalNote,
    @Schema(
            description =
                "Liste de contrôle libre (clés et valeurs JSON), 20 000 caractères au plus une"
                    + " fois sérialisée (422 TOO_LARGE) ; absent = objet vide.")
        Map<String, Object> checklist,
    @Schema(
            description =
                "Pour approved uniquement : vrai pour approuver même si tous les justificatifs"
                    + " ne sont pas conformes ; une note interne est alors obligatoire. Absent ="
                    + " faux.")
        Boolean force) {}
