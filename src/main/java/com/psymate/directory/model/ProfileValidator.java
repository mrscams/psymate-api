package com.psymate.directory.model;

import com.psymate.directory.model.ProfileException.FieldError;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Règles de validation de PUT /api/me/profile ; sans accès base (les codes actifs sont passés). */
public final class ProfileValidator {

  static final Set<String> MODALITIES = Set.of("in_person", "video", "home");
  static final Set<String> AVAILABILITIES = Set.of("accepting", "waitlist", "closed", "unknown");
  static final int MAX_BIO = 1200;
  static final int MAX_SPECIALTIES = 8;
  private static final Set<String> ISO_639_1 = Set.of(Locale.getISOLanguages());

  private ProfileValidator() {}

  public static List<FieldError> validate(
      ProfileEdit r, Set<String> activeSpecialties, Set<String> activeAudiences) {
    List<FieldError> errors = new ArrayList<>();
    if (r.displayName() == null || r.displayName().isBlank()) {
      errors.add(new FieldError("displayName", "Le nom affiché est obligatoire."));
    } else if (r.displayName().length() > 120) {
      errors.add(new FieldError("displayName", "Le nom affiché est limité à 120 caractères."));
    }
    maxLength("titleLabel", r.titleLabel(), 120, errors);
    maxLength("contactPhone", r.contactPhone(), 30, errors);
    maxLength("contactEmail", r.contactEmail(), 254, errors);
    maxLength("websiteUrl", r.websiteUrl(), 300, errors);
    maxLength("address", r.address(), 300, errors);
    maxLength("city", r.city(), 100, errors);
    maxLength("postalCode", r.postalCode(), 20, errors);
    maxLength("responseDelay", r.responseDelay(), 60, errors);
    maxLength("fees", r.fees(), 500, errors);
    if (r.bio() != null && r.bio().length() > MAX_BIO) {
      errors.add(new FieldError("bio", "La présentation est limitée à 1 200 caractères."));
    }
    closedList("specialties", r.specialtiesOrEmpty(), activeSpecialties, errors);
    if (r.specialtiesOrEmpty().size() > MAX_SPECIALTIES) {
      errors.add(new FieldError("specialties", "8 spécialités au maximum."));
    }
    closedList("audiences", r.audiencesOrEmpty(), activeAudiences, errors);
    closedList("modalities", r.modalitiesOrEmpty(), MODALITIES, errors);
    closedList("languages", r.languagesOrEmpty(), ISO_639_1, errors);
    if (r.availability() != null && !AVAILABILITIES.contains(r.availability())) {
      errors.add(
          new FieldError(
              "availability", "Valeurs admises : accepting, waitlist, closed, unknown."));
    }
    if (r.websiteUrl() != null && !r.websiteUrl().isBlank() && !isHttpUrl(r.websiteUrl())) {
      errors.add(
          new FieldError("websiteUrl", "L'adresse du site doit commencer par http ou https."));
    }
    if (r.contactEmail() != null
        && !r.contactEmail().isBlank()
        && !r.contactEmail().matches("^[^@\\s]+@[^@\\s]+$")) {
      errors.add(new FieldError("contactEmail", "Adresse e-mail invalide."));
    }
    if (r.country() != null && !r.country().isBlank() && !r.country().matches("^[A-Za-z]{2}$")) {
      errors.add(new FieldError("country", "Code pays ISO à deux lettres attendu."));
    }
    return errors;
  }

  private static void maxLength(String field, String v, int max, List<FieldError> errors) {
    if (v != null && v.length() > max) {
      errors.add(new FieldError(field, "Limité à " + max + " caractères."));
    }
  }

  private static void closedList(
      String field, List<String> values, Set<String> allowed, List<FieldError> errors) {
    for (String v : values) {
      if (v == null || !allowed.contains(v)) {
        errors.add(new FieldError(field, "Valeur hors liste : " + v));
      }
    }
    if (values.stream().distinct().count() != values.size()) {
      errors.add(new FieldError(field, "Valeurs en double."));
    }
  }

  static boolean isHttpUrl(String s) {
    try {
      URI u = URI.create(s.trim());
      return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))
          && u.getHost() != null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
