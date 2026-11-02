package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Création : le détail fourni doit correspondre au type. {@code links} pose des liens manuels
 * acceptés avec l'entrée, tout ou rien (M2-02) ; refusé pour une séance.
 */
@Schema(
    description =
        "Création d'une entrée. Champs minimaux par type : note = body ; event = title ;"
            + " situation = title et occurred.from ; feeling = feeling.emotionCode et"
            + " feeling.intensity ; medication = medication.molecule ; session ="
            + " session.modality. Un bloc de détail d'un autre type est refusé"
            + " (DETAIL_NOT_ALLOWED). authorType et submissionId sont refusés (UNKNOWN_FIELD)."
            + " Un corps de type session démarre une séance (une seule ouverte à la fois).")
public record CreateEntryRequest(
    @Schema(
            description = "Type de l'entrée. Obligatoire (REQUIRED).",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "note")
        String type,
    @Schema(
            description =
                "Statut : draft ou final (défaut final). Autre valeur : INVALID_VALUE. Ignoré"
                    + " pour une séance, toujours finale.",
            allowableValues = {"draft", "final"},
            defaultValue = "final",
            example = "final")
        String status,
    @Schema(
            description =
                "Moment auquel l'entrée a eu lieu. Absent : l'instant de saisie (obligatoire"
                    + " pour une situation).")
        EntryOccurredRequest occurred,
    @Schema(
            description =
                "Séance de rattachement, qui doit être une séance de ce patient (SESSION_INVALID"
                    + " sinon). Absent : la séance courante du patient (SESSION_REQUIRED s'il"
                    + " n'en a aucune). Interdit pour une séance (SESSION_NOT_ALLOWED).")
        UUID sessionId,
    @Schema(
            description =
                "Visibilité : private ou clinical (défaut clinical). Autre valeur :"
                    + " INVALID_VALUE.",
            allowableValues = {"private", "clinical"},
            defaultValue = "clinical",
            example = "clinical")
        String visibility,
    @Schema(
            description =
                "Titre (obligatoire pour event et situation). Refusé pour une séance"
                    + " (NOT_ALLOWED_ON_CREATE).",
            example = "Dispute avec le collègue")
        String title,
    @Schema(
            description =
                "Texte (obligatoire pour note). Refusé pour une séance (NOT_ALLOWED_ON_CREATE) :"
                    + " le compte rendu se donne à la clôture.")
        String body,
    @Schema(
            description =
                "Libellés de tags existants du praticien (UNKNOWN_TAG sur tags[i] sinon). Refusé"
                    + " pour une séance (NOT_ALLOWED_ON_CREATE).")
        List<String> tags,
    @Schema(description = "Détail du ressenti, pour le type feeling uniquement.")
        EntryFeelingRequest feeling,
    @Schema(description = "Détail du médicament, pour le type medication uniquement.")
        EntryMedicationRequest medication,
    @Schema(description = "Détail de la séance, pour le type session uniquement.")
        EntrySessionRequest session,
    @Schema(
            description =
                "Liens à poser avec l'entrée, 20 au plus (OUT_OF_RANGE au-delà), tout ou rien :"
                    + " un lien refusé annule la création. Refusé pour une séance"
                    + " (NOT_ALLOWED_ON_CREATE).")
        List<EntryLinkRequest> links)
    implements ClinicalRequest {}
