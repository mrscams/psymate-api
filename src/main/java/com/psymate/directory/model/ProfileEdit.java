package com.psymate.directory.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Colonnes éditables d'un profil, telles que le praticien les envoie. Implémentée par {@code
 * UpdateProfileRequest} : {@link PractitionerProfile} et {@link ProfileValidator} lisent la
 * modification sans dépendre de dto/ (G Q33, model ne dépend que de model et d'api).
 */
public interface ProfileEdit {

  String displayName();

  String titleLabel();

  Boolean showProfessionalNumber();

  String bio();

  String city();

  String postalCode();

  String country();

  String contactPhone();

  String contactEmail();

  String websiteUrl();

  String address();

  Boolean showPhone();

  Boolean showEmail();

  Boolean showAddress();

  String responseDelay();

  String fees();

  String availability();

  LocalDate nextSlotAt();

  List<String> specialtiesOrEmpty();

  List<String> audiencesOrEmpty();

  List<String> modalitiesOrEmpty();

  List<String> languagesOrEmpty();
}
