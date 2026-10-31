package com.psymate.clinical.application;

import com.psymate.clinical.domain.EntryStatus;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.domain.MedicationDetail;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.TimeSpec;
import com.psymate.clinical.domain.Visibility;
import java.util.List;
import java.util.UUID;

/**
 * Correction d'une entrée : un champ nul est inchangé ; un détail fourni remplace le détail courant
 * en entier.
 *
 * @param reason motif de la correction, conservé dans la version (ignoré en période de grâce)
 * @param tagIds tags du praticien (déjà résolus) qui remplacent ceux de l'entrée ; nul : inchangés.
 *     Les tags vivent sur l'entrée, pas dans entry_version : les changer seul ne crée pas de
 *     version.
 */
public record CorrectEntryCommand(
    String title,
    String body,
    Visibility visibility,
    EntryStatus status,
    TimeSpec time,
    FeelingDetail feeling,
    MedicationDetail medication,
    SessionDetail session,
    String reason,
    List<UUID> tagIds) {

  /** Sans changement de tags. */
  public CorrectEntryCommand(
      String title,
      String body,
      Visibility visibility,
      EntryStatus status,
      TimeSpec time,
      FeelingDetail feeling,
      MedicationDetail medication,
      SessionDetail session,
      String reason) {
    this(title, body, visibility, status, time, feeling, medication, session, reason, null);
  }
}
