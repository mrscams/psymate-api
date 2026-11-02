package com.psymate.clinical.service;

import com.psymate.clinical.model.EntryStatus;
import com.psymate.clinical.model.FeelingDetail;
import com.psymate.clinical.model.MedicationDetail;
import com.psymate.clinical.model.SessionDetail;
import com.psymate.clinical.model.TimeSpec;
import com.psymate.clinical.model.Visibility;
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
