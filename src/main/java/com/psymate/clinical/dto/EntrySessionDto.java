package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Détail d'une séance (type session).")
public record EntrySessionDto(
    @Schema(
            description =
                "Genre de séance : intake = séance d'accueil créée avec le patient ; session ="
                    + " séance ordinaire ; contact = contact hors rendez-vous (téléphone,"
                    + " e-mail) consigné après coup.",
            allowableValues = {"intake", "session", "contact"},
            example = "session")
        String kind,
    @Schema(
            description = "Modalité de la séance.",
            allowableValues = {"in_person", "video", "phone", "email"},
            example = "video")
        String modality,
    @Schema(
            description =
                "Durée en minutes (1 à 600). Null tant que la séance n'est pas clôturée, ou si le"
                    + " contact n'en a pas.",
            minimum = "1",
            maximum = "600")
        Integer durationMin,
    @Schema(
            description = "Instant de clôture UTC. Null tant que la séance est ouverte.",
            example = "2026-10-20T20:00:00Z")
        Instant closedAt) {}
