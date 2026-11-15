package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Rattachement d'un rendez-vous invité à la fiche patient créée pour lui (G Q60). */
@Schema(description = "Rattachement d'un rendez-vous invité à un patient du praticien.")
public record AttachPatientRequest(
    @Schema(description = "Patient du praticien, non archivé. Obligatoire.") @NotNull
        UUID patientId,
    @Schema(
            description =
                "true pour rattacher aussi tous les autres rendez-vous invités non purgés du"
                    + " praticien ayant les mêmes nom, prénom et téléphone (casse et espaces"
                    + " ignorés). Défaut : false.")
        Boolean includeSameGuest) {}
