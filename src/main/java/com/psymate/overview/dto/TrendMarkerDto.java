package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Repère sur la courbe : séance ou début/fin de médicament.")
public record TrendMarkerDto(
    @Schema(description = "Jour UTC du repère, AAAA-MM-JJ.", example = "2026-03-10") LocalDate date,
    @Schema(
            description = "Nature du repère : session ou medication.",
            allowableValues = {"session", "medication"},
            example = "session")
        String kind,
    @Schema(
            description = "Identifiant de l'entrée séance ou médicament.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description =
                "Titre de la séance (null sans titre) ou « molécule dosage » du médicament"
                    + " (« molécule » seule sans dosage).",
            example = "sertraline 50 mg")
        String label,
    @Schema(
            description =
                "Pour une séance : intake (accueil), session (séance) ou contact (hors"
                    + " rendez-vous) ; null pour un médicament.",
            allowableValues = {"intake", "session", "contact"},
            example = "session")
        String sessionKind,
    @Schema(
            description = "Pour un médicament : start (début) ou end (fin) ; null pour une séance.",
            allowableValues = {"start", "end"},
            example = "start")
        String change) {}
