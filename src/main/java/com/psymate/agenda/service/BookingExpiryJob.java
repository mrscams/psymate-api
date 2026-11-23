package com.psymate.agenda.service;

import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.model.BookingRequest;
import com.psymate.agenda.repository.AppointmentRepository;
import com.psymate.agenda.repository.BookingRequestRepository;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.shared.i18n.ServedLanguage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expiration des demandes en ligne sans réponse (M14-07, G Q82, K3.3) : en mode {@code manual}, un
 * rendez-vous en ligne encore {@code tentative} est annulé d'office {@code app.booking.pending-ttl}
 * (72 h) après la vérification de l'e-mail, ou au début de son créneau si c'est plus tôt.
 * Annulation ordinaire : {@code cancelled}, {@code cancel_reason} = libellé « Demande expirée »
 * dans la langue du praticien, {@code booking_request.closed_by = expired}, événement {@code
 * AppointmentCancelled}, e-mail « Votre demande a expiré » au demandeur. Un rendez-vous confirmé
 * ({@code planned}) n'expire jamais.
 *
 * <p>Chaque rendez-vous est traité dans sa propre transaction, sous le verrou consultatif du
 * praticien (celui de la vérification, de l'annulation par le demandeur et des transitions du
 * praticien), puis relu : un rendez-vous confirmé ou annulé entre-temps est laissé tel quel. Un
 * second passage n'a donc aucun effet. Un échec sur un rendez-vous n'arrête pas les autres ; un
 * échec d'envoi ne fait jamais échouer la tâche. Le journal ne porte que des nombres.
 *
 * <p>Même schéma que les autres tâches planifiées : déclencheur cron de Spring ({@code
 * app.booking.expiry.cron}, en UTC ; « - » le désactive) sur un planificateur privé, sans {@code
 * EnableScheduling}.
 */
@Component
public class BookingExpiryJob {

  private static final Logger LOG = LoggerFactory.getLogger(BookingExpiryJob.class);

  private final AppointmentRepository appointments;
  private final BookingRequestRepository requests;
  private final BookingNotifier notifier;
  private final DomainEventPublisher events;
  private final PractitionerFacade practitioners;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final String cron;
  private final Duration pendingTtl;
  private final MessageSource messages;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  BookingExpiryJob(
      AppointmentRepository appointments,
      BookingRequestRepository requests,
      BookingNotifier notifier,
      DomainEventPublisher events,
      PractitionerFacade practitioners,
      PlatformTransactionManager transactionManager,
      Clock clock,
      @Value("${app.booking.expiry.cron:0 */15 * * * *}") String cron,
      @Value("${app.booking.pending-ttl:PT72H}") Duration pendingTtl) {
    if (pendingTtl == null || pendingTtl.isNegative() || pendingTtl.isZero()) {
      throw new IllegalStateException("app.booking.pending-ttl doit être une durée positive");
    }
    this.appointments = appointments;
    this.requests = requests;
    this.notifier = notifier;
    this.events = events;
    this.practitioners = practitioners;
    this.tx = new TransactionTemplate(transactionManager);
    this.clock = clock;
    this.cron = cron;
    this.pendingTtl = pendingTtl;
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    this.messages = source;
  }

  @PostConstruct
  void schedule() {
    if ("-".equals(cron)) {
      return;
    }
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.setThreadNamePrefix("booking-expiry-");
    scheduler.initialize();
    scheduler.schedule(
        this::runSafely, new CronTrigger(cron, TimeZone.getTimeZone(ZoneOffset.UTC)));
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }

  /** Délai de réponse du praticien après la vérification (défaut 72 h). */
  public Duration pendingTtl() {
    return pendingTtl;
  }

  /**
   * Annule les demandes échues maintenant et renvoie leur nombre. Appelée par le planificateur et
   * par les tests ; idempotente.
   */
  public int run() {
    OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    OffsetDateTime verifiedBefore = now.minus(pendingTtl);
    List<UUID> due =
        tx.execute(
            s ->
                requests.findExpiringAppointmentIds(AppointmentOrigin.ONLINE, verifiedBefore, now));
    int expired = 0;
    int failed = 0;
    String lastFailure = null;
    for (UUID id : due == null ? List.<UUID>of() : due) {
      try {
        if (Boolean.TRUE.equals(tx.execute(s -> expire(id, verifiedBefore, now)))) {
          expired++;
        }
      } catch (RuntimeException e) {
        failed++;
        lastFailure = e.getClass().getName();
      }
    }
    LOG.info("{} online booking request(s) expired", expired);
    if (failed > 0) {
      LOG.warn("{} online booking request(s) could not be expired ({})", failed, lastFailure);
    }
    return expired;
  }

  /** Dans une transaction : verrou du praticien, relecture, annulation si toujours due. */
  private boolean expire(UUID appointmentId, OffsetDateTime verifiedBefore, OffsetDateTime now) {
    Optional<UUID> practitionerId = appointments.findPractitionerIdById(appointmentId);
    if (practitionerId.isEmpty()) {
      return false;
    }
    requests.lockPractitioner(practitionerId.get());
    Appointment appointment = appointments.findById(appointmentId).orElse(null);
    BookingRequest request = requests.findByAppointmentId(appointmentId).orElse(null);
    if (appointment == null
        || request == null
        || !isDue(appointment, request, verifiedBefore, now)) {
      return false;
    }
    Locale locale = practitionerLocale(appointment.getPractitionerId());
    appointment.cancel(
        messages.getMessage(
            "booking.cancelReason.expired", null, ServedLanguage.messageLocaleOf(locale)),
        now);
    request.close(BookingRequest.CLOSED_BY_EXPIRED);
    events.publish(AppointmentService.event(AppointmentCancelled::new, appointment));
    notifier.expired(appointment);
    return true;
  }

  /**
   * Toujours due après relecture : en ligne, encore en attente, vérifiée avant {@code
   * verifiedBefore} ou créneau commencé.
   */
  static boolean isDue(
      Appointment a, BookingRequest r, OffsetDateTime verifiedBefore, OffsetDateTime now) {
    return a.getOrigin() == AppointmentOrigin.ONLINE
        && a.getStatus() == AppointmentStatus.tentative
        && r.getVerifiedAt() != null
        && (r.getVerifiedAt().isBefore(verifiedBefore) || !a.getStartsAt().isAfter(now));
  }

  private Locale practitionerLocale(UUID practitionerId) {
    Object locale = practitioners.preferencesOf(practitionerId).get("locale");
    return BookingMailer.localeOf(locale instanceof String s ? s : null);
  }

  private void runSafely() {
    try {
      run();
    } catch (RuntimeException e) {
      LOG.error("online booking expiry failed: {}", e.getClass().getName());
    }
  }
}
