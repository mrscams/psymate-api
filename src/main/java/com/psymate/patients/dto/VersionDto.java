package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Schema(
    description =
        "Instantané de la fiche après une modification par PATCH. La création (version 1) n'a"
            + " pas d'instantané : la liste commence à la version 2.")
public record VersionDto(
    @Schema(description = "Numéro de la version produite par la modification.", example = "2")
        int version,
    @Schema(
            description =
                "État de la fiche après la modification : objet JSON avec displayName, firstName,"
                    + " lastName, birthDate (AAAA-MM-JJ ou null), pronoun, phone, email, context"
                    + " et followUp.")
        Map<String, Object> snapshot,
    @Schema(
            description =
                "Motif de la modification fourni par le champ reason du PATCH ; null si absent.",
            example = "Changement de numéro")
        String reason,
    @Schema(
            description = "Identifiant du praticien auteur de la modification.",
            example = "5f1b7c3e-1d2a-4b6c-8e9f-0a1b2c3d4e5f")
        UUID authorId,
    @Schema(
            description = "Date-heure de la modification, UTC ISO 8601.",
            example = "2026-03-14T09:30:00Z")
        OffsetDateTime createdAt) {}
