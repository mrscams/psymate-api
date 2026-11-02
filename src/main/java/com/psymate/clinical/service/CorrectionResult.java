package com.psymate.clinical.service;

import java.util.UUID;

/**
 * Résultat d'une correction.
 *
 * @param version version courante après correction
 * @param changed faux si rien n'a changé (aucune écriture)
 * @param versioned vrai si une nouvelle version a été créée (faux : grâce ou rien à changer)
 */
public record CorrectionResult(UUID entryId, int version, boolean changed, boolean versioned) {}
