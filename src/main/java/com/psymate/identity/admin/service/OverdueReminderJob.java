package com.psymate.identity.admin.service;

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
 * Rappel quotidien (07:00 UTC) aux administrateurs des dossiers en attente depuis plus de 2 jours
 * ouvrés.
 *
 * <p>Même mécanisme que PurgeJob : cron Spring sur un planificateur privé, sans l'annotation
 * d'activation de la planification globale (elle casserait l'injection du TaskExecutor dans
 * AuthService).
 */
@Component
public class OverdueReminderJob {

  static final String CRON = "0 0 7 * * *";

  private static final Logger LOG = LoggerFactory.getLogger(OverdueReminderJob.class);

  private final AdminVerificationService service;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  OverdueReminderJob(AdminVerificationService service) {
    this.service = service;
  }

  @PostConstruct
  void schedule() {
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("admin-overdue-");
    scheduler.initialize();
    scheduler.schedule(this::run, new CronTrigger(CRON, TimeZone.getTimeZone(ZoneOffset.UTC)));
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }

  void run() {
    try {
      int overdue = service.sendOverdueReminders();
      if (overdue > 0) {
        LOG.info("{} overdue verification dossier(s) reported to administrators", overdue);
      }
    } catch (RuntimeException e) {
      LOG.error("overdue verification reminder failed", e);
    }
  }
}
