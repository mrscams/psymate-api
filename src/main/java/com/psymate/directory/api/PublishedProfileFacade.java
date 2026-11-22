package com.psymate.directory.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture d'une fiche publiée de l'annuaire par un autre module (G Q71 : la prise de rendez-vous en
 * ligne d'agenda). Une vue n'est rendue que si la fiche est publiée <b>et</b> le praticien approuvé
 * ({@code verificationStatus = approved}, lu par {@code PractitionerFacade}, comme G Q48 (2)). Slug
 * inconnu, fiche non publiée, praticien suspendu, rejeté ou clos, argument nul ou vide : {@link
 * Optional#empty()}, sans distinction (G Q78).
 */
public interface PublishedProfileFacade {

  /** Fiche publiée d'un praticien approuvé, par slug exact. */
  Optional<PublishedProfileView> bySlug(String slug);

  /** Fiche publiée d'un praticien approuvé, par identifiant du praticien. */
  Optional<PublishedProfileView> byPractitioner(UUID practitionerId);
}
