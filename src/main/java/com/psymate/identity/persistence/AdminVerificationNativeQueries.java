package com.psymate.identity.persistence;

import jakarta.persistence.Tuple;
import java.util.List;
import java.util.UUID;

/**
 * Lectures natives de la file de vérification des dossiers (sous-requêtes corrélées sur
 * security_event et verification_document, {@code string_agg}, curseur keyset). Elles restent en
 * SQL : une projection de plusieurs tables n'est pas une entité (G Q32). Passer par l'EntityManager
 * plutôt qu'un client JDBC déclenche l'auto-flush des entités modifiées dans la transaction.
 */
public interface AdminVerificationNativeQueries {

  /**
   * Page de la file (colonnes : id, display_name, profession, address_city, documents_count,
   * present_types, submitted_at, sort_key). La clé de tri est la date de soumission, ou updated_at
   * du praticien à défaut, tronquée à la milliseconde.
   *
   * @param cursorKey clé de tri du curseur en millisecondes, null pour la première page
   */
  List<Tuple> adminQueue(
      String status, boolean oldestFirst, Long cursorKey, UUID cursorId, int limit);

  /**
   * Dossiers under_review (colonnes : display_name, profession, submitted_at), plus anciens
   * d'abord.
   */
  List<Tuple> adminUnderReview();
}
