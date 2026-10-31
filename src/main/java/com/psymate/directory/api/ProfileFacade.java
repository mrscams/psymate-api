package com.psymate.directory.api;

import java.util.Optional;
import java.util.UUID;

/** Façade de lecture du profil public d'un praticien pour les autres modules. */
public interface ProfileFacade {

  Optional<ProfileSummary> find(UUID practitionerId);

  /** Vue minimale : publication et URL publique. */
  record ProfileSummary(UUID practitionerId, String slug, boolean published) {}
}
