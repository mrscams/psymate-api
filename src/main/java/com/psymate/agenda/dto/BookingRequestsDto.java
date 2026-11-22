package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Demandes en ligne qui attendent la réponse du praticien ({@code GET /api/agenda/requests}). */
@Schema(
    description =
        "Demandes de rendez-vous en ligne en attente de réponse (G Q74) : rendez-vous à confirmer"
            + " nés d'une demande en ligne, à venir.")
public record BookingRequestsDto(
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Rendez-vous du praticien courant d'origine online au statut tentative"
                            + " qui commencent au plus tôt maintenant, triés par début, 100 au"
                            + " plus."))
        List<AppointmentDto> appointments) {}
