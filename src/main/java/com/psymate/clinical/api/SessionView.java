package com.psymate.clinical.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Séance telle que l'en-tête patient l'affiche (« en cours depuis n min », « dernière séance : 17
 * oct. »).
 *
 * @param kind intake, session ou contact
 * @param modality in_person, video, phone ou email
 * @param occurredFrom début de la séance
 * @param closedAt nul = séance ouverte
 * @param durationMin nul tant que la séance n'est pas clôturée (ou si le contact n'en a pas)
 */
public record SessionView(
    UUID id,
    UUID patientId,
    String kind,
    String modality,
    Instant occurredFrom,
    Instant closedAt,
    Integer durationMin) {}
