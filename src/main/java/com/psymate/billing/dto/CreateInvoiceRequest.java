package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Création d'un brouillon de facture (POST /api/invoices, L5.3). Une ligne par séance de {@code
 * sessionEntryIds} (désignation et tarif de G Q87), puis les lignes données. Tout autre champ est
 * refusé (UNKNOWN_FIELD).
 */
@Schema(
    description =
        "Nouveau brouillon : une ligne par séance de sessionEntryIds (désignation sans texte"
            + " clinique, tarif de la séance), par date, puis les lignes données dans leur ordre."
            + " Tout autre champ est refusé (UNKNOWN_FIELD).")
public record CreateInvoiceRequest(
    @Schema(description = "Patient de la facture.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID patientId,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Séances à facturer, 200 au plus, sans doublon : séances closes du"
                            + " patient, à facturer ou écartées. Facultatif."),
            maxItems = 200)
        @Size(max = 200)
        List<@NotNull UUID> sessionEntryIds,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Lignes à ajouter après les séances : 100 lignes libres au plus."
                            + " Facultatif."),
            maxItems = 300)
        @Size(max = 300)
        List<@NotNull @Valid InvoiceLineRequest> lines) {}
