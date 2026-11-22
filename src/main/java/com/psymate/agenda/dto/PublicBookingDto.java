package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Réglages publics de la prise de rendez-vous en ligne d'un praticien ({@code GET
 * /api/public/booking/practitioners/{slug}}, G Q75). Ni plages, ni amplitude de travail (G Q76).
 */
@Schema(
    description =
        "Prise de rendez-vous en ligne offerte par un praticien et ses réglages publics (G Q75) :"
            + " ni les plages ouvertes, ni les rendez-vous, ni l'amplitude de travail.")
public record PublicBookingDto(
    @Schema(description = "Adresse publique (slug) du praticien.", example = "claire-lambert")
        String slug,
    @Schema(description = "Nom affiché sur la fiche publique.", example = "Claire Lambert")
        String displayName,
    @Schema(
            description =
                "Mode de confirmation : manual, le praticien confirme ou refuse chaque demande ;"
                    + " auto, le rendez-vous est confirmé dès la vérification de l'e-mail.",
            allowableValues = {"manual", "auto"},
            example = "manual")
        String confirmation,
    @Schema(description = "Durée d'un créneau, en minutes (15 à 180).", example = "60")
        int slotDurationMin,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Modalités offertes, jamais vide, dans l'ordre in_person, video, phone."),
            schema =
                @Schema(
                    description = "Modalité de consultation.",
                    allowableValues = {"in_person", "video", "phone"},
                    example = "in_person"))
        List<String> modalities,
    @Schema(
            description = "Fuseau IANA du praticien, dans lequel les créneaux s'affichent.",
            example = "Europe/Paris")
        String timezone,
    @Schema(
            description = "Délai minimal, en heures, entre maintenant et le début d'un créneau.",
            example = "24")
        int minNoticeHours,
    @Schema(
            description =
                "Horizon, en jours : aucun créneau après la fin du jour aujourd'hui + horizonDays"
                    + " dans le fuseau du praticien.",
            example = "28")
        int horizonDays) {}
