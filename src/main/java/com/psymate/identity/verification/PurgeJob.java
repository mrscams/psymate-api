package com.psymate.identity.verification;

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
 * Purge quotidienne des justificatifs (R12) : le fichier est supprimé, la ligne et ses métadonnées
 * restent. purge_after est posé à la décision (+ 90 jours) par l'administration (M1-14).
 *
 * <p>La planification utilise le déclencheur cron de Spring sur un planificateur privé (pas de
 * {@code @EnableScheduling}) : l'activer crée un second {@code TaskExecutor}, ou supprime l'unique
 * {@code applicationTaskExecutor}, ce qui casse l'injection d'un {@code TaskExecutor} dans
 * AuthService (module auth, hors périmètre).
 */
@Component
class PurgeJob {

  static final String CRON = "0 15 3 * * *";

  private static final Logger LOG = LoggerFactory.getLogger(PurgeJob.class);

  private final VerificationService service;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  PurgeJob(VerificationService service) {
    this.service = service;
  }

  @PostConstruct
  void schedule() {
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("verification-purge-");
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
      if (purged > 0) {
        LOG.info("{} verification document(s) purged", purged);
      }
    } catch (RuntimeException e) {
      LOG.error("verification document purge failed", e);
    }
  }
}
