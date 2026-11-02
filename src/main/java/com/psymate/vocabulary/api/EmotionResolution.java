package com.psymate.vocabulary.api;

import java.util.Set;

/**
 * Ce que doivent porter, maintenant, les ressentis issus d'une émotion personnalisée déjà fusionnée
 * au moins une fois (M9-03) : {@code currentCode} est la cible finale des fusions actives depuis
 * {@code sourceCode}, ou {@code sourceCode} lui-même si ses fusions ont été annulées. {@code
 * historicalCodes} réunit la source et tous les codes qu'elle a pu porter par une fusion, active ou
 * annulée : un ressenti dont l'émotion d'origine est la source et dont l'émotion courante est hors
 * de cet ensemble a été corrigé à la main, et ne se réécrit pas.
 */
public record EmotionResolution(
    String sourceCode, String currentCode, Set<String> historicalCodes) {}
