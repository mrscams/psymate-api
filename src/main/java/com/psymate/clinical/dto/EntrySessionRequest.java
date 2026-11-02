package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Détail d'une séance. À la création d'une entrée de type session, seule la modalité est"
            + " acceptée (la durée se donne à la clôture : NOT_ALLOWED_ON_CREATE sur"
            + " session.durationMin). En PATCH, un champ absent est conservé ; le genre et la"
            + " clôture ne se modifient pas.")
public record EntrySessionRequest(
    @Schema(
            description = "Modalité de la séance (obligatoire pour le type session).",
            allowableValues = {"in_person", "video", "phone", "email"},
            example = "video")
        String modality,
    @Schema(
            description = "Durée en minutes, de 1 à 600 (OUT_OF_RANGE sinon).",
            minimum = "1",
            maximum = "600",
            example = "50")
        Integer durationMin) {}
