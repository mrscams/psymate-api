package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.request;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static com.psymate.testsupport.RightToLeftMails.RLM;
import static com.psymate.testsupport.RightToLeftMails.arabic;
import static com.psymate.testsupport.RightToLeftMails.arabicLocale;
import static com.psymate.testsupport.RightToLeftMails.assertArabic;
import static com.psymate.testsupport.RightToLeftMails.assertPlainTextOnly;
import static com.psymate.testsupport.RightToLeftMails.message;
import static com.psymate.testsupport.RightToLeftMails.recipient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.service.BookingMailer;
import com.psymate.agenda.service.BookingMailer.Change;
import com.psymate.agenda.service.BookingMailer.Details;
import com.psymate.agenda.service.BookingMailer.FollowUp;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.testsupport.RightToLeftMails;
import com.psymate.testsupport.RightToLeftMails.Mail;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M16-05 (G Q100, Q101) : les e-mails de la prise de rendez-vous ({@link BookingMailer}) en arabe
 * partent en {@code multipart/alternative} de droite à gauche, date et heure en chiffres latins et
 * en calendrier grégorien ; en français et en anglais, ils restent un corps {@code text/plain}
 * seul. Chaque e-mail est exercé sur le service ; le parcours d'une demande en {@code ar}
 * (vérification, demande transmise, nouvelle demande au praticien, confirmation par le praticien)
 * l'est de bout en bout. Même configuration que BookingNotificationsIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class, PublicBookingFixtures.ListenerConfig.class})
class BookingMailsArabicIT {

  private static final Pattern VERIFY_LINK =
      Pattern.compile("/rendez-vous/confirmer/([A-Za-z0-9_-]+)");

  /** Lundi 11 mars 2030, 09:00 à Paris (heure d'hiver). */
  private static final Details DETAILS =
      new Details(
          "Claire Lambert",
          Instant.parse("2030-03-11T08:00:00Z"),
          50,
          AppointmentModality.video,
          ZoneId.of("Europe/Paris"));

  private static final Change BEFORE =
      new Change(Instant.parse("2030-03-08T13:30:00Z"), 60, AppointmentModality.in_person);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BookingMailer mailer;

  private final RightToLeftMails mails = new RightToLeftMails();
  private final Locale ar = arabicLocale();
  private PublicBookingFixtures f;

  @BeforeEach
  void setUp() {
    f = new PublicBookingFixtures(mvc, jdbc);
  }

  /** Bloc de détails isolé : date grégorienne, heure, fuseau et durée en chiffres latins. */
  private static void assertDetails(Mail mail) {
    assertThat(mail.text()).contains("2030").contains("09:00").contains("Europe/Paris");
    assertThat(mail.html())
        .contains("<span dir=\"ltr\">11</span>")
        .contains("<span dir=\"ltr\">2030</span>")
        .contains("<span dir=\"ltr\">09:00</span>")
        .contains("<span dir=\"ltr\">Europe/Paris</span>")
        .contains("<span dir=\"ltr\">50</span>");
    // « PsyMate ليس خدمة طوارئ. » : ligne arabe qui commence par du latin, marquée U+200F.
    assertThat(mail.text()).contains(RLM + "PsyMate ");
  }

  // ---- au demandeur

