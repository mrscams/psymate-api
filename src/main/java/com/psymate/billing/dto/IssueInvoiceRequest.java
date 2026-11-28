package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Émission d'un brouillon (POST /api/invoices/{id}/issue, L5.3, G Q89). {@code expectedNumber} est
 * le numéro montré au praticien ({@code nextNumberPreview}) : s'il n'est plus le suivant, rien
 * n'est émis (409 INVOICE_NUMBER_CHANGED). Tout autre champ est refusé (UNKNOWN_FIELD).
 */
@Schema(
    description =
        "Émission d'un brouillon : numéro attendu (celui affiché, nextNumberPreview) et, si la"
            + " facture est déjà réglée, le paiement reçu. Tout autre champ est refusé"
            + " (UNKNOWN_FIELD).")
public record IssueInvoiceRequest(
    @Schema(
            description =
                "Numéro que le praticien a vu (nextNumberPreview du brouillon). S'il n'est plus"
                    + " le suivant de la suite : 409 INVOICE_NUMBER_CHANGED, rien n'est émis.",
            example = "F-2026-0001",
            maxLength = 30,
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 30)
        String expectedNumber,
    @Schema(
            description =
                "Facture déjà réglée : un paiement du total est enregistré dans la même"
                    + " transaction que l'émission. Absent ou null : aucun paiement.")
        @Valid
        InvoiceAlreadyPaidRequest alreadyPaid) {}
