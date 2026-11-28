package com.psymate.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Façade de lecture d'un praticien pour les autres modules. */
public interface PractitionerFacade {

  Optional<PractitionerView> find(UUID id);

  /** Numéro professionnel (ADELI / RPPS) déclaré, s'il existe. */
  Optional<String> professionalNumber(UUID id);

  /** Adresse e-mail du compte du praticien, si le praticien existe. */
  Optional<String> emailOf(UUID id);

  /**
   * Identité d'émetteur du praticien, telle que l'imprime une facture (M15-03, G Q85) : nom,
   * profession, numéro professionnel, adresse du cabinet, téléphone et e-mail du compte. Lecture
   * seule, vide si le praticien est inconnu.
   */
  Optional<IssuerView> issuerOf(UUID id);

  /**
   * Vrai si {@code password} est le mot de passe actuel du compte (M15-03, G Q95 : confirmation
   * d'une opération sensible, par exemple le changement d'IBAN). Même encodeur que la connexion et
   * le changement de mot de passe. Aucun effet de bord : ni compteur de tentatives, ni verrou, ni
   * événement de sécurité, et le mot de passe n'est jamais journalisé ; limiter les essais revient
   * à l'appelant. Faux pour un praticien inconnu, un compte sans mot de passe ou un mot de passe
   * vide.
   */
  boolean passwordMatches(UUID id, CharSequence password);

  /**
   * Lecture par lot (un seul SELECT). Les identifiants inconnus sont absents de la map ; {@code
   * preferences} est vide dans cette vue allégée.
   */
  Map<UUID, PractitionerView> findAll(Collection<UUID> ids);

  /** Préférences effectives (valeurs stockées complétées par les défauts), vide si inconnu. */
  Map<String, Object> preferencesOf(UUID id);

  /** Vue minimale d'un praticien. {@code preferences} : clés plates (ADR-11). */
  record PractitionerView(
      UUID id,
      UUID tenantId,
      String profession,
      String verificationStatus,
      Map<String, Object> preferences) {}

  /**
   * Identité d'émetteur d'un praticien (M15-03). {@code displayName} est le nom affiché du compte
   * (« Dr Maya Kaddour ») ; {@code profession} est le code de profession ({@code psychologist},
   * {@code psychiatrist}, {@code coach}). {@code professionalNumber}, l'adresse et {@code phone}
   * sont nuls quand le praticien ne les a pas renseignés ; {@code email} est l'adresse du compte.
   * Ni le haché du mot de passe ni aucune donnée de sécurité n'y figurent.
   */
  record IssuerView(
      UUID id,
      String firstName,
      String lastName,
      String displayName,
      String profession,
      String professionalNumber,
      String addressStreet,
      String addressPostalCode,
      String addressCity,
      String phone,
      String email) {}
}
