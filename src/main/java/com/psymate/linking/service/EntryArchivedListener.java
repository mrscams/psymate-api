package com.psymate.linking.service;

import com.psymate.clinical.api.EntryArchived;
import com.psymate.events.api.EventDeduplicator;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Passe superseded les liens vivants d'une entrée archivée (A7 : accepted vers superseded,
 * archivage d'une entrée). Traitement hors requête, donc sans praticien connecté. Idempotent de
 * deux façons : EventDeduplicator.once écarte un rejeu de la même publication, et LinkService ne
 * touche que les liens encore vivants.
 *
 * <p>La restauration de l'entrée (EntryService.restore, sans événement) ne rétablit pas les liens :
 * ils restent superseded, il faut les poser de nouveau.
 */
@Component
class EntryArchivedListener {

  static final String LISTENER_ID = "linking.entry-archived";

  private final LinkService links;
  private final EventDeduplicator deduplicator;

  EntryArchivedListener(LinkService links, EventDeduplicator deduplicator) {
    this.links = links;
    this.deduplicator = deduplicator;
  }

  @ApplicationModuleListener(id = LISTENER_ID)
  void on(EntryArchived event) {
    deduplicator.once(event, LISTENER_ID, () -> links.supersedeLinksOf(event.getEntryId()));
  }
}
