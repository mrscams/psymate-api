package com.psymate.clinical.application;

import com.psymate.clinical.domain.Entry;
import com.psymate.clinical.domain.EntryVersion;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.domain.MedicationDetail;
import com.psymate.clinical.domain.SessionDetail;
import java.time.Instant;
import java.util.List;

/**
 * Lecture complète d'une entrée : ligne commune, version courante, détail du type, tags.
 *
 * @param emotionFamily famille de l'émotion du ressenti d'après le vocabulaire ; nulle si absente
 * @param tags libellés des tags actifs de l'entrée, triés
 */
public record EntryDetails(
    Entry entry,
    EntryVersion version,
    Instant recordedAt,
    FeelingDetail feeling,
    String emotionFamily,
    MedicationDetail medication,
    SessionDetail session,
    List<String> tags) {}
