package com.psymate.identity.repository;

import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectures natives de la liste et du détail des comptes pour l'administration (date de soumission
 * corrélée sur security_event, recherche ILIKE échappée, curseur keyset, {@code
 * checklist->>'previousStatus'}). Elles restent en SQL (G Q32) ; l'EntityManager garantit
 * l'auto-flush des entités modifiées dans la transaction.
 */
public interface AdminPractitionerNativeQueries {

  /**
   * Page de comptes triée par création décroissante, puis identifiant décroissant (colonnes : id,
   * display_name, email, profession, address_city, role, verification_status, verified_at,
   * submitted_at, updated_at, totp, created_at, last_login_at, suspended_at, purge_after,
   * sort_key). Un critère null est ignoré ; {@code q} est le texte brut de la recherche.
   *
   * @param cursorKey clé de tri du curseur en millisecondes, null pour la première page
   */
  List<Tuple> adminAccounts(
      String q,
      String status,
      String role,
      String profession,
      Long cursorKey,
      UUID cursorId,
      int limit);

  /** Nombre de comptes par statut, hors filtre de statut (statuts absents omis). */
  Map<String, Integer> adminStatusCounts(String q, String role, String profession);

  /** Un compte, mêmes colonnes que {@link #adminAccounts}. */
  Optional<Tuple> adminAccount(UUID id);

  /**
   * Statut que le compte avait avant sa dernière suspension manuelle (checklist de la décision).
   */
  Optional<String> previousStatusBeforeSuspension(UUID practitionerId);
}
