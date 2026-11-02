package com.psymate.clinical.model;

import java.util.Map;

/** Comptes de toute la fenêtre filtrée, hors curseur, du plus fréquent au plus rare. */
public record TimelineFacets(
    Map<String, Integer> types, Map<String, Integer> emotions, Map<String, Integer> tags) {}
