package com.psymate.identity.audit;

import com.psymate.identity.repository.SecurityEventJpaRepository;
import com.psymate.shared.web.IpAddresses;
import jakarta.annotation.PreDestroy;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Écrit le journal d'audit applicatif (qui a lu ou modifié quel patient) dans {@code
 * security_event} (E3.2, G Q29 : un seul journal, pas de nouvelle table).
 *
 * <p>Ce composant n'y fait que des INSERT, par le fragment natif de {@link
 * SecurityEventJpaRepository}, jamais de lecture, de modification ni de suppression (la table est
 * en ajout seul pour le rôle applicatif). Les lignes d'audit se distinguent des autres événements
 * de sécurité d'identity par {@code kind} = {@code patient.read} ou {@code patient.write}.
 *
 * <p>Écriture asynchrone : un pool privé d'un thread daemon à file bornée (10 000), comme le relais
 * de contact du module directory (pas de {@code @EnableAsync}, voir ContactRequestService). Si la
 * file est pleine, l'appelant écrit lui-même ({@code CallerRunsPolicy}) : la file pleine ne fait
 * perdre aucune ligne. Une ligne n'est perdue que si l'INSERT échoue (journalisé en WARN) ou si
 * elle arrive après l'arrêt du pool ; rien n'est jamais propagé : l'audit ne fait jamais échouer
 * une requête.
 */
public class AuditWriter {

  private static final Logger LOG = LoggerFactory.getLogger(AuditWriter.class);
  private static final int MAX_USER_AGENT = 500;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final SecurityEventJpaRepository events;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(10_000),
          r -> {
            Thread t = new Thread(r, "audit-writer");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.CallerRunsPolicy());

  public AuditWriter(SecurityEventJpaRepository events) {
    this.events = events;
  }

  /** Planifie l'écriture hors du thread de la requête ; ne lève jamais d'exception. */
  public void publish(AuditEvent event) {
    try {
      executor.execute(() -> writeQuietly(event));
    } catch (RuntimeException e) {
      LOG.warn("Ligne d'audit non planifiée ({})", event.kind(), e);
    }
  }

  /** Écriture synchrone d'une ligne (INSERT seul) ; lève l'exception de la base en cas d'échec. */
  public void write(AuditEvent event) {
    // Instant non tronqué : le pilote l'arrondit à la microseconde, comme avant M9-05.
    events.insertAuditEvent(
        UUID.randomUUID(),
        event.practitionerId(),
        event.kind(),
        IpAddresses.normalize(event.ip()),
        truncate(event.userAgent()),
        JSON.writeValueAsString(event.details()),
        event.occurredAt().atOffset(ZoneOffset.UTC));
  }

  private void writeQuietly(AuditEvent event) {
    try {
      write(event);
    } catch (RuntimeException e) {
      LOG.warn("Ligne d'audit non écrite ({})", event.kind(), e);
    }
  }

  private static String truncate(String userAgent) {
    if (userAgent == null) {
      return null;
    }
    return userAgent.length() <= MAX_USER_AGENT
        ? userAgent
        : userAgent.substring(0, MAX_USER_AGENT);
  }

  /** Arrêt propre : les lignes déjà en file sont écrites (5 s au plus) avant la fermeture. */
  @PreDestroy
  void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
