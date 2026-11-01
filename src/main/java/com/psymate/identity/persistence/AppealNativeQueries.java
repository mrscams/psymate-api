package com.psymate.identity.persistence;

import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectures natives des recours de l'administration : jointure practitioner_appeal, {@code
 * verification_decision} et practitioner, curseur keyset. Elles restent en SQL (G Q32) ;
 * l'EntityManager garantit l'auto-flush d'un recours modifié dans la même transaction.
 */
public interface AppealNativeQueries {

  /**
   * Page de recours (colonnes : id, practitioner_id, display_name, decision_id, decision_kind,
   * decision_admin_id, message, submitted_at, reviewed_at, reviewed_by, outcome, response_message,
   * sort_key). open : non examinés, du plus ancien au plus récent ; decided et all : du plus récent
   * au plus ancien.
   *
   * @param cursorKey clé de tri du curseur en millisecondes, null pour la première page
   */
  List<Tuple> appealPage(String status, Long cursorKey, UUID cursorId, int limit);

  /** Un recours avec sa décision et son praticien, mêmes colonnes que {@link #appealPage}. */
  Optional<Tuple> appealRow(UUID id);
}
