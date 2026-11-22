package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.PARIS;
import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.manageTokenIn;
import static com.psymate.agenda.PublicBookingFixtures.sha256;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.service.BookingMailer;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * E-mails de suivi au demandeur (M14-07, G Q81) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) et Mailpit : confirmation, refus, annulation et déplacement par le
 * praticien ; rien pour une note, une identité, un invité sans e-mail ou rattaché, un rendez-vous
 * du praticien ; nouveau lien de gestion à chaque e-mail qui le rappelle ; verrou partagé entre le
 * praticien et le demandeur. Les e-mails sont cherchés par objet exact ; l'envoi est écoulé ({@link
 * PublicBookingFixtures#settle}) avant tout comptage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class, PublicBookingFixtures.ListenerConfig.class})
class BookingNotificationsIT {

  static final String CONFIRMED = "Votre rendez-vous est confirmé";
  static final String CONFIRMED_EN = "Your appointment is confirmed";
  static final String REFUSED = "Votre demande n'a pas pu être acceptée";
  static final String CANCELLED = "Votre rendez-vous est annulé";
  static final String MOVED = "Votre rendez-vous est déplacé";
  static final String VISITOR_CANCELLED = "Rendez-vous en ligne annulé";

  private static final DateTimeFormatter DATE_FR =
      DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BookingMailer mailer;

  private PublicBookingFixtures f;
  private PublicBookingMails mails;

  @BeforeEach
  void setUp() {
    f = new PublicBookingFixtures(mvc, jdbc);
    mails = new PublicBookingMails();
  }

  private String url(UUID appointment, String action) {
    return "/api/appointments/" + appointment + (action == null ? "" : "/" + action);
  }

  /** Objet neutre (Q81) : ni nom, ni profession, ni chiffre (date, heure). */
  private static void assertNeutral(String subject, Practitioner p) {
    assertThat(subject)
        .doesNotContain(p.displayName())
        .doesNotContain("Lambert")
        .doesNotContainIgnoringCase("psycholog")
        .doesNotContainPattern("[0-9]");
  }

  // ---- confirmation

  @Test
  void confirmingAnOnlineRequestMailsTheApplicantOnceInTheirLanguage() throws Exception {
    Practitioner p = f.practitioner("manual");
    String fr = applicantEmail();
    UUID frAppointment = f.appointmentOf(f.verifiedRequest(p, slot(3, "09:00"), fr));
    String en = applicantEmail();
    String enVerify = f.pendingRequest(p, slot(3, "10:00"), en);
    jdbc.update(
        "update booking_request set locale = 'en' where verify_token_hash = ?", sha256(enVerify));
    f.verify(enVerify).andExpect(status().isOk());
    UUID enAppointment =
        jdbc.queryForObject(
            "select appointment_id from booking_request where verify_token_hash = ?",
            UUID.class,
            sha256(enVerify));
    f.settle(frAppointment, mailer);
    f.settle(enAppointment, mailer);

    f.practitionerPost(p, url(frAppointment, "confirm"), "{}").andExpect(status().isOk());
    f.practitionerPost(p, url(enAppointment, "confirm"), "{}").andExpect(status().isOk());
    f.settle(frAppointment, mailer);
    f.settle(enAppointment, mailer);

    String text = mails.awaitText(fr, CONFIRMED);
    assertThat(text).as("e-mail de confirmation").isNotNull();
    assertNeutral(CONFIRMED, p);
    assertThat(text)
        .contains(p.displayName())
        .contains("(fuseau Europe/Paris)")
        .contains("Durée : 60 minutes")
        .contains("/rendez-vous/gerer/")
        .contains("Ce lien remplace ceux des messages précédents, qui ne sont plus valables.")
        .contains("PsyMate n'est pas un service d'urgence.");
    assertThat(mails.countAfterQuiet(fr, CONFIRMED)).isEqualTo(1);

    String english = mails.awaitText(en, CONFIRMED_EN);
    assertThat(english).as("e-mail en anglais").isNotNull();
    assertNeutral(CONFIRMED_EN, p);
    assertThat(english)
        .contains("This link replaces the ones in previous messages")
        .contains("PsyMate is not an emergency service.");
    assertThat(mails.countAfterQuiet(en, CONFIRMED)).isZero();
    assertThat(mails.countAfterQuiet(en, CONFIRMED_EN)).isEqualTo(1);
  }

