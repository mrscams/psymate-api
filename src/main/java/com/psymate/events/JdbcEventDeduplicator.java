package com.psymate.events;

import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.EventDeduplicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Déduplication fondée sur le registre {@code event_publication} : une publication complétée de
 * même {@code listener_id} dont le {@code serialized_event} porte le même champ JSON {@code id}
 * (l'identifiant de {@code DomainEvent}) signifie que l'effet a déjà eu lieu. Couvre la
 * republication du même événement et le rejeu concurrent (verrou advisory transactionnel sur le
 * couple). Ne couvre pas la fenêtre « effet commité, achèvement non encore écrit » : voir {@link
 * EventDeduplicator}.
 *
 * <p>L'identifiant est lu dans le JSON plutôt que par comparaison du texte sérialisé : le
 * sérialiseur de Spring Modulith (module {@code events-core}) n'est disponible qu'à l'exécution,
 * pas à la compilation.
 */
@Component
class JdbcEventDeduplicator implements EventDeduplicator {

  private final JdbcTemplate jdbc;

  JdbcEventDeduplicator(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean once(DomainEvent event, String listenerId, Runnable effect) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "EventDeduplicator.once must run inside the listener transaction");
    }
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> {},
        listenerId + "|" + event.getId());
    Boolean done =
        jdbc.queryForObject(
            "select exists(select 1 from event_publication"
                + " where listener_id = ? and completion_date is not null"
                + " and serialized_event::jsonb ->> 'id' = ?)",
            Boolean.class,
            listenerId,
            event.getId().toString());
    if (Boolean.TRUE.equals(done)) {
      return false;
    }
    effect.run();
    return true;
  }
}
