package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Séances à facturer d'un patient (L5.2). Montants en centimes d'euro entiers (G Q84). */
@Schema(description = "Séances à facturer d'un patient, par date.")
public record BillableSessionGroupDto(
    @Schema(description = "Patient du groupe.") BillingPatientRefDto patient,
    @Schema(
            description =
                "Tarif commun du patient en centimes d'euro : son tarif, sinon le prix de la"
                    + " prestation par défaut. Null s'il n'y en a aucun. Ne tient pas compte d'un"
                    + " montant fixé sur une séance (voir amount et rateSource de chaque séance).",
            minimum = "1",
            example = "6000")
        Integer rate,
    @Schema(
            description =
                "session si au moins une séance du groupe a un montant fixé à la main ; sinon"
                    + " l'origine de rate : patient, service ou none (G Q87).",
            allowableValues = {"session", "patient", "service", "none"},
            example = "patient")
        String rateSource,
    @Schema(
            description = "Nombre de brouillons de facture en cours pour ce patient.",
            example = "0")
        int openDraftCount,
    @Schema(description = "Dernière facture émise du patient ; null s'il n'en a pas.")
        BillingLastInvoiceDto lastInvoice,
    @Schema(description = "Séances à facturer du patient, de la plus ancienne à la plus récente.")
        List<BillableSessionDto> sessions) {}
