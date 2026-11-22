package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentModality;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * E-mails de la prise de rendez-vous en ligne (G Q81), en français ou en anglais (clés {@code
 * booking.mail.*}). Objets neutres : ni nom du praticien, ni profession, ni date. Les corps portent
 * le nom du praticien, la date et l'heure dans le fuseau du praticien avec le nom du fuseau, la
 * durée, la modalité, le lien, et rappellent que le produit n'est pas un service d'urgence ; jamais
 * la note de planification. Expéditeur : celui de l'API ({@code app.mail.from}, comme les autres
 * e-mails).
 *
 * <p>Envoi après validation de la transaction ({@link #afterCommit}), hors du fil de la requête,
 * sur un pool privé de fils démons : un échec d'envoi ne remonte jamais au demandeur. Les journaux
 * ne portent jamais de nom, d'adresse, de téléphone ni de jeton.
 */
@Component
public class BookingMailer {

  private static final Logger LOG = LoggerFactory.getLogger(BookingMailer.class);

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  /**
   * Détails d'un rendez-vous dans un e-mail.
   *
   * @param practitionerName nom affiché du praticien
   * @param startsAt début, instant
   * @param durationMin durée en minutes
   * @param modality modalité
   * @param zone fuseau du praticien ({@code booking.timezone})
   */
  public record Details(
      String practitionerName,
      Instant startsAt,
      int durationMin,
      AppointmentModality modality,
      ZoneId zone) {

    public Details {
      Objects.requireNonNull(startsAt, "startsAt");
      Objects.requireNonNull(modality, "modality");
      Objects.requireNonNull(zone, "zone");
    }
  }

  private final JavaMailSender sender;
  private final MessageSource messages;
  private final String from;
  private final String appName;
  private final String webBaseUrl;

  // Pool privé (sur le modèle du relais du formulaire de contact) : file bornée, fils démons.
  private final ThreadPoolExecutor pool =
      new ThreadPoolExecutor(
          2,
          2,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(500),
          r -> {
            Thread t = new Thread(r, "booking-mail");
            t.setDaemon(true);
            return t;
          });

  BookingMailer(
      JavaMailSender sender,
      @Value("${app.mail.from}") String from,
      @Value("${app.name}") String appName,
      @Value("${app.web.base-url}") String webBaseUrl) {
    this.sender = sender;
    this.from = from;
    this.appName = appName;
    this.webBaseUrl = webBaseUrl;
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    this.messages = source;
  }

  @PreDestroy
  void stop() {
    pool.shutdown();
  }

  /** {@code en} pour « en », le français sinon (langue du produit). */
  static Locale localeOf(String tag) {
    return "en".equals(tag) ? Locale.ENGLISH : Locale.FRENCH;
  }

  // ---- envoi différé

  /**
   * Exécute {@code send} sur le pool une fois la transaction en cours validée (aussitôt sans
   * transaction) ; rien si elle est annulée. Une erreur est journalisée avec {@code what} et la
   * classe de l'exception seulement : jamais son message, qui peut citer une adresse.
   */
  public void afterCommit(String what, Runnable send) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              submit(what, send);
            }
          });
    } else {
      submit(what, send);
    }
  }

  private void submit(String what, Runnable send) {
    try {
      pool.execute(
          () -> {
            try {
              send.run();
            } catch (RuntimeException e) {
              LOG.warn("booking e-mail ({}) could not be sent: {}", what, e.getClass().getName());
            }
          });
    } catch (RejectedExecutionException e) {
      LOG.warn("booking e-mail ({}) dropped: queue full", what);
    }
  }

  // ---- e-mails au demandeur

  /** Lien de vérification de l'adresse ({@code /rendez-vous/confirmer/{jeton}}, G Q73). */
  public void sendVerification(
      String to, Locale locale, Details details, String token, long validityMinutes) {
    Locale l = normalize(locale);
    String link = webBaseUrl + "/rendez-vous/confirmer/" + token;
    send(
        to,
        text("booking.mail.verify.subject", l),
        text(
            "booking.mail.verify.body",
            l,
            singleLine(details.practitionerName()),
            details(details, l),
            String.valueOf(validityMinutes),
            link,
            appName));
  }

  /**
   * Après la vérification : « demande transmise » ({@code manual}) ou « rendez-vous confirmé »
   * ({@code auto}), avec le lien de gestion ({@code /rendez-vous/gerer/{jeton}}, G Q80).
   */
  public void sendVerified(
      String to, Locale locale, Details details, String manageToken, boolean confirmed) {
    Locale l = normalize(locale);
    String link = webBaseUrl + "/rendez-vous/gerer/" + manageToken;
    String kind = confirmed ? "confirmed" : "pending";
    send(
        to,
        text("booking.mail." + kind + ".subject", l),
        text(
            "booking.mail." + kind + ".body",
            l,
            singleLine(details.practitionerName()),
            details(details, l),
            link,
            appName));
  }

  // ---- e-mails au praticien (adresse du compte, sa langue)

  /** Nouvelle demande ({@code manual}) ou nouveau rendez-vous ({@code auto}) pris en ligne. */
  public void sendPractitionerNew(
      String to, Locale locale, Details details, String applicant, boolean confirmed) {
    Locale l = normalize(locale);
    String kind = confirmed ? "newAppointment" : "newRequest";
    send(
        to,
        text("booking.mail." + kind + ".subject", l),
        text(
            "booking.mail." + kind + ".body",
            l,
            applicant(applicant, l),
            details(details, l),
            webBaseUrl + "/agenda",
            appName));
  }

  /** Annulation par le demandeur (G Q80). {@code applicant} null : invité rattaché ou purgé. */
  public void sendPractitionerCancelled(
      String to, Locale locale, Details details, String applicant) {
    Locale l = normalize(locale);
    send(
        to,
        text("booking.mail.visitorCancelled.subject", l),
        text(
            "booking.mail.visitorCancelled.body",
            l,
            applicant(applicant, l),
            details(details, l),
            webBaseUrl + "/agenda",
            appName));
  }

  // ---- mise en forme

  private static Locale normalize(Locale locale) {
    return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
  }

  /** Message avec arguments : MessageFormat actif, apostrophes doublées dans les fichiers. */
  private String text(String key, Locale locale, Object... args) {
    return messages.getMessage(key, args, locale);
  }

  /** Bloc date, heure, fuseau, durée, modalité. */
  String details(Details d, Locale locale) {
    ZonedDateTime at = d.startsAt().atZone(d.zone());
    String date =
        DateTimeFormatter.ofPattern(text("booking.mail.datePattern", locale), locale).format(at);
    return text(
        "booking.mail.details",
        locale,
        date,
        TIME.format(at),
        d.zone().getId(),
        String.valueOf(d.durationMin()),
        text("booking.mail.modality." + d.modality().name(), locale));
  }

  private String applicant(String name, Locale locale) {
    String n = singleLine(name);
    return n.isEmpty() ? text("booking.mail.unknownApplicant", locale) : n;
  }

  /** Une valeur saisie reste sur une ligne : caractères de contrôle remplacés par un espace. */
  static String singleLine(String value) {
    return value == null ? "" : value.replaceAll("\\p{Cntrl}+", " ").strip();
  }

  private void send(String to, String subject, String body) {
    try {
      MimeMessage mime = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
      helper.setFrom(from, appName);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body);
      sender.send(mime);
    } catch (MessagingException | UnsupportedEncodingException e) {
      throw new MailPreparationException(e);
    }
  }
}
