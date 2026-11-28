package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Une page de la liste des factures (L5.3), avec son curseur. */
@Schema(
    description =
        "Une page de factures : brouillons d'abord, puis date d'émission décroissante, puis"
            + " identifiant.")
public record InvoicePageDto(
    @Schema(description = "Factures de la page.") List<InvoiceSummaryDto> items,
    @Schema(
            description =
                "Curseur opaque de la page suivante (paramètre cursor). Null sur la dernière"
                    + " page.")
        String nextCursor) {}
