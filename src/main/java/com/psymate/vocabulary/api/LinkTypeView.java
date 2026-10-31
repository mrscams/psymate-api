package com.psymate.vocabulary.api;

import java.util.List;

/** Type de lien avec sa matrice d'autorisation (codes d'entry_type). */
public record LinkTypeView(
    String code,
    boolean directed,
    String labelFr,
    String labelEn,
    String inverseLabelFr,
    String inverseLabelEn,
    List<String> allowedSourceTypes,
    List<String> allowedTargetTypes,
    String lineStyle,
    int sortOrder) {}
