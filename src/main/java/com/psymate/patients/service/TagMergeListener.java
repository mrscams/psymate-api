package com.psymate.patients.service;

import com.psymate.events.api.EventDeduplicator;
import com.psymate.vocabulary.api.TagsMerged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Réécrit {@code patient_tag} après une fusion de tags (M9-03). Traitement hors requête, donc sans
 * praticien connecté : le praticien vient de l'événement. Idempotent de deux façons :
 * EventDeduplicator.once écarte un rejeu de la même publication, et {@link
 * PatientTagRelabelService} ne touche que les tags encore archivés.
 */
@Component
class TagMergeListener {

  static final String LISTENER_ID = "patients.tags-merged";

  private final PatientTagRelabelService relabel;
  private final EventDeduplicator deduplicator;

  TagMergeListener(PatientTagRelabelService relabel, EventDeduplicator deduplicator) {
    this.relabel = relabel;
    this.deduplicator = deduplicator;
  }

  @ApplicationModuleListener(id = LISTENER_ID)
  void on(TagsMerged event) {
    deduplicator.once(
        event, LISTENER_ID, () -> relabel.relabel(event.getTenantId(), event.getPractitionerId()));
  }
}
