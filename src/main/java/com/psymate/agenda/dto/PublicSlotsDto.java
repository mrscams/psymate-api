package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Créneaux offerts d'une fenêtre de dates ({@code GET …/practitioners/{slug}/slots}). */
@Schema(
    description =
        "Créneaux offerts à la prise de rendez-vous en ligne sur une fenêtre de dates (G Q76).")
public record PublicSlotsDto(
    @Schema(
            description =
                "Fuseau IANA du praticien, dans lequel les dates from et to sont lues et les"
                    + " créneaux s'affichent.",
            example = "Europe/Paris")
        String timezone,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Créneaux offerts, instants UTC triés par début ; vide si aucun créneau"
                            + " n'est libre."))
        List<PublicSlotDto> slots) {}
