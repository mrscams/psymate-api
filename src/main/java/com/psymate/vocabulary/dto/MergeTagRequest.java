package com.psymate.vocabulary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Cible d'une fusion de tags.")
public record MergeTagRequest(
    @Schema(
            description =
                "Identifiant du tag actif du praticien qui absorbe le tag source ; obligatoire,"
                    + " différent de la source.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID targetId) {}
