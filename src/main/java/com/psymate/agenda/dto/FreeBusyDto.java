package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Voisinage d'un créneau (I3, {@code GET /api/agenda/freebusy}) : conflits, précédent, suivant. */
@Schema(
    description =
        "Ce qui entoure un créneau avant de le créer ou d'y déplacer un rendez-vous : conflits,"
            + " rendez-vous précédent et suivant du même jour. Les rendez-vous invités (G Q60)"
            + " comptent comme les autres : leur patientDisplayName vaut « Prénom Nom », ou"
            + " « Invité purgé » après la purge, et previous / next portent guest au lieu de"
            + " patient.")
public record FreeBusyDto(
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Rendez-vous actifs (planned, tentative) qui chevauchent le créneau,"
                            + " triés par début. Vide si le créneau est libre."))
        List<ConflictDto> conflicts,
    @Schema(
            description =
                "Dernier rendez-vous actif terminé au plus tard au début du créneau, le même jour"
                    + " calendaire (fuseau tz). Null s'il n'y en a pas.")
        AppointmentDto previous,
    @Schema(
            description =
                "Premier rendez-vous actif commençant au plus tôt à la fin du créneau, le même jour"
                    + " calendaire (fuseau tz). Null s'il n'y en a pas.")
        AppointmentDto next) {}
