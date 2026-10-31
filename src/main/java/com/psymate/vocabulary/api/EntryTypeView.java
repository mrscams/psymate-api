package com.psymate.vocabulary.api;

/** Type d'entrée (note, event, situation, feeling, session, medication). */
public record EntryTypeView(
    String code,
    String labelFr,
    String labelEn,
    String detailTable,
    boolean hasDuration,
    int sortOrder) {}
