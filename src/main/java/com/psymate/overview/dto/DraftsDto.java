package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Brouillons et séance ouverte.")
public record DraftsDto(
    @Schema(description = "Nombre d'entrées en brouillon non archivées.", example = "2") int count,
    @Schema(description = "Séance non clôturée la plus récente ; null s'il n'y en a pas.")
        OpenSessionDto openSession) {}
