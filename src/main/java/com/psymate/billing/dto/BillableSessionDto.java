package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Séance à facturer (L5.2) : date, modalité, durée, prestation et tarif retenus. Aucun texte
 * clinique : ni titre, ni compte rendu. Montant en centimes d'euro entiers (G Q84).
 */
@Schema(
    description =
        "Séance à facturer. Aucun texte clinique (ni titre, ni compte rendu). Montant en centimes"
            + " d'euro entiers.")
public record BillableSessionDto(
    @Schema(description = "Identifiant de la séance (entrée de type session).") UUID sessionEntryId,
    @Schema(
            description = "Jour de la séance (AAAA-MM-JJ), au fuseau Europe/Paris.",
            example = "2026-11-05")
        LocalDate occurredOn,
    @Schema(description = "Modalité : in_person, video, phone ou email.", example = "in_person")
        String modality,
    @Schema(
            description = "Durée retenue à la clôture, en minutes. Null si inconnue.",
            example = "50")
        Integer durationMin,
    @Schema(
            description =
                "Prestation retenue : celle choisie pour la séance (PATCH), sinon la prestation"
                    + " par défaut. Null sans prestation.")
        UUID serviceId,
    @Schema(
            description = "Libellé de la prestation retenue. Null sans prestation.",
            example = "Séance individuelle")
        String serviceLabel,
    @Schema(
            description =
                "Tarif retenu pour cette séance, en centimes d'euro : 6000 = 60,00 €. Null quand"
                    + " rateSource vaut none (la séance ne peut pas entrer dans une facture).",
            minimum = "1",
            example = "6000")
        Integer amount,
    @Schema(
            description =
                "Origine du tarif de cette séance (G Q87) : session (montant fixé sur la séance),"
                    + " patient (tarif du patient), service (prix de la prestation retenue) ou"
                    + " none.",
            allowableValues = {"session", "patient", "service", "none"},
            example = "patient")
        String rateSource) {}
