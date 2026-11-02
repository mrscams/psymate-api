package com.psymate.clinical.service;

/**
 * Clôture d'une séance.
 *
 * @param durationMin durée en minutes ; nul : préférence sessions.defaultDurationMin du praticien
 *     (60 à défaut)
 * @param summaryBody compte rendu de séance, facultatif
 */
public record CloseSessionCommand(Integer durationMin, String summaryBody) {}
