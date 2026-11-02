package com.psymate.clinical.export.service;

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
 * Purge quotidienne des exports du compte expirés (M6-05) : le fichier chiffré est supprimé, la
 * ligne export_job reste avec purged_at. Même schéma que la purge des justificatifs
 * (identity.verification.PurgeJob) : planificateur privé et déclencheur cron, sans
 * enable-scheduling, qui créerait un second TaskExecutor dans le contexte.
 */
@Component
class ExportPurgeJob {

  static final String CRON = "0 45 3 * * *";

  private static final Logger LOG = LoggerFactory.getLogger(ExportPurgeJob.class);

  private final ExportJobService service;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  ExportPurgeJob(ExportJobService service) {
    this.service = service;
  }

  @PostConstruct
  void schedule() {
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("export-purge-");
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
        LOG.info("{} account export file(s) purged", purged);
      }
    } catch (RuntimeException e) {
      LOG.error("account export purge failed", e);
    }
  }
}
