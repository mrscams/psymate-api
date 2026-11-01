package com.psymate.clinical.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Lecture allégée d'une entrée pour le graphe (linking) : de quoi dessiner un nœud sans charger la
 * version complète ni le détail.
 *
 * @param title titre de la version courante, nul si elle n'en a pas
 * @param intensity intensité 0..10, non nulle seulement pour un ressenti
 * @param emotionCode code d'émotion, non nul seulement pour un ressenti
 * @param authorType practitioner en V1 (A11), en lecture seule
 * @param archived l'entrée est archivée (archived_at renseigné)
 */
public record EntrySummary(
    UUID id,
    UUID patientId,
    String type,
    String title,
    Instant occurredFrom,
    Instant occurredTo,
    Integer intensity,
    String emotionCode,
    String authorType,
    boolean archived) {}
