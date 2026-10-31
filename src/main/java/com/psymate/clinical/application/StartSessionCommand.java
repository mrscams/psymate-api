package com.psymate.clinical.application;

import java.time.Instant;

/**
 * Démarrage d'une séance.
 *
 * @param modality in_person, video, phone ou email
 * @param occurredFrom début réel de la séance ; nul : maintenant
 */
public record StartSessionCommand(String modality, Instant occurredFrom) {}