  @Test
  void verificationInArabic() throws Exception {
    String to = recipient("ar-verify");
    mailer.sendVerification(to, ar, DETAILS, "tok_verify-1", 30);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("booking.mail.verify.subject"), true);
    assertDetails(mail);
    assertThat(mail.html()).contains("/rendez-vous/confirmer/tok_verify-1\" dir=\"ltr\">");
  }

  @Test
  void requestForwardedAndConfirmedInArabic() throws Exception {
    String pending = recipient("ar-pending");
    mailer.sendVerified(pending, ar, DETAILS, "tok_manage-1", false);
    Mail p = mails.await(pending);
    assertArabic(p, arabic("booking.mail.pending.subject"), true);
    assertDetails(p);
    assertThat(p.html()).contains("/rendez-vous/gerer/tok_manage-1\" dir=\"ltr\">");

    String confirmed = recipient("ar-confirmed");
    mailer.sendVerified(confirmed, ar, DETAILS, "tok_manage-2", true);
    Mail c = mails.await(confirmed);
    assertArabic(c, arabic("booking.mail.confirmed.subject"), true);
    assertDetails(c);
  }

  @Test
  void followUpsInArabic() throws Exception {
    for (FollowUp kind : FollowUp.values()) {
      String to = recipient("ar-" + kind.name().toLowerCase(Locale.ROOT));
      boolean moved = kind == FollowUp.RESCHEDULED || kind == FollowUp.MODIFIED;
      String token = kind.carriesLink() ? "tok_" + kind.name().toLowerCase(Locale.ROOT) : null;
      mailer.sendFollowUp(to, ar, kind, DETAILS, moved ? BEFORE : null, token);
      Mail mail = mails.await(to);
      String key = "booking.mail.followUp." + kind.name().toLowerCase(Locale.ROOT);
      assertArabic(mail, arabic(key + ".subject"), kind.carriesLink());
      assertDetails(mail);
      if (moved) {
        // Ancienne date (vendredi 8 mars, 14:30) et anciennes valeurs, chiffres latins.
        assertThat(mail.html())
            .contains("<span dir=\"ltr\">14:30</span>")
            .contains("<span dir=\"ltr\">60</span>");
      }
    }
  }

  /** Déplacement d'un rendez-vous déjà commencé : sans lien, avec la phrase « déjà commencé ». */
  @Test
  void rescheduledAfterStartInArabicHasNoLink() throws Exception {
    String to = recipient("ar-rescheduled-started");
    mailer.sendFollowUp(to, ar, FollowUp.RESCHEDULED, DETAILS, BEFORE, null);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("booking.mail.followUp.rescheduled.subject"), false);
    assertThat(mail.text()).contains(arabic("booking.mail.followUp.started").strip());
  }

  // ---- au praticien

  @Test
  void practitionerEmailsInArabic() throws Exception {
    String request = recipient("ar-new-request");
    mailer.sendPractitionerNew(request, ar, DETAILS, "Sara Benali", false);
    Mail r = mails.await(request);
    assertArabic(r, arabic("booking.mail.newRequest.subject"), true);
    assertThat(r.html())
        .contains("<span dir=\"ltr\">Sara Benali</span>")
        .contains("/agenda\" dir=\"ltr\">");

    String appointment = recipient("ar-new-appointment");
    mailer.sendPractitionerNew(appointment, ar, DETAILS, "Sara Benali", true);
    assertArabic(mails.await(appointment), arabic("booking.mail.newAppointment.subject"), true);

    String cancelled = recipient("ar-visitor-cancelled");
    mailer.sendPractitionerCancelled(cancelled, ar, DETAILS, null);
    Mail c = mails.await(cancelled);
    assertArabic(c, arabic("booking.mail.visitorCancelled.subject"), true);
    assertThat(c.text()).contains(arabic("booking.mail.unknownApplicant"));
  }

  // ---- français et anglais

  @Test
  void frenchAndEnglishStayPlainText() throws Exception {
    String fr = recipient("fr-verify");
    mailer.sendVerification(fr, ServedLanguage.FR.locale(), DETAILS, "tok_fr", 30);
    Mail french = mails.await(fr);
    assertPlainTextOnly(french, message(ServedLanguage.FR, "booking.mail.verify.subject"));
    assertThat(french.text()).contains("lundi 11 mars 2030").contains("PsyMate n'est pas");

    String en = recipient("en-follow-up");
    mailer.sendFollowUp(en, ServedLanguage.EN.locale(), FollowUp.CANCELLED, DETAILS, null, null);
    assertPlainTextOnly(
        mails.await(en), message(ServedLanguage.EN, "booking.mail.followUp.cancelled.subject"));

    String practitioner = recipient("fr-new-request");
    mailer.sendPractitionerNew(
        practitioner, ServedLanguage.FR.locale(), DETAILS, "Sara Benali", false);
    assertPlainTextOnly(
        mails.await(practitioner), message(ServedLanguage.FR, "booking.mail.newRequest.subject"));
  }

  // ---- de bout en bout

  /**
   * Une demande en ligne avec {@code locale = "ar"} chez un praticien dont la préférence {@code
   * locale} vaut {@code ar} : vérification, demande transmise, nouvelle demande au praticien et
   * confirmation par le praticien partent en arabe ; une demande en {@code fr} chez le même
   * praticien reçoit sa vérification en texte brut seul.
   */
  @Test
  void anArabicOnlineRequestGetsArabicEmailsFromVerificationToConfirmation() throws Exception {
    Map<String, Object> prefs = PublicBookingFixtures.offered("manual");
    prefs.put("locale", "ar");
    Practitioner p = f.practitioner(prefs);
    String email = applicantEmail();
    Map<String, Object> body = request(slot(3, "09:00"), email);
    body.put("locale", "ar");

    f.postRequest(p.slug(), body).andExpect(status().isAccepted());

    Mail verification = mails.await(email, arabic("booking.mail.verify.subject"));
    assertArabic(verification, arabic("booking.mail.verify.subject"), true);
    assertThat(verification.html()).contains("<span dir=\"ltr\">09:00</span>");
    Matcher token = VERIFY_LINK.matcher(verification.text());
    assertThat(token.find()).isTrue();

    MvcResult verified =
        f.verify(token.group(1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("pending"))
            .andReturn();
    String manageToken = PublicBookingFixtures.json(verified).path("manageToken").asString();

    assertArabic(
        mails.await(email, arabic("booking.mail.pending.subject")),
        arabic("booking.mail.pending.subject"),
        true);
    Mail practitioner = mails.await(p.email(), arabic("booking.mail.newRequest.subject"));
    assertArabic(practitioner, arabic("booking.mail.newRequest.subject"), true);
    assertThat(practitioner.html()).contains("<span dir=\"ltr\">Sara Benali</span>");

    UUID appointment = f.appointmentOf(manageToken);
    f.settle(appointment, mailer);
    f.practitionerPost(p, "/api/appointments/" + appointment + "/confirm", "{}")
        .andExpect(status().isOk());
    f.settle(appointment, mailer);
    assertArabic(
        mails.await(email, arabic("booking.mail.followUp.confirmed.subject")),
        arabic("booking.mail.followUp.confirmed.subject"),
        true);

    String french = applicantEmail();
    Map<String, Object> frBody = request(slot(3, "11:00"), french);
    f.postRequest(p.slug(), frBody).andExpect(status().isAccepted());
    assertPlainTextOnly(
        mails.await(french, message(ServedLanguage.FR, "booking.mail.verify.subject")),
        message(ServedLanguage.FR, "booking.mail.verify.subject"));
  }
}
