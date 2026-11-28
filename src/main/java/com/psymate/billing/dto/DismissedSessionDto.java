package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Séance écartée (« ne pas facturer ») : les champs d'une séance à facturer, et son patient
 * puisqu'elle n'est pas regroupée (L5.2). Montant en centimes d'euro entiers (G Q84).
 */
@Schema(
    description =
        "Séance écartée : mêmes champs qu'une séance à facturer, plus son patient. Aucun texte"
            + " clinique.")
public record DismissedSessionDto(
    @Schema(description = "Patient de la séance.") BillingPatientRefDto patient,
    @Schema(description = "Identifiant de la séance (entrée de type session).") UUID sessionEntryId,
    @Schema(
            description = "Jour de la séance (AAAA-MM-JJ), au fuseau Europe/Paris.",
            example = "2026-11-05")
        LocalDate occurredOn,
    @Schema(description = "Modalité : in_person, video, phone ou email.", example = "video")
        String modality,
    @Schema(
            description = "Durée retenue à la clôture, en minutes. Null si inconnue.",
            example = "50")
        Integer durationMin,
    @Schema(
            description =
                "Prestation retenue : celle choisie pour la séance, sinon la prestation par"
                    + " défaut. Null sans prestation.")
        UUID serviceId,
    @Schema(
            description = "Libellé de la prestation retenue. Null sans prestation.",
            example = "Séance individuelle")
        String serviceLabel,
    @Schema(
            description = "Tarif retenu en centimes d'euro. Null quand rateSource vaut none.",
            minimum = "1",
            example = "6000")
        Integer amount,
    @Schema(
            description = "Origine du tarif (G Q87) : session, patient, service ou none.",
            allowableValues = {"session", "patient", "service", "none"},
            example = "service")
        String rateSource) {}
