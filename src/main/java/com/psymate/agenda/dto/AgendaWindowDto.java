package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Fenêtre de l'agenda (I3, {@code GET /api/agenda}) : grille Jour / Semaine / Mois et Liste. */
@Schema(
    description =
        "Rendez-vous du praticien qui chevauchent la fenêtre [from, to[, triés par début.")
public record AgendaWindowDto(
    @Schema(description = "Début de la fenêtre, inclus, date-heure ISO 8601 UTC.") Instant from,
    @Schema(description = "Fin de la fenêtre, exclue, date-heure ISO 8601 UTC.") Instant to,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Rendez-vous dont le créneau chevauche la fenêtre (startsAt < to et"
                            + " endsAt > from), triés par startsAt. Annulés exclus sauf"
                            + " includeCancelled=true."))
        List<AppointmentDto> appointments,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Réservé aux indisponibilités externes (jalon ultérieur) : toujours"
                            + " vide dans cette version."))
        List<Object> busy) {}
