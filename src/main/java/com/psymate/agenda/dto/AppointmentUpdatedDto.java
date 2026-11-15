package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Réponse de modification (I3) : le rendez-vous à jour et les chevauchements de son créneau. */
@Schema(description = "Rendez-vous modifié et chevauchements de son créneau (jamais bloquants).")
public record AppointmentUpdatedDto(
    @Schema(description = "Rendez-vous après modification.") AppointmentDto appointment,
    @Schema(description = "Rendez-vous actifs du praticien qui chevauchent ; vide sinon.")
        List<ConflictDto> conflicts) {}
