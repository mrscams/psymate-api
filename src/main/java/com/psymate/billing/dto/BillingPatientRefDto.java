package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Patient tel que la facturation le montre : identifiant, nom affiché, archivage. */
@Schema(description = "Patient : identifiant, nom affiché et archivage. Aucune donnée clinique.")
public record BillingPatientRefDto(
    @Schema(description = "Identifiant du patient.") UUID id,
    @Schema(description = "Nom affiché du patient.", example = "Hélène R.") String displayName,
    @Schema(description = "Patient archivé : son groupe se lit sans être facturable (409).")
        boolean archived) {}
