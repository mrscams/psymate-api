package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Réponse de création (I3) : les rendez-vous créés et les chevauchements signalés. */
@Schema(description = "Rendez-vous créés et chevauchements signalés (jamais bloquants).")
public record AppointmentCreatedDto(
    @Schema(
            description =
                "Rendez-vous créés, triés par début : un seul sans recurrence, sinon une"
                    + " occurrence par élément, de même seriesId.")
        List<AppointmentDto> appointments,
    @Schema(
            description =
                "Rendez-vous actifs du praticien qui chevauchent l'une des occurrences créées"
                    + " (occurrenceStartsAt dit laquelle) ; vide sinon.")
        List<ConflictDto> conflicts) {}
