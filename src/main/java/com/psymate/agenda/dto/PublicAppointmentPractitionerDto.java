package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Praticien d'un rendez-vous pris en ligne, tel que le demandeur le voit (K5.1). */
@Schema(description = "Praticien du rendez-vous : identité publique seulement.")
public record PublicAppointmentPractitionerDto(
    @Schema(
            description =
                "Adresse publique (slug) du praticien ; null si sa fiche n'est plus publiée ou"
                    + " s'il n'est plus approuvé.",
            example = "claire-lambert")
        String slug,
    @Schema(
            description =
                "Nom affiché sur la fiche publique ; null si la fiche n'est plus publiée ou si le"
                    + " praticien n'est plus approuvé.",
            example = "Claire Lambert")
        String displayName) {}
