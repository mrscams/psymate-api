package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Faits clés du patient.")
public record OverviewFactsDto(
    @Schema(
            description =
                "Âge en années révolues aujourd'hui ; null si la date de naissance n'est pas"
                    + " renseignée.",
            example = "34")
        Integer age,
    @Schema(
            description =
                "Date de début du suivi AAAA-MM-JJ : follow_up.since de la fiche si elle est"
                    + " valide, sinon la date de création du dossier.",
            example = "2025-09-01")
        LocalDate followedSince,
    @Schema(description = "Nombre de séances non archivées.", example = "12") int sessionCount,
    @Schema(
            description =
                "Médicaments en cours (fin ni connue ni passée), une ligne par molécule, du plus"
                    + " récent au plus ancien ; liste vide s'il n'y en a pas.")
        List<ActiveMedicationDto> activeMedications,
    @Schema(description = "Drapeaux actifs (non levés), du plus ancien au plus récent.")
        List<OverviewFlagDto> flags) {}
