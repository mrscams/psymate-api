package com.psymate.clinical.service;

import java.time.Instant;

/**
 * Contact hors rendez-vous (appel, e-mail), consigné comme une séance immédiatement clôturée.
 *
 * @param modality phone ou email
 * @param occurredFrom date du contact, obligatoire
 * @param body texte du contact, obligatoire : il tient lieu de compte rendu
 * @param durationMin durée facultative en minutes
 */
public record ContactCommand(
    String modality, Instant occurredFrom, String body, Integer durationMin) {}