  @Test
  void confirmingOrCancellingATentativeCreatedByThePractitionerMailsNobody() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String body =
        "{\"startsAt\":\""
            + slot(4, "09:00")
            + "\",\"durationMin\":50,\"modality\":\"in_person\",\"tentative\":true,"
            + "\"guest\":{\"lastName\":\"Benali\",\"email\":\""
            + email
            + "\"}}";
    String created =
        PublicBookingFixtures.body(
            f.practitionerPost(p, "/api/appointments", body)
                .andExpect(status().isCreated())
                .andReturn());
    UUID id = UUID.fromString(JsonPath.read(created, "$.appointments[0].id"));

    f.practitionerPost(p, url(id, "confirm"), "{}").andExpect(status().isOk());
    f.practitionerPatch(p, url(id, null), "{\"startsAt\":\"" + slot(4, "11:00") + "\"}")
        .andExpect(status().isOk());
    f.practitionerPost(p, url(id, "cancel"), "{}").andExpect(status().isOk());
    f.settle(id, mailer);

    assertThat(mails.countAllAfterQuiet(email)).isZero();
  }

  // ---- refus et annulation, jamais le motif

  @Test
  void refusingARequestMailsTheRefusalWithoutThePractitionerReason() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String token = f.verifiedRequest(p, slot(3, "11:00"), email);
    UUID id = f.appointmentOf(token);
    f.settle(id, mailer);

    f.practitionerPost(p, url(id, "cancel"), "{\"reason\":\"Motif confidentiel 4711\"}")
        .andExpect(status().isOk());
    f.settle(id, mailer);

    String text = mails.awaitText(email, REFUSED);
    assertThat(text).as("e-mail de refus").isNotNull();
    assertNeutral(REFUSED, p);
    assertThat(text)
        .contains(p.displayName())
        .doesNotContain("Motif confidentiel")
        .doesNotContain("4711")
        .doesNotContain("/rendez-vous/gerer/");
    assertThat(mails.countAfterQuiet(email, CANCELLED)).isZero();
    assertThat(mails.countAfterQuiet(email, REFUSED)).isEqualTo(1);
    // Le refus ne remplace pas le lien : le demandeur voit toujours l'état de sa demande.
    f.manage(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.cancelledBy").value("practitioner"));
  }

  @Test
  void cancellingAPlannedOnlineAppointmentMailsTheCancellationWithoutTheReason() throws Exception {
    Practitioner p = f.practitioner("auto");
    String email = applicantEmail();
    UUID id = f.appointmentOf(f.verifiedRequest(p, slot(5, "10:00"), email));
    f.settle(id, mailer);

    f.practitionerPost(p, url(id, "cancel"), "{\"reason\":\"Raison privée du praticien\"}")
        .andExpect(status().isOk());
    // Second appel idempotent : ni événement ni e-mail de plus.
    f.practitionerPost(p, url(id, "cancel"), "{}").andExpect(status().isOk());
    f.settle(id, mailer);

    String text = mails.awaitText(email, CANCELLED);
    assertThat(text).as("e-mail d'annulation").isNotNull();
    assertNeutral(CANCELLED, p);
    assertThat(text).doesNotContain("Raison privée").doesNotContain("/rendez-vous/gerer/");
    assertThat(mails.countAfterQuiet(email, CANCELLED)).isEqualTo(1);
    assertThat(mails.countAfterQuiet(email, REFUSED)).isZero();
  }

  // ---- déplacement

  @Test
  void movingMailsBothDatesButNoteIdentityOrModalityChangesMailNothing() throws Exception {
    Practitioner p = f.practitioner("auto");
    String email = applicantEmail();
    Instant before = slot(4, "09:00");
    UUID id = f.appointmentOf(f.verifiedRequest(p, before, email));
    f.settle(id, mailer);
    assertThat(mails.awaitCount(email, "Votre rendez-vous", 1)).isEqualTo(1);

    f.practitionerPatch(p, url(id, null), "{\"planningNote\":\"Note privée de planification\"}")
        .andExpect(status().isOk());
    f.practitionerPatch(
            p, url(id, null), "{\"guest\":{\"lastName\":\"Benali\",\"firstName\":\"Sarah\"}}")
        .andExpect(status().isOk());
    f.practitionerPatch(p, url(id, null), "{\"modality\":\"in_person\"}")
        .andExpect(status().isOk());
    f.settle(id, mailer);
    assertThat(mails.countAllAfterQuiet(email)).as("aucun e-mail de plus").isEqualTo(1);

    Instant after = slot(4, "11:00");
    f.practitionerPatch(p, url(id, null), "{\"startsAt\":\"" + after + "\"}")
        .andExpect(status().isOk());
    f.settle(id, mailer);

    String text = mails.awaitText(email, MOVED);
    assertThat(text).as("e-mail de déplacement").isNotNull();
    assertNeutral(MOVED, p);
    assertThat(text)
        .contains(
            "Ancienne date : "
                + DATE_FR.format(before.atZone(PARIS))
                + " à 09:00 (fuseau Europe/Paris)")
        .contains("Date : " + DATE_FR.format(after.atZone(PARIS)) + " à 11:00")
        .contains("au cabinet")
        .contains("/rendez-vous/gerer/")
        .doesNotContain("Note privée");

    // Changer la durée déplace aussi la fin : un second e-mail.
    f.practitionerPatch(p, url(id, null), "{\"durationMin\":90}").andExpect(status().isOk());
    f.settle(id, mailer);
    assertThat(mails.awaitCount(email, MOVED, 2)).isEqualTo(2);
    assertThat(mails.countAfterQuiet(email, MOVED)).isEqualTo(2);
  }

  // ---- invité sans e-mail, rattaché à un patient

  @Test
  void guestWithoutEmailOrAttachedToAPatientGetsNoEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String erased = applicantEmail();
    UUID withoutEmail = f.appointmentOf(f.verifiedRequest(p, slot(6, "09:00"), erased));
    f.practitionerPatch(
            p, url(withoutEmail, null), "{\"guest\":{\"lastName\":\"Benali\",\"email\":\"\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.email").isEmpty());
    f.practitionerPost(p, url(withoutEmail, "confirm"), "{}").andExpect(status().isOk());

    String attachedEmail = applicantEmail();
    String attachedToken = f.verifiedRequest(p, slot(6, "10:00"), attachedEmail);
    UUID attached = f.appointmentOf(attachedToken);
    UUID patient = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Sara B.')",
        patient,
        f.tenant,
        p.id());
    f.practitionerPost(p, url(attached, "attach-patient"), "{\"patientId\":\"" + patient + "\"}")
        .andExpect(status().isOk());
    f.practitionerPost(p, url(attached, "confirm"), "{}").andExpect(status().isOk());
    f.practitionerPatch(p, url(attached, null), "{\"startsAt\":\"" + slot(6, "11:00") + "\"}")
        .andExpect(status().isOk());
    f.settle(withoutEmail, mailer);
    f.settle(attached, mailer);

    assertThat(mails.countAfterQuiet(erased, CONFIRMED)).isZero();
    assertThat(mails.countAfterQuiet(attachedEmail, CONFIRMED)).isZero();
    assertThat(mails.countAfterQuiet(attachedEmail, MOVED)).isZero();
    // Rattaché : aucun e-mail, donc le lien de gestion n'est pas remplacé et reste valable (K3.4).
    f.manage(attachedToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("confirmed"));
  }

  // ---- nouveau lien de gestion

  @Test
  void eachFollowUpWithALinkReplacesTheManageLink() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String first = f.verifiedRequest(p, slot(7, "09:00"), email);
    UUID id = f.appointmentOf(first);
    f.settle(id, mailer);

    f.practitionerPost(p, url(id, "confirm"), "{}").andExpect(status().isOk());
    f.settle(id, mailer);
    String second = manageTokenIn(mails.awaitText(email, CONFIRMED));
    assertThat(second).hasSize(43).isNotEqualTo(first);

    f.manage(first)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOKING_TOKEN_INVALID"));
    f.cancel(first)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOKING_TOKEN_INVALID"));
    f.manage(second).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("confirmed"));

    f.practitionerPatch(p, url(id, null), "{\"startsAt\":\"" + slot(7, "11:00") + "\"}")
        .andExpect(status().isOk());
    f.settle(id, mailer);
    String third = manageTokenIn(mails.awaitText(email, MOVED));
    assertThat(third).hasSize(43).isNotEqualTo(second);
    f.manage(second).andExpect(status().isNotFound());
    f.manage(third).andExpect(status().isOk());
    // Seule l'empreinte du dernier jeton est gardée.
    assertThat(f.state(id).get("manage_token_hash")).isEqualTo(sha256(third));
  }

  // ---- courses entre le praticien et le demandeur (verrou du praticien)

  private List<Integer> race(Callable<Integer> a, Callable<Integer> b) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (Callable<Integer> call : List.of(a, b)) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return call.call();
                }));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> r : results) {
        statuses.add(r.get(30, TimeUnit.SECONDS));
      }
      return statuses;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void confirmAndVisitorCancelInParallelLetExactlyOneWinAndSendOneEmail() throws Exception {
    for (int round = 0; round < 3; round++) {
      Practitioner p = f.practitioner("manual");
      String email = applicantEmail();
      String token = f.verifiedRequest(p, slot(8 + round, "09:00"), email);
      UUID id = f.appointmentOf(token);
      f.settle(id, mailer);

      List<Integer> statuses =
          race(
              () ->
                  f.practitionerPost(p, url(id, "confirm"), "{}")
                      .andReturn()
                      .getResponse()
                      .getStatus(),
              () -> f.cancel(token).andReturn().getResponse().getStatus());
      f.settle(id, mailer);

      Map<String, Object> state = f.state(id);
      if (statuses.get(0) == 200) {
        // Confirmé d'abord : l'e-mail de confirmation a remplacé le lien, l'ancien ne vaut plus.
        assertThat(statuses.get(1)).isEqualTo(404);
        assertThat(state.get("status")).isEqualTo("planned");
        assertThat(state.get("closed_by")).isNull();
        assertThat(mails.awaitCount(email, CONFIRMED, 1)).isEqualTo(1);
        assertThat(mails.countAfterQuiet(p.email(), VISITOR_CANCELLED)).isZero();
        assertThat(f.publications(AppointmentCancelled.class, id)).isZero();
      } else {
        // Annulé d'abord par le demandeur : la confirmation arrive trop tard.
        assertThat(statuses).containsExactly(409, 200);
        assertThat(state.get("status")).isEqualTo("cancelled");
        assertThat(state.get("closed_by")).isEqualTo("visitor");
        assertThat(mails.awaitCount(p.email(), VISITOR_CANCELLED, 1)).isEqualTo(1);
        assertThat(mails.countAfterQuiet(email, CONFIRMED)).isZero();
        assertThat(f.publications(AppointmentCancelled.class, id)).isEqualTo(1);
      }
      assertThat(
              mails.countAfterQuiet(email, CONFIRMED)
                  + mails.countAfterQuiet(p.email(), VISITOR_CANCELLED))
          .as("un seul e-mail part")
          .isEqualTo(1);
    }
  }

  @Test
  void practitionerAndVisitorCancelInParallelCancelOnceAndSendOneEmail() throws Exception {
    for (int round = 0; round < 3; round++) {
      Practitioner p = f.practitioner("manual");
      String email = applicantEmail();
      String token = f.verifiedRequest(p, slot(8 + round, "10:00"), email);
      UUID id = f.appointmentOf(token);
      f.settle(id, mailer);

      List<Integer> statuses =
          race(
              () ->
                  f.practitionerPost(p, url(id, "cancel"), "{}")
                      .andReturn()
                      .getResponse()
                      .getStatus(),
              () -> f.cancel(token).andReturn().getResponse().getStatus());
      f.settle(id, mailer);

      // Les deux répondent 200 : le second voit un rendez-vous déjà annulé (idempotent).
      assertThat(statuses).containsExactly(200, 200);
      Map<String, Object> state = f.state(id);
      assertThat(state.get("status")).isEqualTo("cancelled");
      assertThat(f.publications(AppointmentCancelled.class, id)).isEqualTo(1);
      if ("practitioner".equals(state.get("closed_by"))) {
        assertThat(mails.awaitCount(email, REFUSED, 1)).isEqualTo(1);
        assertThat(mails.countAfterQuiet(p.email(), VISITOR_CANCELLED)).isZero();
      } else {
        assertThat(state.get("closed_by")).isEqualTo("visitor");
        assertThat(mails.awaitCount(p.email(), VISITOR_CANCELLED, 1)).isEqualTo(1);
        assertThat(mails.countAfterQuiet(email, REFUSED)).isZero();
      }
      assertThat(
              mails.countAfterQuiet(email, REFUSED)
                  + mails.countAfterQuiet(p.email(), VISITOR_CANCELLED))
          .as("un seul e-mail part")
          .isEqualTo(1);
    }
  }
}
