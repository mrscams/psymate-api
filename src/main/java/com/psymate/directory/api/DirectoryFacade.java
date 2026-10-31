package com.psymate.directory.api;

import java.util.Optional;

/** Façade de lecture de l'annuaire public pour les autres modules. */
public interface DirectoryFacade {

  /** Vue minimale d'un profil publié ; vide si le slug est inconnu ou le profil non publié. */
  Optional<PublishedProfile> findPublished(String slug);

  record PublishedProfile(String slug, String displayName, String city) {}
}
