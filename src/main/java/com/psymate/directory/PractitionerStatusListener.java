package com.psymate.directory;

import com.psymate.identity.api.PractitionerRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Retire l'annuaire public d'un praticien dont le dossier est refusé (A10.3, M5-06). Traitement
 * hors requête, donc sans praticien connecté, et idempotent : la dépublication ne touche une ligne
 * que si le profil est encore publié, si bien qu'un rejeu de l'événement n'émet pas de second
 * ProfileUnpublished.
 *
 * <p>PractitionerSuspended (M1-14b) à ajouter quand l'événement existera : même effet.
 */
@Component
class PractitionerStatusListener {

  private final ProfileService profiles;

  PractitionerStatusListener(ProfileService profiles) {
    this.profiles = profiles;
  }

  @ApplicationModuleListener(id = "directory.practitioner-rejected")
  void on(PractitionerRejected event) {
    profiles.unpublishByPractitioner(event.getTenantId(), event.getPractitionerId());
  }
}
