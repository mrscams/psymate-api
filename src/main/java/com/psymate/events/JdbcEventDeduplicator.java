package com.psymate.events;

import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.EventDeduplicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Déduplication fondée sur le registre {@code event_publication} (table du module events, E3.8) :
 * une publication complétée de même {@code listener_id} dont le {@code serialized_event} porte le
 * même champ JSON {@code id} (l'identifiant de {@code DomainEvent}) signifie que l'effet a déjà eu
 * lieu.
 *
 * <p>Déroulement dans la transaction de l'abonné : verrou advisory transactionnel sur le couple
 * (abonné, événement), test d'existence d'une publication complétée, exécution de l'effet, puis
 * marquage {@code COMPLETED} de la publication incomplète du couple dans la même transaction.
 * L'effet et l'achèvement sont donc atomiques : un rejeu après un arrêt du processus ne retrouve
 * plus de publication incomplète, et un rejeu concurrent attend la fin de la transaction puis voit
 * la publication complétée. L'achèvement que Spring Modulith écrit ensuite (après le commit, par
 * id) réécrit la même date : sans effet.
 *
 * <p>L'identifiant est lu dans le JSON plutôt que par comparaison du texte sérialisé : le
 * sérialiseur de Spring Modulith (module {@code events-core}) n'est disponible qu'à l'exécution,
 * pas à la compilation. La requête parcourt les publications de l'abonné (pas d'index dédié), ce
 * que la rétention de {@code app.events.completed-retention} borne.
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
    String eventId = event.getId().toString();
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> {},
        listenerId + "|" + eventId);
    Boolean done =
        jdbc.queryForObject(
            "select exists(select 1 from event_publication"
                + " where listener_id = ? and completion_date is not null"
                + " and serialized_event::jsonb ->> 'id' = ?)",
            Boolean.class,
            listenerId,
            eventId);
    if (Boolean.TRUE.equals(done)) {
      return false;
    }
    effect.run();
    jdbc.update(
        "update event_publication set completion_date = now(), status = 'COMPLETED'"
            + " where listener_id = ? and completion_date is null"
            + " and serialized_event::jsonb ->> 'id' = ?",
        listenerId,
        eventId);
    return true;
  }
}
