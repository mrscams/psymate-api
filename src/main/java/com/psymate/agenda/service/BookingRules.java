package com.psymate.agenda.service;

import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentStatus;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Règles pures de la prise de rendez-vous en ligne (M14-06) : jetons et empreintes, filtre
 * anti-spam, état public d'un rendez-vous. Ni base, ni horloge (l'instant courant est un
 * paramètre), aucun état.
 */
public final class BookingRules {

  /**
   * Délai minimal entre l'affichage du formulaire et son envoi (comme le formulaire de contact).
   */
  static final Duration MIN_FILL_TIME = Duration.ofSeconds(3);

  /**
   * Avance tolérée de l'horloge du client sur celle du serveur : un {@code formShownAt} jusqu'à 5
   * minutes dans le futur est accepté (horloge du visiteur en avance) ; au-delà, la demande est
   * ignorée en silence.
   */
  static final Duration MAX_CLIENT_CLOCK_AHEAD = Duration.ofMinutes(5);

  /** E-mails de vérification par adresse et par heure (G Q79). */
  static final int MAX_REQUESTS_PER_EMAIL_PER_HOUR = 3;

  /** Octets d'un jeton : 256 bits (G Q80). */
  static final int TOKEN_BYTES = 32;

  private static final SecureRandom RANDOM = new SecureRandom();

  private BookingRules() {}

  /** État public d'un rendez-vous ({@code PublicAppointmentDto.status}, K5.1). */
  enum PublicStatus {
    pending,
    confirmed,
    cancelled,
    past
  }

  // ---- jetons et empreintes

  /** Jeton aléatoire de 256 bits, base64url sans remplissage (43 caractères), comme identity. */
  static String newToken() {
    byte[] raw = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  /** Empreinte stockée d'un jeton : SHA-256 hexadécimal, comme les jetons d'identity. */
  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Adresse normalisée : minuscules, sans aucun blanc (bords compris). */
  static String normalizeEmail(String email) {
    StringBuilder sb = new StringBuilder(email.length());
    email
        .codePoints()
        .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c))
        .forEach(sb::appendCodePoint);
    return sb.toString().toLowerCase(Locale.ROOT);
  }

  /**
   * Empreinte d'une adresse ({@code booking_request.email_hash}, G Q79) : HMAC-SHA-256 hexadécimal
   * de l'adresse normalisée, avec le secret {@code app.booking.hash-secret}.
   */
  static String emailHash(String email, byte[] secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(normalizeEmail(email).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Même adresse, casse et blancs ignorés ; une adresse absente n'égale rien. */
  static boolean sameEmail(String a, String b) {
    return a != null && b != null && normalizeEmail(a).equals(normalizeEmail(b));
  }

  // ---- anti-spam (G Q78 : ignoré en silence, sous le 202)

  /**
   * Pot de miel rempli, formulaire affiché plus de 5 minutes dans le futur ({@link
   * #MAX_CLIENT_CLOCK_AHEAD}), ou envoyé en moins de 3 s. Un instant d'affichage dans le futur,
   * dans la tolérance, vient d'une horloge du client en avance : le délai de remplissage ne se
   * mesure pas, la demande est acceptée.
   */
  static boolean looksLikeSpam(String honeypot, Instant formShownAt, Instant now) {
    if (honeypot != null && !honeypot.isBlank()) {
      return true;
    }
    if (formShownAt == null || formShownAt.isAfter(now.plus(MAX_CLIENT_CLOCK_AHEAD))) {
      return true;
    }
    return !formShownAt.isAfter(now)
        && Duration.between(formShownAt, now).compareTo(MIN_FILL_TIME) < 0;
  }

  /** Plafond d'adresse atteint : déjà 3 demandes dans l'heure pour cette empreinte (G Q79). */
  static boolean emailCapReached(long requestsInLastHour) {
    return requestsInLastHour >= MAX_REQUESTS_PER_EMAIL_PER_HOUR;
  }

  // ---- état public d'un rendez-vous (K5.1)

  /**
   * {@code pending} pour {@code tentative} ; {@code confirmed} pour {@code planned} non terminé ;
   * {@code cancelled} ; {@code past} pour {@code planned} terminé et pour {@code done}.
   */
  static PublicStatus publicStatus(AppointmentStatus status, Instant endsAt, Instant now) {
    return switch (status) {
      case tentative -> PublicStatus.pending;
      case planned -> endsAt.isAfter(now) ? PublicStatus.confirmed : PublicStatus.past;
      case cancelled -> PublicStatus.cancelled;
      case done -> PublicStatus.past;
    };
  }

  static PublicStatus publicStatus(Appointment a, Instant now) {
    return publicStatus(a.getStatus(), a.getEndsAt().toInstant(), now);
  }

  /**
   * Le demandeur peut annuler : rendez-vous actif ({@code pending} ou {@code confirmed}) qui n'a
   * pas commencé ; un rendez-vous commencé ou terminé répond 409 APPOINTMENT_NOT_ACTIVE.
   */
  static boolean canCancel(AppointmentStatus status, Instant startsAt, Instant now) {
    return status.isActive() && startsAt.isAfter(now);
  }

  static boolean canCancel(Appointment a, Instant now) {
    return canCancel(a.getStatus(), a.getStartsAt().toInstant(), now);
  }
}
