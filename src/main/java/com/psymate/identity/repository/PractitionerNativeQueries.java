package com.psymate.identity.repository;

import java.util.UUID;

/**
 * Écritures jsonb ciblées : l'opérateur {@code -} retire une clé sans réécrire le document, donc
 * sans écraser un PATCH /preferences concurrent (une réécriture du Map entier le ferait).
 */
public interface PractitionerNativeQueries {

  /** Retire l'obligation de changer de mot de passe (compte de bootstrap, M7-08). */
  void clearMustChangePassword(UUID practitionerId);
}
