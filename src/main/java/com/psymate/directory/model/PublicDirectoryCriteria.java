package com.psymate.directory.model;

import java.util.List;

/** Critères de recherche ; la profession est filtrée par le service (elle vit dans identity). */
public record PublicDirectoryCriteria(
    String q,
    String city,
    Double lat,
    Double lng,
    double radiusKm,
    List<String> modalities,
    List<String> audiences,
    List<String> languages,
    boolean accepting,
    String sort) {}
