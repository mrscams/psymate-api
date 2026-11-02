package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Administrateur auteur d'un événement du journal.")
public record ActorDto(
    @Schema(
            description = "Identifiant de l'administrateur.",
            example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
        UUID id,
    @Schema(description = "Nom d'affichage de l'administrateur.", example = "Julien Bernard")
        String displayName) {}
