package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Profil 360 complet d'un patient, pour l'onglet : toutes les informations actives, sans"
            + " pagination (500 au plus).")
public record PatientProfileDto(
    @Schema(
            description = "Identifiant du patient.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID patientId,
    @Schema(
            description =
                "Informations actives, triées par rubrique (history, family, health,"
                    + " values_goals) ; dans history, periodYear croissant (sans année en"
                    + " dernier) puis createdAt ; ailleurs, createdAt croissant. Liste vide si le"
                    + " profil est vide.")
        List<PatientProfileItemDto> items) {}
