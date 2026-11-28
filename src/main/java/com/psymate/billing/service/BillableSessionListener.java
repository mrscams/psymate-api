package com.psymate.billing.service;

import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.SessionClosed;
import com.psymate.events.api.EventDeduplicator;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Séances à facturer tenues par les événements de clinical (G Q86) : une séance de kind session
 * close devient {@code pending} ({@code SessionClosed}) ; une séance archivée encore {@code
 * pending} devient {@code dismissed} ({@code EntryArchived}). Traitement hors requête, donc sans
 * praticien connecté. Idempotent de deux façons : {@link EventDeduplicator#once} écarte le rejeu
 * d'une même publication, et l'effet lui-même ne crée jamais de doublon (insertion {@code on
 * conflict do nothing}) ni ne réveille une ligne écartée ou facturée.
 *
 * <p>Aucune reprise des séances closes avant la mise en service : seules les clôtures publiées
 * créent une ligne (et la création à la demande, G Q86). La restauration d'une séance archivée
 * (sans événement) ne remet pas sa ligne à facturer : le praticien la remet lui-même.
 */
@Component
class BillableSessionListener {

  static final String SESSION_CLOSED_ID = "billing.session-closed";
  static final String ENTRY_ARCHIVED_ID = "billing.entry-archived";

  /** Type d'entrée d'une séance dans {@link EntryArchived#getEntryType()}. */
  static final String SESSION_ENTRY_TYPE = "session";

  private final BillableSessionService sessions;
  private final EventDeduplicator deduplicator;

  BillableSessionListener(BillableSessionService sessions, EventDeduplicator deduplicator) {
    this.sessions = sessions;
    this.deduplicator = deduplicator;
  }

  @ApplicationModuleListener(id = SESSION_CLOSED_ID)
  void on(SessionClosed event) {
    deduplicator.once(event, SESSION_CLOSED_ID, () -> sessions.recordClosed(event));
  }

  @ApplicationModuleListener(id = ENTRY_ARCHIVED_ID)
  void on(EntryArchived event) {
    if (!SESSION_ENTRY_TYPE.equals(event.getEntryType())) {
      return;
    }
    deduplicator.once(event, ENTRY_ARCHIVED_ID, () -> sessions.dismissArchived(event.getEntryId()));
  }
}
