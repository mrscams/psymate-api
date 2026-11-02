package com.psymate.clinical.export.repository;

import jakarta.persistence.Tuple;
import java.util.List;
import java.util.UUID;

/**
 * Lecture native du dossier à exporter (G Q38) : {@code entry}, {@code entry_version} et les tables
 * de détail, qui n'ont pas d'entité (clinical lit en SQL). Fragment Spring Data de {@link
 * ExportJobJpaRepository} ; colonnes lues par alias, mises en records par {@link ExportRepository}.
 *
 * <p>Les filtres de confidentialité sont ici et nulle part ailleurs : {@code visibility =
 * 'clinical'} (A4.7, une entrée privée n'est jamais lue, ni son titre, ni son corps, ni ses
 * versions) et {@code archived_at is null}. Le texte vient de la version courante seulement ({@code
 * entry.current_version_id}) : aucune ancienne version n'est jointe.
 */
interface ExportNativeQueries {

  /**
   * Séances non archivées et cliniques, de la plus ancienne à la plus récente (colonnes : id,
   * occurred_from, occurred_to, occurred_precision, occurred_label, kind, modality, duration_min,
   * title, body).
   */
  List<Tuple> exportableSessions(UUID patientId);

  /**
   * Entrées (hors séances) non archivées et cliniques, de la plus ancienne à la plus récente, avec
   * leurs détails typés (colonnes : id, session_id, type, status, occurred_from, occurred_to,
   * occurred_precision, occurred_label, title, body, emotion_code, intensity, molecule, dosage,
   * frequency, period_from, period_to).
   */
  List<Tuple> exportableEntries(UUID patientId);

  /** Tags posés sur les entrées exportables du patient (colonnes : entry_id, tag_id). */
  List<Tuple> exportableEntryTags(UUID patientId);
}
