package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Créneau offert à la prise de rendez-vous en ligne : début et fin, rien d'autre (G Q76). */
@Schema(
    description =
        "Créneau offert à la prise de rendez-vous en ligne : son début et sa fin, sans identifiant,"
            + " sans raison ni nombre de places. Seuls les créneaux libres sont publiés.")
public record PublicSlotDto(
    @Schema(description = "Début du créneau, UTC ISO 8601.", example = "2026-12-07T08:00:00Z")
        Instant startsAt,
    @Schema(
            description = "Fin du créneau, UTC ISO 8601 : startsAt + slotDurationMin.",
            example = "2026-12-07T09:00:00Z")
        Instant endsAt) {}
