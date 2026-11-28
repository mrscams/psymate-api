package com.psymate.billing.repository;

import com.psymate.billing.model.BillingInvoiceRef;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * SQL natif des séances à facturer (G Q38 : fragment Spring Data sur l'{@code EntityManager} du
 * module). Ne lit que les tables de billing : {@code billable_session} et, en lecture seule tant
 * que les factures n'ont pas d'entité (M15-08), {@code invoice}.
 */
public interface BillableSessionNativeQueries {

  /**
   * Crée la ligne {@code pending} d'une séance close si elle n'existe pas encore ({@code insert …
   * on conflict do nothing}) : l'écoute de {@code SessionClosed} et la création à la demande (G
   * Q86) peuvent se croiser sans doublon ni erreur de clé, et une ligne existante (dismissed,
   * invoiced) n'est jamais touchée. Exige une transaction en cours.
   *
   * @return 1 si la ligne a été créée, 0 si elle existait déjà
   */
  int insertPendingIfAbsent(
      UUID sessionEntryId,
      UUID tenantId,
      UUID practitionerId,
      UUID patientId,
      LocalDate occurredOn,
      String modality,
      Integer durationMin);

  /**
   * Nombre de brouillons de facture ({@code kind = 'invoice'}, {@code status = 'draft'}) de chaque
   * patient du praticien ; un patient sans brouillon est absent de la map. Collection vide : map
   * vide, sans requête.
   */
  Map<UUID, Integer> openDraftCounts(UUID practitionerId, Collection<UUID> patientIds);

  /**
   * Dernière facture émise de chaque patient du praticien ({@code kind = 'invoice'}, {@code status}
   * {@code issued} ou {@code void}), par date d'émission puis instant d'émission ; un patient sans
   * facture émise est absent de la map. Collection vide : map vide, sans requête.
   */
  Map<UUID, BillingInvoiceRef> lastInvoices(UUID practitionerId, Collection<UUID> patientIds);
}
