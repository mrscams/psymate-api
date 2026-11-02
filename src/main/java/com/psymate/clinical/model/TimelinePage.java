package com.psymate.clinical.model;

import java.util.List;

/**
 * Une page de timeline : éléments triés par occurred_from décroissant, curseur de la suite (nul sur
 * la dernière page), facettes de la fenêtre et situations qui chevauchent la fenêtre.
 */
public record TimelinePage(
    List<TimelineItem> items,
    String nextCursor,
    TimelineFacets facets,
    List<TimelineItem> ongoingSituations) {}
