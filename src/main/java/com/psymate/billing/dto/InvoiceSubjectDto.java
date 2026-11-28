package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Sujet d'une facture dans la liste (L5.3 {@code subject}) : ses séances, ou sa première ligne. */
@Schema(
    description =
        "Ce que la facture couvre : nombre de lignes de séance, première et dernière date des"
            + " lignes, et, pour une facture sans séance, la désignation de sa première ligne.")
public record InvoiceSubjectDto(
    @Schema(description = "Nombre de lignes de séance.", example = "3") int sessionCount,
    @Schema(
            description = "Plus ancienne date des lignes (AAAA-MM-JJ). Null sans date.",
            example = "2026-11-05")
        LocalDate firstOn,
    @Schema(
            description = "Plus récente date des lignes (AAAA-MM-JJ). Null sans date.",
            example = "2026-11-19")
        LocalDate lastOn,
    @Schema(
            description =
                "Désignation de la première ligne quand la facture n'a aucune ligne de séance ;"
                    + " null sinon.",
            example = "Bilan psychologique")
        String label) {}
