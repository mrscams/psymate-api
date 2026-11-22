package com.psymate.agenda.service;

import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.agenda.dto.CreateBookingRequest;
import com.psymate.agenda.dto.PublicAppointmentDto;
import com.psymate.agenda.dto.PublicAppointmentPractitionerDto;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.model.BookingConfirmation;
import com.psymate.agenda.model.BookingException;
import com.psymate.agenda.model.BookingRequest;
import com.psymate.agenda.repository.AppointmentRepository;
import com.psymate.agenda.repository.BookingRequestRepository;
import com.psymate.directory.api.PublishedProfileFacade;
import com.psymate.directory.api.PublishedProfileView;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.PractitionerFacade;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prise de rendez-vous en ligne, côté demandeur (M14-06, G Q72 à Q74, Q77 à Q82) : demande,
 * vérification de l'e-mail et création du rendez-vous invité, lien de gestion, annulation.
 *
 * <p>Aucune réponse avant la vérification ne dépend de l'identité saisie (G Q78) : les envois
 * ignorés (pot de miel, formulaire trop rapide, plafond d'adresse) répondent comme les autres. Rien
 * n'est retenu avant la vérification. La vérification et l'annulation se font sous le verrou
 * consultatif du praticien ({@link BookingRequestRepository#lockPractitioner}) : le premier e-mail
 * vérifié obtient le créneau (G Q77). Les jetons ne sont gardés que hachés (SHA-256) ; l'adresse ne
 * l'est que par son empreinte HMAC ({@code app.booking.hash-secret}). Les journaux ne portent ni
 * nom, ni adresse, ni téléphone, ni jeton.
 */
@Service
public class BookingRequestService {

  private static final Logger LOG = LoggerFactory.getLogger(BookingRequestService.class);

  private final BookingAvailability availability;
  private final BookingSettingsReader settingsReader;
  private final BookingRequestRepository requests;
  private final AppointmentRepository appointments;
  private final PublishedProfileFacade profiles;
  private final PractitionerFacade practitioners;
  private final DomainEventPublisher events;
  private final BookingMailer mailer;
  private final Clock clock;
  private final byte[] hashSecret;
  private final Duration verifyTtl;
  private final MessageSource messages;

  public BookingRequestService(
      BookingAvailability availability,
      BookingSettingsReader settingsReader,
      BookingRequestRepository requests,
      AppointmentRepository appointments,
      PublishedProfileFacade profiles,
      PractitionerFacade practitioners,
      DomainEventPublisher events,
      BookingMailer mailer,
      Clock clock,
      @Value("${app.booking.hash-secret}") String hashSecret,
      @Value("${app.booking.verify-ttl}") Duration verifyTtl) {
    if (hashSecret == null || hashSecret.isBlank()) {
      throw new IllegalStateException("app.booking.hash-secret est obligatoire");
    }
    if (verifyTtl == null || verifyTtl.isNegative() || verifyTtl.isZero()) {
      throw new IllegalStateException("app.booking.verify-ttl doit être une durée positive");
    }
    this.availability = availability;
    this.settingsReader = settingsReader;
    this.requests = requests;
    this.appointments = appointments;
    this.profiles = profiles;
    this.practitioners = practitioners;
    this.events = events;
    this.mailer = mailer;
    this.clock = clock;
    this.hashSecret = hashSecret.getBytes(StandardCharsets.UTF_8);
    this.verifyTtl = verifyTtl;
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    this.messages = source;
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  // ---- créneau offert (même calcul que GET …/slots, G Q76)

  /**
   * Créneau offert qui commence à {@code startsAt}, calculé par {@link SlotCalculator} sur le jour
   * local de {@code startsAt} dans le fuseau du praticien, avec les rendez-vous actifs qui touchent
   * ce créneau.
   */
  private Optional<SlotCalculator.Slot> offeredSlot(
      UUID practitionerId, BookingSettings settings, Instant startsAt, Instant now) {
    ZoneId zone = settings.timezone();
    LocalDate day = startsAt.atZone(zone).toLocalDate();
    Instant endsAt = startsAt.plus(Duration.ofMinutes(settings.slotDurationMin()));
    List<SlotCalculator.Busy> busy =
        appointments
            .findWindow(practitionerId, utc(startsAt), utc(endsAt), AgendaQueryService.ACTIVE)
            .stream()
            .map(
                a ->
                    new SlotCalculator.Busy(
                        a.getStartsAt().toInstant(), a.getEndsAt().toInstant(), a.getStatus()))
            .toList();
    return SlotCalculator.offered(settings, day, day, now, busy).stream()
        .filter(slot -> slot.startsAt().equals(startsAt))
        .findFirst();
  }

  // ---- demande (POST …/practitioners/{slug}/requests)

  /**
   * Enregistre une demande et envoie le lien de vérification après validation de la transaction.
   * 404 BOOKING_NOT_AVAILABLE si la prise de rendez-vous n'est pas offerte ; 409 SLOT_NOT_AVAILABLE
   * si {@code startsAt} n'est pas le début d'un créneau offert ou si la modalité n'est pas offerte
   * ; sinon retour sans erreur (202), que la demande soit enregistrée ou ignorée en silence.
   */
  @Transactional
  public void create(String slug, CreateBookingRequest r) {
    BookingAvailability.Offer offer =
        availability.offer(slug).orElseThrow(BookingException::notAvailable);
    BookingSettings settings = offer.settings();
    UUID practitionerId = offer.profile().practitionerId();
    Instant now = clock.instant();
    AppointmentModality modality = AppointmentModality.valueOf(r.modality());
    if (!settings.modalities().contains(modality)) {
      throw BookingException.slotNotAvailable();
    }
    SlotCalculator.Slot slot =
        offeredSlot(practitionerId, settings, r.startsAt(), now)
            .orElseThrow(BookingException::slotNotAvailable);

    // Sous le 202, sans ligne ni e-mail (G Q78) : rien ne distingue ces cas d'un envoi accepté.
    if (BookingRules.looksLikeSpam(r.website(), r.formShownAt(), now)) {
      LOG.debug("booking request ignored (anti-spam)");
      return;
    }
    OffsetDateTime createdAt = now();
    String emailHash = BookingRules.emailHash(r.email(), hashSecret);
    // Plafond par adresse (Q79) : comptage et insertion sous le verrou de l'empreinte, sinon des
    // envois simultanés compteraient tous moins de 3 avant que le premier soit validé.
    requests.lockEmail(emailHash);
    long recent = requests.countByEmailHashAndCreatedAtAfter(emailHash, createdAt.minusHours(1));
    if (BookingRules.emailCapReached(recent)) {
      LOG.debug("booking request ignored (address cap)");
      return;
    }

    String token = BookingRules.newToken();
    String locale = r.locale() == null ? "fr" : r.locale();
    String email = r.email().strip();
    BookingRequest request =
        new BookingRequest(
            UUID.randomUUID(),
            offer.profile().tenantId(),
            practitionerId,
            utc(slot.startsAt()),
            utc(slot.endsAt()),
            modality,
            r.firstName().strip(),
            r.lastName().strip(),
            email,
            blankToNull(r.phone()),
            locale,
            emailHash,
            BookingRules.sha256(token),
            createdAt.plus(verifyTtl),
            createdAt);
    requests.save(request);
    LOG.info("booking request {} created", request.getId());

    BookingMailer.Details details =
        new BookingMailer.Details(
            offer.profile().displayName(),
            slot.startsAt(),
            settings.slotDurationMin(),
            modality,
            settings.timezone());
    long minutes = verifyTtl.toMinutes();
    mailer.afterCommit(
        "verification",
        () ->
            mailer.sendVerification(
                email, BookingMailer.localeOf(locale), details, token, minutes));
  }

  // ---- vérification (POST /requests/verify)

  /**
   * Vérifie l'adresse et crée le rendez-vous invité (G Q73, Q74, Q77). Idempotent : une demande
   * déjà vérifiée rend l'état courant de son rendez-vous, sans jeton de gestion. 404
   * BOOKING_TOKEN_INVALID, 410 BOOKING_TOKEN_EXPIRED, 404 BOOKING_NOT_AVAILABLE, 409
   * BOOKING_ALREADY_ACTIVE, 409 SLOT_TAKEN, dans cet ordre.
   */
  @Transactional
  public PublicAppointmentDto verify(String token) {
    String hash = BookingRules.sha256(token);
    UUID practitionerId =
        requests
            .findPractitionerIdByVerifyTokenHash(hash)
            .orElseThrow(BookingException::tokenInvalid);
    requests.lockPractitioner(practitionerId);
    // Relue après le verrou : une vérification concurrente du même jeton est déjà validée.
    BookingRequest request =
        requests.findByVerifyTokenHash(hash).orElseThrow(BookingException::tokenInvalid);
    Instant now = clock.instant();
    if (request.isVerified()) {
      return toDto(request, appointmentOf(request), now, null);
    }
    if (!request.getVerifyExpiresAt().toInstant().isAfter(now)) {
      throw BookingException.tokenExpired();
    }
    BookingAvailability.Offer offer =
        availability.offer(practitionerId).orElseThrow(BookingException::notAvailable);
    BookingSettings settings = offer.settings();
    if (hasActiveOnlineAppointment(practitionerId, request.getEmail(), now)) {
      throw BookingException.alreadyActive();
    }
    Instant startsAt = request.getStartsAt().toInstant();
    boolean stillOffered =
        offeredSlot(practitionerId, settings, startsAt, now)
            .filter(slot -> slot.endsAt().equals(request.getEndsAt().toInstant()))
            .isPresent();
    if (!stillOffered) {
      throw BookingException.slotTaken();
    }

    boolean confirmed = settings.confirmation() == BookingConfirmation.auto;
    OffsetDateTime at = now();
    Appointment appointment =
        Appointment.online(
            UUID.randomUUID(),
            request.getTenantId(),
            practitionerId,
            new Appointment.Guest(
                request.getFirstName(),
                request.getLastName(),
                request.getPhone(),
                request.getEmail()),
            request.getStartsAt(),
            request.getEndsAt(),
            request.getModality(),
            !confirmed,
            at);
    appointments.save(appointment);
    events.publish(AppointmentService.event(AppointmentPlanned::new, appointment));

    String applicantEmail = request.getEmail();
    String applicantName = fullName(request.getFirstName(), request.getLastName());
    String manageToken = BookingRules.newToken();
    request.verify(at, appointment.getId(), BookingRules.sha256(manageToken));
    LOG.info(
        "booking request {} verified, appointment {} ({})",
        request.getId(),
        appointment.getId(),
        appointment.getStatus());

    BookingMailer.Details details =
        new BookingMailer.Details(
            offer.profile().displayName(),
            startsAt,
            appointment.getDurationMin(),
            appointment.getModality(),
            settings.timezone());
    Locale applicantLocale = BookingMailer.localeOf(request.getLocale());
    mailer.afterCommit(
        "verified",
        () ->
            mailer.sendVerified(applicantEmail, applicantLocale, details, manageToken, confirmed));
    practitioners
        .emailOf(practitionerId)
        .ifPresent(
            to -> {
              Locale locale = practitionerLocale(practitionerId);
              mailer.afterCommit(
                  "practitioner new",
                  () -> mailer.sendPractitionerNew(to, locale, details, applicantName, confirmed));
            });
    return toDto(request, appointment, now, manageToken);
  }

  /**
   * Un rendez-vous en ligne actif et à venir du praticien a déjà cette adresse (G Q79), casse et
   * blancs ignorés.
   */
  private boolean hasActiveOnlineAppointment(UUID practitionerId, String email, Instant now) {
    for (AppointmentStatus status : AgendaQueryService.ACTIVE) {
      for (Appointment a :
          appointments.findPending(
              practitionerId, status, AppointmentOrigin.ONLINE, utc(now), Limit.unlimited())) {
        if (BookingRules.sameEmail(a.getGuestEmail(), email)) {
          return true;
        }
      }
    }
    return false;
  }

  // ---- gestion (POST /manage, /manage/cancel)

  /** État du rendez-vous d'un lien de gestion ; 404 BOOKING_TOKEN_INVALID. */
  @Transactional(readOnly = true)
  public PublicAppointmentDto manage(String token) {
    BookingRequest request =
        requests
            .findByManageTokenHash(BookingRules.sha256(token))
            .orElseThrow(BookingException::tokenInvalid);
    return toDto(request, appointmentOf(request), clock.instant(), null);
  }

  /**
   * Annulation par le demandeur (G Q80) : annulation ordinaire, {@code closed_by = visitor}, {@code
   * AppointmentCancelled}, e-mail au praticien. Idempotente sur un rendez-vous déjà annulé (aucun
   * second e-mail) ; 409 APPOINTMENT_NOT_ACTIVE si le rendez-vous a commencé ou est terminé.
   */
  @Transactional
  public PublicAppointmentDto cancel(String token) {
    String hash = BookingRules.sha256(token);
    UUID practitionerId =
        requests
            .findPractitionerIdByManageTokenHash(hash)
            .orElseThrow(BookingException::tokenInvalid);
    requests.lockPractitioner(practitionerId);
    BookingRequest request =
        requests.findByManageTokenHash(hash).orElseThrow(BookingException::tokenInvalid);
    Appointment appointment = appointmentOf(request);
    Instant now = clock.instant();
    if (appointment.getStatus() == AppointmentStatus.cancelled) {
      return toDto(request, appointment, now, null);
    }
    if (!BookingRules.canCancel(appointment, now)) {
      throw BookingException.appointmentNotActive();
    }
    Locale locale = practitionerLocale(practitionerId);
    appointment.cancel(text("booking.cancelReason.visitor", locale), now());
    request.close(BookingRequest.CLOSED_BY_VISITOR);
    events.publish(AppointmentService.event(AppointmentCancelled::new, appointment));
    LOG.info("booking appointment {} cancelled by the visitor", appointment.getId());

    String applicantName =
        fullName(appointment.getGuestFirstName(), appointment.getGuestLastName());
    BookingMailer.Details details =
        new BookingMailer.Details(
            null,
            appointment.getStartsAt().toInstant(),
            appointment.getDurationMin(),
            appointment.getModality(),
            settingsReader.read(practitionerId).timezone());
    practitioners
        .emailOf(practitionerId)
        .ifPresent(
            to ->
                mailer.afterCommit(
                    "visitor cancelled",
                    () -> mailer.sendPractitionerCancelled(to, locale, details, applicantName)));
    return toDto(request, appointment, now, null);
  }

  // ---- lecture commune

  private Appointment appointmentOf(BookingRequest request) {
    return appointments
        .findById(request.getAppointmentId())
        .orElseThrow(BookingException::tokenInvalid);
  }

  /**
   * Vue publique (K5.1). Praticien lu par {@link PublishedProfileFacade#byPractitioner} : slug et
   * nom affiché à null si la fiche n'est plus publiée ou le praticien plus approuvé.
   */
  private PublicAppointmentDto toDto(
      BookingRequest request, Appointment a, Instant now, String manageToken) {
    BookingRules.PublicStatus status = BookingRules.publicStatus(a, now);
    PublishedProfileView profile = profiles.byPractitioner(a.getPractitionerId()).orElse(null);
    return new PublicAppointmentDto(
        status.name(),
        status == BookingRules.PublicStatus.cancelled ? request.getClosedBy() : null,
        a.getStartsAt().toInstant(),
        a.getEndsAt().toInstant(),
        a.getDurationMin(),
        a.getModality().name(),
        new PublicAppointmentPractitionerDto(
            profile == null ? null : profile.slug(),
            profile == null ? null : profile.displayName()),
        BookingRules.canCancel(a, now),
        manageToken);
  }

  private Locale practitionerLocale(UUID practitionerId) {
    Object locale = practitioners.preferencesOf(practitionerId).get("locale");
    return BookingMailer.localeOf(locale instanceof String s ? s : null);
  }

  private String text(String key, Locale locale) {
    return messages.getMessage(key, null, locale);
  }

  private static String fullName(String firstName, String lastName) {
    String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName));
    return name.strip();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
