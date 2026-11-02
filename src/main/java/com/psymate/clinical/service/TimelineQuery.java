package com.psymate.clinical.service;

import com.psymate.clinical.model.EntryType;
import java.time.Instant;
import java.util.List;

/**
 * Demande de fenêtre de timeline. Les bornes nulles sont ignorées ; les listes vides ne filtrent
 * pas ; {@code tags} porte des libellés (résolus côté service, un libellé inconnu ne correspond à
 * rien) ; {@code cursor} nul = première page.
 */
public record TimelineQuery(
    Instant from,
    Instant to,
    List<EntryType> types,
    List<String> emotions,
    List<String> tags,
    boolean draftsOnly,
    boolean includePrivate,
    TimelineCursor cursor,
    int limit) {}
