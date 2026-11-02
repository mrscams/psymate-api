package com.psymate.clinical.service;

import com.psymate.clinical.model.EntryStatus;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.FeelingDetail;
import com.psymate.clinical.model.MedicationDetail;
import com.psymate.clinical.model.SessionDetail;
import com.psymate.clinical.model.TimeSpec;
import com.psymate.clinical.model.Visibility;
import java.util.List;
import java.util.UUID;

/**
 * Création d'une entrée. Pas de champ authorType ni submissionId : ils ne s'écrivent jamais (A11).
 * Le détail utilisé est celui du type ; les autres sont ignorés.
 *
 * @param sessionId séance de rattachement : obligatoire sauf pour le type session
 * @param time nul : instant exact de la saisie (obligatoire pour une situation)
 * @param visibility nul : clinical
 * @param status nul : final
 * @param tagIds tags du praticien (déjà résolus) à poser sur l'entrée ; nul ou vide : aucun
 */
public record CreateEntryCommand(
    EntryType type,
    UUID sessionId,
    String title,
    String body,
    Visibility visibility,
    EntryStatus status,
    TimeSpec time,
    FeelingDetail feeling,
    MedicationDetail medication,
    SessionDetail session,
    List<UUID> tagIds) {

  /** Sans tags. */
  public CreateEntryCommand(
      EntryType type,
      UUID sessionId,
      String title,
      String body,
      Visibility visibility,
      EntryStatus status,
      TimeSpec time,
      FeelingDetail feeling,
      MedicationDetail medication,
      SessionDetail session) {
    this(
        type,
        sessionId,
        title,
        body,
        visibility,
        status,
        time,
        feeling,
        medication,
        session,
        List.of());
  }
}
