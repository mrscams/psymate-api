package com.psymate.agenda.service;

import com.psymate.agenda.dto.PublicBookingDto;
import com.psymate.agenda.dto.PublicSlotDto;
import com.psymate.agenda.dto.PublicSlotsDto;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.BookingException;
import com.psymate.agenda.repository.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes publiques de prise de rendez-vous en ligne en lecture (M14-05, K5.1) : réglages publics et
 * créneaux offerts. Lecture seule (aucune écriture, aucune session) ; la fiche passe par {@code
 * directory :: api}, les préférences par {@code identity :: api}, les rendez-vous par {@link
 * AppointmentRepository} (seuls les actifs, sans jamais en sortir ni identifiant ni nom).
 */
@Service
@Transactional(readOnly = true)
public class PublicBookingService {

  /** Fenêtre par défaut : from + 13 jours, deux semaines to inclus (K3.1). */
  static final int DEFAULT_WINDOW_DAYS = 14;

  private final BookingAvailability availability;
  private final AppointmentRepository appointments;
  private final Clock clock;

  public PublicBookingService(
      BookingAvailability availability, AppointmentRepository appointments, Clock clock) {
    this.availability = availability;
    this.appointments = appointments;
    this.clock = clock;
  }

  private BookingAvailability.Offer offer(String slug) {
    return availability.offer(slug).orElseThrow(BookingException::notAvailable);
  }

  /** Réglages publics ; 404 BOOKING_NOT_AVAILABLE si la prise de rendez-vous n'est pas offerte. */
  public PublicBookingDto settings(String slug) {
    BookingAvailability.Offer offer = offer(slug);
    BookingSettings s = offer.settings();
    return new PublicBookingDto(
        offer.profile().slug(),
        offer.profile().displayName(),
        s.confirmation().name(),
        s.slotDurationMin(),
        s.modalities().stream().map(AppointmentModality::name).toList(),
        s.timezone().getId(),
        s.minNoticeHours(),
        s.horizonDays());
  }

  /**
   * Créneaux offerts des jours {@code from} à {@code to} inclus, dates locales du praticien ;
   * {@code from} absent = aujourd'hui, {@code to} absent = from + 13 jours. 404 d'abord (G Q78),
   * puis to avant from (422 INVALID_VALUE sur to), puis plus de 31 jours (422 WINDOW_TOO_LARGE).
   */
  public PublicSlotsDto slots(String slug, LocalDate from, LocalDate to) {
    BookingAvailability.Offer offer = offer(slug);
    BookingSettings s = offer.settings();
    ZoneId zone = s.timezone();
    Instant now = clock.instant();
    LocalDate first = from != null ? from : now.atZone(zone).toLocalDate();
    LocalDate last = to != null ? to : first.plusDays(DEFAULT_WINDOW_DAYS - 1L);
    if (last.isBefore(first)) {
      throw BookingException.toBeforeFrom();
    }
    if (ChronoUnit.DAYS.between(first, last) + 1 > BookingException.MAX_WINDOW_DAYS) {
      throw BookingException.windowTooLarge();
    }
    Instant windowStart = first.atStartOfDay(zone).toInstant();
    Instant windowEnd = last.plusDays(1).atStartOfDay(zone).toInstant();
    List<SlotCalculator.Busy> busy =
        appointments
            .findWindow(
                offer.profile().practitionerId(),
                windowStart.atOffset(ZoneOffset.UTC),
                windowEnd.atOffset(ZoneOffset.UTC),
                AgendaQueryService.ACTIVE)
            .stream()
            .map(
                a ->
                    new SlotCalculator.Busy(
                        a.getStartsAt().toInstant(), a.getEndsAt().toInstant(), a.getStatus()))
            .toList();
    List<PublicSlotDto> slots =
        SlotCalculator.offered(s, first, last, now, busy).stream()
            .map(slot -> new PublicSlotDto(slot.startsAt(), slot.endsAt()))
            .toList();
    return new PublicSlotsDto(zone.getId(), slots);
  }
}
