package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Régime de TVA (PUT des réglages, G Q92). La mention n'est pas modifiable : elle en découle. */
@Schema(
    description =
        "Régime de TVA. Absent ou null : health_exempt. En régime liable, rateBp"
            + " (VAT_RATE_REQUIRED) et vatNumber (VAT_NUMBER_REQUIRED) sont obligatoires ; hors"
            + " régime liable, ils sont ignorés et effacés.")
public record BillingVatRequest(
    @Schema(
            description = "health_exempt, franchise ou liable (INVALID_VALUE sinon).",
            allowableValues = {"health_exempt", "franchise", "liable"},
            example = "health_exempt")
        String regime,
    @Schema(
            description = "Taux en points de base, de 0 à 10000 (2000 = 20 %).",
            minimum = "0",
            maximum = "10000",
            example = "2000")
        @Min(0)
        @Max(10000)
        Integer rateBp,
    @Schema(
            description =
                "Numéro de TVA intracommunautaire : deux lettres de pays puis 2 à 18 caractères"
                    + " alphanumériques, 20 au plus ; espaces retirés, lettres en majuscules.",
            example = "FR40303265045")
        String vatNumber) {}
