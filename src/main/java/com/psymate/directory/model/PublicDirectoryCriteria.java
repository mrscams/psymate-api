package com.psymate.directory.model;

import java.util.List;

/**
 * Critères de recherche ; la profession est filtrée par le service (elle vit dans identity). {@code
 * specialties} est vide ou null quand le filtre de spécialité est absent.
 */
public record PublicDirectoryCriteria(
    String q,
    String city,
    Double lat,
    Double lng,
    double radiusKm,
    List<String> specialties,
    List<String> modalities,
    List<String> audiences,
    List<String> languages,
    boolean accepting,
    String sort) {

  /** Vrai si au moins une spécialité est demandée. */
  public boolean hasSpecialties() {
    return specialties != null && !specialties.isEmpty();
  }

  /** Mêmes critères sans le filtre de spécialité (base de calcul de la facette). */
  public PublicDirectoryCriteria withoutSpecialties() {
    return new PublicDirectoryCriteria(
        q, city, lat, lng, radiusKm, List.of(), modalities, audiences, languages, accepting, sort);
  }
}
