package com.psymate.billing.model;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Critères de la liste des factures (GET /api/invoices, L5.3), déjà validés.
 *
 * @param status statut exposé demandé (draft, due, partial, paid, void), null = tous
 * @param overdue filtre sur le retard, null = sans filtre
 * @param kind invoice ou credit_note, null = les deux
 * @param patientId un seul patient (déjà vérifié au praticien), null = tous
 * @param pattern motif {@code like} normalisé (minuscules, sans accents, jokers échappés par {@code
 *     !}) cherché dans le numéro et le nom du destinataire ; null sans recherche
 * @param matchingPatientIds patients du praticien dont le nom affiché contient la recherche (façade
 *     des patients) ; vide s'il n'y en a pas, ignoré sans recherche
 * @param from date d'émission minimale incluse, null = sans limite
 * @param after position du curseur (dernier élément de la page précédente), null = première page
 * @param limit taille de la page
 * @param today aujourd'hui au jour de Europe/Paris (retard)
 * @param draftVatRateBp taux de TVA des réglages du moment en régime assujetti, null sinon (total
 *     des brouillons)
 */
public record InvoiceListCriteria(
    String status,
    Boolean overdue,
    String kind,
    UUID patientId,
    String pattern,
    Set<UUID> matchingPatientIds,
    LocalDate from,
    InvoiceCursor after,
    int limit,
    LocalDate today,
    Integer draftVatRateBp) {

  public InvoiceListCriteria {
    matchingPatientIds = matchingPatientIds == null ? Set.of() : Set.copyOf(matchingPatientIds);
  }
}
