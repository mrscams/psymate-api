package com.psymate.directory.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Purge quotidienne des demandes de contact (E3.10, fiche 09) : les lignes dont purge_after (30
 * jours après l'envoi) est dépassé sont supprimées physiquement (table technique, E2).
 *
 * <p>Même schéma que la purge des justificatifs d'identity : déclencheur cron de Spring sur un
 * planificateur privé, sans {@code @EnableScheduling} (l'activer crée un second {@code
 * TaskExecutor} ou supprime l'unique {@code applicationTaskExecutor}, ce qui casse l'injection d'un
 * {@code TaskExecutor} dans AuthService).
 */
@Component
class ContactPurgeJob {

  static final String CRON = "0 30 3 * * *";

  private static final Logger LOG = LoggerFactory.getLogger(ContactPurgeJob.class);

  private final ContactRequestService service;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  ContactPurgeJob(ContactRequestService service) {
    this.service = service;
  }

  @PostConstruct
  void schedule() {
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("contact-purge-");
    scheduler.initialize();
    scheduler.schedule(this::run, new CronTrigger(CRON, TimeZone.getTimeZone(ZoneOffset.UTC)));
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }

  void run() {
    try {
      int purged = service.purgeDue();
      LOG.info("{} contact request(s) purged", purged);
    } catch (RuntimeException e) {
      LOG.error("contact request purge failed", e);
    }
  }
}
