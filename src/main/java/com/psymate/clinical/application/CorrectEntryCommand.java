package com.psymate.clinical.application;

import com.psymate.clinical.domain.EntryStatus;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.domain.MedicationDetail;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.TimeSpec;
import com.psymate.clinical.domain.Visibility;

/**
 * Correction d'une entrée : un champ nul est inchangé ; un détail fourni remplace le détail courant
 * en entier.
 *
 * @param reason motif de la correction, conservé dans la version (ignoré en période de grâce)
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
    String reason) {}
