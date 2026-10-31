package com.psymate.clinical.application;

import com.psymate.clinical.domain.TimeSpan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entrée allégée de la timeline.
 *
 * @param title titre de la version courante
 * @param emotionCode code d'émotion (ressenti seulement), sinon nul
 * @param emotionFamily famille de l'émotion d'après le catalogue, nulle si inconnue
 * @param intensity intensité du ressenti, sinon nulle
 * @param tags libellés des tags actifs, triés sans casse
 * @param ongoing situation sans fin
 */
public record TimelineItem(
    UUID id,
    String type,
    TimeSpan occurred,
    String title,
    String emotionCode,
    String emotionFamily,
    Integer intensity,
    List<String> tags,
    int linkCount,
    UUID sessionId,
    String status,
    String visibility,
    String authorType,
    Instant recordedAt,
    boolean ongoing) {}
