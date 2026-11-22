package com.psymate.agenda.service;

import com.psymate.agenda.repository.BookingRequestRepository;
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
 * Purge quotidienne des demandes de rendez-vous en ligne (M14-07, G Q82, E9, ligne {@code
 * booking_request}) : suppression physique de la ligne {@code booking_request}
 *
 * <ul>
 *   <li>jamais vérifiée, créée depuis plus de {@code app.booking.unverified-retention} (24 h, Q82
 *       a) ;
 *   <li>vérifiée, dont le rendez-vous s'est terminé ou a été annulé depuis plus de {@code
 *       app.booking.closed-retention} (30 jours, Q82 f).
 * </ul>
 *
 * Le rendez-vous reste toujours : aucune ligne {@code appointment} n'est supprimée (ses colonnes
 * invité sont purgées par {@link GuestPurgeJob}). Aucun événement. Le journal ne porte que des
 * nombres, jamais une identité.
 *
 * <p>Même schéma que les autres purges : déclencheur cron de Spring ({@code
 * app.booking.purge.cron}, en UTC ; « - » le désactive) sur un planificateur privé, sans {@code
 * EnableScheduling}.
 */
@Component
public class BookingPurgeJob {

  private static final Logger LOG = LoggerFactory.getLogger(BookingPurgeJob.class);

  /** Lignes supprimées par un passage. */
  public record Purged(int unverified, int closed) {}

  private final BookingRequestRepository requests;
  private final Clock clock;
  private final String cron;
  private final Duration unverifiedRetention;
  private final Duration closedRetention;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  BookingPurgeJob(
      BookingRequestRepository requests,
      Clock clock,
      @Value("${app.booking.purge.cron:0 45 3 * * *}") String cron,
      @Value("${app.booking.unverified-retention:PT24H}") Duration unverifiedRetention,
      @Value("${app.booking.closed-retention:P30D}") Duration closedRetention) {
    this.requests = requests;
    this.clock = clock;
    this.cron = cron;
    this.unverifiedRetention = unverifiedRetention;
    this.closedRetention = closedRetention;
  }

  @PostConstruct
  void schedule() {
    if ("-".equals(cron)) {
      return;
    }
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("booking-purge-");
    scheduler.initialize();
    scheduler.schedule(
        this::runSafely, new CronTrigger(cron, TimeZone.getTimeZone(ZoneOffset.UTC)));
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }

  /** Conservation d'une demande jamais vérifiée (défaut 24 h). */
  public Duration unverifiedRetention() {
    return unverifiedRetention;
  }

  /** Conservation d'une demande après la fin ou l'annulation du rendez-vous (défaut 30 jours). */
  public Duration closedRetention() {
    return closedRetention;
  }

  /** Supprime les demandes échues maintenant. Appelée par le planificateur et par les tests. */
  public Purged run() {
    OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    int unverified = requests.deleteUnverifiedCreatedBefore(now.minus(unverifiedRetention));
    int closed = requests.deleteClosedBefore(now.minus(closedRetention));
    LOG.info("{} unverified and {} closed booking request(s) purged", unverified, closed);
    return new Purged(unverified, closed);
  }

  private void runSafely() {
    try {
      run();
    } catch (RuntimeException e) {
      LOG.error("booking request purge failed: {}", e.getClass().getName());
    }
  }
}
