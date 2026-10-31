package com.psymate.directory;

import java.time.LocalDate;
import java.util.List;

/**
 * Corps de {@code PUT /api/me/profile} : remplace toutes les colonnes éditables. Les champs absents
 * prennent leur valeur par défaut (listes vides, interrupteurs à faux, disponibilité inconnue).
 * {@code published}, {@code slug} et {@code photo} ne s'éditent pas ici.
 */
record UpdateProfileRequest(
    String displayName,
    String titleLabel,
    Boolean showProfessionalNumber,
    String bio,
    List<String> specialties,
    List<String> audiences,
    List<String> modalities,
    List<String> languages,
    String city,
    String postalCode,
    String country,
    String contactPhone,
    String contactEmail,
    String websiteUrl,
    String address,
    Boolean showPhone,
    Boolean showEmail,
    Boolean showAddress,
    String responseDelay,
    String fees,
    String availability,
    LocalDate nextSlotAt) {

  List<String> specialtiesOrEmpty() {
    return specialties == null ? List.of() : specialties;
  }

  List<String> audiencesOrEmpty() {
    return audiences == null ? List.of() : audiences;
  }

  List<String> modalitiesOrEmpty() {
    return modalities == null ? List.of() : modalities;
  }

  List<String> languagesOrEmpty() {
    return languages == null ? List.of() : languages;
  }
}
