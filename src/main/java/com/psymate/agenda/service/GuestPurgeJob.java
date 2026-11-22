package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.repository.AppointmentRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Purge quotidienne des invités (G Q60, Q82, E9) : pour chaque rendez-vous invité non purgé
 * terminé, ou annulé, depuis plus de {@code app.agenda.guest-purge.retention} (6 mois), nom,
 * prénom, téléphone et e-mail passent à NULL et {@code guest_purged_at} est posé, en une seule
 * requête {@code UPDATE}. Un rendez-vous d'origine {@code online} annulé (refus, expiration,
 * annulation) est purgé plus tôt : {@code app.agenda.guest-purge.online-cancelled-retention} (30
 * jours) après son annulation (Q82 c) ; un rendez-vous du praticien annulé reste sur la règle des 6
 * mois. Le rendez-vous reste : aucune ligne n'est supprimée. Aucun événement. Le journal ne porte
 * que le nombre de lignes, jamais une identité.
 *
 * <p>Même schéma que les autres purges (identity, directory, events) : déclencheur cron de Spring
 * sur un planificateur privé, sans {@code @EnableScheduling} (l'activer crée un second {@code
 * TaskExecutor} ou supprime l'unique {@code applicationTaskExecutor}).
 */
@Component
public class GuestPurgeJob {

  private static final Logger LOG = LoggerFactory.getLogger(GuestPurgeJob.class);

  private final AppointmentRepository appointments;
  private final Clock clock;
  private final String cron;
  private final Duration retention;
  private final Duration onlineCancelledRetention;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  GuestPurgeJob(
      AppointmentRepository appointments,
      Clock clock,
      @Value("${app.agenda.guest-purge.cron:0 30 3 * * *}") String cron,
      @Value("${app.agenda.guest-purge.retention:P180D}") Duration retention,
      @Value("${app.agenda.guest-purge.online-cancelled-retention:P30D}")
          Duration onlineCancelledRetention) {
    this.appointments = appointments;
    this.clock = clock;
    this.cron = cron;
    this.retention = retention;
    this.onlineCancelledRetention = onlineCancelledRetention;
  }

  @PostConstruct
  void schedule() {
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("guest-purge-");
    scheduler.initialize();
    scheduler.schedule(
        this::runSafely, new CronTrigger(cron, TimeZone.getTimeZone(ZoneOffset.UTC)));
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }

  /** Rétention appliquée (défaut 180 jours). */
  public Duration retention() {
    return retention;
  }

  /** Rétention d'un rendez-vous en ligne annulé, depuis l'annulation (défaut 30 jours, Q82 c). */
  public Duration onlineCancelledRetention() {
    return onlineCancelledRetention;
  }

  /**
   * Purge les invités échus maintenant et renvoie le nombre de rendez-vous purgés. Appelée par le
   * planificateur et par les tests.
   */
  public int run() {
    OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    int purged =
        appointments.purgeGuests(
            now.minus(retention),
            now.minus(onlineCancelledRetention),
            AppointmentOrigin.ONLINE,
            now);
    LOG.info("{} guest appointment(s) purged", purged);
    return purged;
  }

  private void runSafely() {
    try {
      run();
    } catch (RuntimeException e) {
      LOG.error("guest appointment purge failed", e);
    }
  }
}
