package com.psymate.agenda.service;

import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.model.BookingRequest;
import com.psymate.agenda.repository.BookingRequestRepository;
import com.psymate.directory.api.PublishedProfileFacade;
import com.psymate.directory.api.PublishedProfileView;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * E-mails de suivi au demandeur d'un rendez-vous pris en ligne (M14-07, G Q81) : confirmation,
 * refus, annulation, déplacement et changement de modalité par le praticien, expiration de la
 * demande. Un e-mail sans lien (refus, annulation, expiration, ou rendez-vous déjà commencé) laisse
 * le dernier lien valable (G Q80 précisée).
 *
 * <p>Seul un rendez-vous d'origine {@code online} dont l'invité a encore un e-mail ({@code
 * guest_email} non nul : ni rattaché à un patient, ni purgé) et dont la demande existe encore est
 * suivi ; tout autre appel ne fait rien. Langue : celle de la demande ({@code
 * booking_request.locale}). Le lien de gestion n'est gardé que par son empreinte (G Q80) : un
 * e-mail qui le rappelle (le rendez-vous reste actif) en émet un nouveau et remplace {@code
 * manage_token_hash}, si bien que l'ancien lien cesse d'être valable. L'appelant est dans la
 * transaction qui change le rendez-vous ; l'envoi part après sa validation, hors du fil courant
 * ({@link BookingMailer#afterCommit}). Jamais le motif d'annulation ni la note de planification ;
 * aucun journal d'identité, d'adresse ni de jeton.
 */
@Component
public class BookingNotifier {

  private final BookingRequestRepository requests;
  private final BookingSettingsReader settings;
  private final PublishedProfileFacade profiles;
  private final BookingMailer mailer;
  private final Clock clock;

  BookingNotifier(
      BookingRequestRepository requests,
      BookingSettingsReader settings,
      PublishedProfileFacade profiles,
      BookingMailer mailer,
      Clock clock) {
    this.requests = requests;
    this.settings = settings;
    this.profiles = profiles;
    this.mailer = mailer;
    this.clock = clock;
  }

  /** Le praticien a confirmé la demande ({@code tentative → planned}). */
  public void confirmed(Appointment a) {
    notify(a, BookingMailer.FollowUp.CONFIRMED, null);
  }

  /**
   * Le praticien a annulé : refus si le rendez-vous était encore en attente ({@code tentative}),
   * annulation sinon. Sans motif.
   */
  public void cancelledByPractitioner(Appointment a, boolean wasPending) {
    notify(a, wasPending ? BookingMailer.FollowUp.REFUSED : BookingMailer.FollowUp.CANCELLED, null);
  }

  /**
   * PATCH du praticien ; {@code previous} : début, durée et modalité d'avant. Un seul e-mail : «
   * déplacé » si le début ou la durée change (il cite aussi une nouvelle modalité), « modifié » si
   * seule la modalité change, rien sinon (note, identité de l'invité).
   */
  public void changed(Appointment a, BookingMailer.Change previous) {
    boolean moved =
        !previous.startsAt().equals(a.getStartsAt().toInstant())
            || previous.durationMin() != a.getDurationMin();
    if (moved) {
      notify(a, BookingMailer.FollowUp.RESCHEDULED, previous);
    } else if (previous.modality() != a.getModality()) {
      notify(a, BookingMailer.FollowUp.MODIFIED, previous);
    }
  }

  /** La demande sans réponse a expiré ({@code BookingExpiryJob}). */
  public void expired(Appointment a) {
    notify(a, BookingMailer.FollowUp.EXPIRED, null);
  }

  /** Vrai si un e-mail de suivi peut partir pour ce rendez-vous. */
  static boolean followed(Appointment a) {
    return a.getOrigin() == AppointmentOrigin.ONLINE && a.getGuestEmail() != null;
  }

  private void notify(Appointment a, BookingMailer.FollowUp kind, BookingMailer.Change previous) {
    if (!followed(a)) {
      return;
    }
    Optional<BookingRequest> found = requests.findByAppointmentId(a.getId());
    if (found.isEmpty()) {
      return;
    }
    BookingRequest request = found.get();
    String manageToken = null;
    // Déplacé ou modifié alors qu'il a déjà commencé, il ne s'annule plus (canCancel faux) : pas
    // de lien, et le dernier lien reste valable pour en lire l'état. La confirmation porte
    // toujours un lien.
    if (kind.carriesLink()
        && (kind == BookingMailer.FollowUp.CONFIRMED
            || BookingRules.canCancel(a, clock.instant()))) {
      manageToken = BookingRules.newToken();
      request.replaceManageToken(BookingRules.sha256(manageToken));
    }
    BookingMailer.Details details =
        new BookingMailer.Details(
            profiles
                .byPractitioner(a.getPractitionerId())
                .map(PublishedProfileView::displayName)
                .orElse(null),
            a.getStartsAt().toInstant(),
            a.getDurationMin(),
            a.getModality(),
            settings.read(a.getPractitionerId()).timezone());
    // Valeurs figées avant la validation : l'envoi part plus tard, sur un autre fil.
    String to = a.getGuestEmail();
    Locale locale = BookingMailer.localeOf(request.getLocale());
    String token = manageToken;
    mailer.afterCommit(
        "follow-up " + kind.name().toLowerCase(Locale.ROOT),
        () -> mailer.sendFollowUp(to, locale, kind, details, previous, token));
  }
}
