package com.psymate.agenda.service;

import com.psymate.directory.api.PublishedProfileFacade;
import com.psymate.directory.api.PublishedProfileView;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * La prise de rendez-vous en ligne est-elle offerte à cette adresse (G Q75) ? Oui seulement si la
 * fiche est publiée et le praticien approuvé ({@link PublishedProfileFacade}, G Q71), si {@code
 * booking.enabled} vaut {@code true} et s'il existe au moins une plage. Sinon {@link
 * Optional#empty()}, sans dire pourquoi (G Q78). Lecture seule.
 */
@Component
public class BookingAvailability {

  private final PublishedProfileFacade profiles;
  private final BookingSettingsReader settings;

  public BookingAvailability(PublishedProfileFacade profiles, BookingSettingsReader settings) {
    this.profiles = profiles;
    this.settings = settings;
  }

  /** Praticien publié, approuvé, et ses réglages, quand la prise de rendez-vous est offerte. */
  public Optional<Offer> offer(String slug) {
    return profiles
        .bySlug(slug)
        .map(profile -> new Offer(profile, settings.read(profile.practitionerId())))
        .filter(Offer::offered);
  }

  /** Une fiche publiée d'un praticien approuvé et ses réglages de prise de rendez-vous. */
  public record Offer(PublishedProfileView profile, BookingSettings settings) {

    /** {@code booking.enabled} et au moins une plage (la fiche est déjà publiée et approuvée). */
    boolean offered() {
      return settings.enabled() && !settings.windows().isEmpty();
    }
  }
}
