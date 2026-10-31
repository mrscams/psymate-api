package com.psymate.events;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Purge nocturne des publications complétées plus vieilles que {@code
 * app.events.completed-retention} (M4-01). Spring Modulith ne fournit aucune rétention : elle est
 * écrite ici. Les publications incomplètes ne sont jamais supprimées (le dépôt JDBC ne supprime que
 * les lignes complétées).
 *
 * <p>Planificateur privé et pas de {@code @EnableScheduling}, comme {@code
 * identity.verification.PurgeJob} : l'activer casse l'injection du {@code TaskExecutor} unique.
 */
@Component
class EventPublicationPurgeJob {

  static final String CRON = "0 30 3 * * *";

  private static final Logger LOG = LoggerFactory.getLogger(EventPublicationPurgeJob.class);

  private final CompletedEventPublications completed;
  private final EventsProperties properties;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  EventPublicationPurgeJob(CompletedEventPublications completed, EventsProperties properties) {
    this.completed = completed;
    this.properties = properties;
  }

  @PostConstruct
  void schedule() {
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("event-purge-");
    scheduler.initialize();
    scheduler.schedule(this::run, new CronTrigger(CRON, TimeZone.getTimeZone(ZoneOffset.UTC)));
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }

  void run() {
    try {
      completed.deletePublicationsOlderThan(properties.completedRetention());
      LOG.info(
          "completed event publications older than {} purged", properties.completedRetention());
    } catch (RuntimeException e) {
      LOG.error("event publication purge failed", e);
    }
  }
}
