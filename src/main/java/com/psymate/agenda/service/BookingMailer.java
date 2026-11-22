package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentModality;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
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
 * e-mails). E-mails de suivi au demandeur (M14-07, {@link #sendFollowUp}) : confirmation, refus,
 * annulation, déplacement, expiration ; jamais le motif d'annulation.
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

  /**
   * Attend que la file d'envoi soit vide et qu'aucun envoi ne soit en cours, {@code timeout} au
   * plus ; vrai si c'est le cas. Sert aux tests, qui écoulent les envois avant de compter les
   * e-mails reçus.
   */
  public boolean awaitIdle(Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (pool.getActiveCount() > 0 || !pool.getQueue().isEmpty()) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      Thread.sleep(20);
    }
    return true;
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

  // ---- e-mails de suivi au demandeur (M14-07, G Q81)

  /**
   * E-mail de suivi d'un rendez-vous en ligne, clés {@code booking.mail.followUp.<clé>.*}. Ceux qui
   * laissent le rendez-vous actif rappellent le lien de gestion ({@link #carriesLink}).
   */
  public enum FollowUp {
    /** Le praticien confirme la demande ({@code tentative → planned}). */
    CONFIRMED("confirmed", true),
    /** Le praticien refuse la demande (annulation d'un {@code tentative}), sans motif. */
    REFUSED("refused", false),
    /** Le praticien annule un rendez-vous confirmé, sans motif. */
    CANCELLED("cancelled", false),
    /** Le praticien déplace le rendez-vous ou change sa durée (et peut-être sa modalité). */
    RESCHEDULED("rescheduled", true),
    /** Le praticien change seulement la modalité. */
    MODIFIED("modified", true),
    /** Demande sans réponse annulée d'office ({@code BookingExpiryJob}, G Q82). */
    EXPIRED("expired", false);

    private final String key;
    private final boolean carriesLink;

    FollowUp(String key, boolean carriesLink) {
      this.key = key;
      this.carriesLink = carriesLink;
    }

    /**
     * Le rendez-vous reste actif : l'e-mail porte un nouveau lien de gestion, sauf s'il a déjà
     * commencé (déplacé ou modifié dans le passé, le demandeur ne peut plus l'annuler).
     */
    public boolean carriesLink() {
      return carriesLink;
    }
  }

  /**
   * Ce qui change dans un e-mail « déplacé » ou « modifié » : valeurs d'avant le PATCH. Une valeur
   * égale à la nouvelle n'est pas citée.
   *
   * @param startsAt ancien début
   * @param durationMin ancienne durée, en minutes
   * @param modality ancienne modalité
   */
  public record Change(Instant startsAt, int durationMin, AppointmentModality modality) {

    public Change {
      Objects.requireNonNull(startsAt, "startsAt");
      Objects.requireNonNull(modality, "modality");
    }
  }

  /**
   * E-mail de suivi au demandeur. Objet neutre ; corps avec le nom du praticien, la date et l'heure
   * dans son fuseau, la durée, la modalité ; jamais le motif d'annulation ni la note de
   * planification.
   *
   * @param previous valeurs d'avant, pour {@link FollowUp#RESCHEDULED} et {@link FollowUp#MODIFIED}
   *     seulement
   * @param manageToken nouveau jeton de gestion ; obligatoire pour {@link FollowUp#CONFIRMED} ;
   *     null pour un déplacement ou une modification d'un rendez-vous déjà commencé, dont l'e-mail
   *     dit alors qu'il ne peut plus être annulé en ligne
   */
  public void sendFollowUp(
      String to,
      Locale locale,
      FollowUp kind,
      Details details,
      Change previous,
      String manageToken) {
    Locale l = normalize(locale);
    String prefix = "booking.mail.followUp." + kind.key;
    String name = practitioner(details.practitionerName(), l);
    String link = manageToken == null ? null : webBaseUrl + "/rendez-vous/gerer/" + manageToken;
    if (kind == FollowUp.CONFIRMED && link == null) {
      throw new IllegalArgumentException("manageToken required for " + kind);
    }
    Object[] args =
        switch (kind) {
          case CONFIRMED -> new Object[] {name, details(details, l), link, appName};
          case RESCHEDULED, MODIFIED ->
              new Object[] {
                name,
                changes(Objects.requireNonNull(previous, "previous"), details, l),
                details(details, l),
                link == null
                    ? text("booking.mail.followUp.started", l)
                    : text("booking.mail.followUp.link", l, link),
                appName
              };
          case REFUSED, CANCELLED, EXPIRED -> new Object[] {name, details(details, l), appName};
        };
    send(to, text(prefix + ".subject", l), text(prefix + ".body", l, args));
  }

  /** Lignes « ancienne date », « durée … au lieu de … », « modalité … au lieu de … ». */
  String changes(Change previous, Details now, Locale locale) {
    StringBuilder sb = new StringBuilder();
    if (!previous.startsAt().equals(now.startsAt())) {
      line(
          sb,
          text("booking.mail.change.date", locale, when(previous.startsAt(), now.zone(), locale)));
    }
    if (previous.durationMin() != now.durationMin()) {
      line(
          sb,
          text(
              "booking.mail.change.duration",
              locale,
              String.valueOf(previous.durationMin()),
              String.valueOf(now.durationMin())));
    }
    if (previous.modality() != now.modality()) {
      line(
          sb,
          text(
              "booking.mail.change.modality",
              locale,
              text("booking.mail.modality." + previous.modality().name(), locale),
              text("booking.mail.modality." + now.modality().name(), locale)));
    }
    return sb.toString();
  }

  private static void line(StringBuilder sb, String line) {
    if (!sb.isEmpty()) {
      sb.append('\n');
    }
    sb.append(line);
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

  /** Date, heure et fuseau d'un instant (ancienne date d'un rendez-vous déplacé). */
  String when(Instant startsAt, ZoneId zone, Locale locale) {
    ZonedDateTime at = startsAt.atZone(zone);
    String date =
        DateTimeFormatter.ofPattern(text("booking.mail.datePattern", locale), locale).format(at);
    return text("booking.mail.when", locale, date, TIME.format(at), zone.getId());
  }

  /** Nom du praticien ; libellé neutre si sa fiche n'est plus publiée. */
  private String practitioner(String name, Locale locale) {
    String n = singleLine(name);
    return n.isEmpty() ? text("booking.mail.unknownPractitioner", locale) : n;
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
