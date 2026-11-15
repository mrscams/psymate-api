package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** Patient d'un rendez-vous, tel que l'affiche le panneau de détail (fiche 15) : aucun contact. */
@Schema(description = "Patient du rendez-vous : identité affichée seulement, aucun contact.")
public record AppointmentPatientDto(
    @Schema(description = "Identifiant du patient.") UUID id,
    @Schema(description = "Nom affiché du patient.", example = "Théo B.") String displayName,
    @Schema(description = "Date de naissance (AAAA-MM-JJ). Null si inconnue.") LocalDate birthDate,
    @Schema(description = "true si le patient est archivé.") boolean archived) {}
