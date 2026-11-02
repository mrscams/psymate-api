package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Tag libre du praticien associé à un patient.")
public record TagDto(
    @Schema(description = "Identifiant du tag.", example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(description = "Libellé du tag, tel qu'affiché.", example = "sommeil") String label) {}
