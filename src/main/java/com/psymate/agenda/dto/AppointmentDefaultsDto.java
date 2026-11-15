package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Défauts de création d'un rendez-vous pour un patient (G Q56) : le web préremplit. */
@Schema(
    description =
        "Valeurs à préremplir pour un nouveau rendez-vous de ce patient. durationMin et modality"
            + " restent obligatoires à la création.")
public record AppointmentDefaultsDto(
    @Schema(
            description =
                "Durée en minutes : celle du suivi du patient, sinon la préférence"
                    + " sessions.defaultDurationMin du praticien, sinon 60.",
            example = "50")
        int durationMin,
    @Schema(
            description =
                "Modalité : celle du suivi du patient si elle vaut in_person, video ou phone, sinon"
                    + " in_person.",
            allowableValues = {"in_person", "video", "phone"},
            example = "in_person")
        String modality,
    @Schema(
            description =
                "Rythme du suivi, texte libre affiché tel quel (« toutes les 2 semaines »). Null"
                    + " s'il n'est pas renseigné.")
        String rhythm) {}
