package com.psymate.billing.model;

import java.time.LocalDate;

/**
 * Dernière facture émise d'un patient, telle que la liste des séances à facturer la montre (L5.2
 * {@code lastInvoice}) : son numéro et sa date d'émission.
 */
public record BillingInvoiceRef(String number, LocalDate issueDate) {}
