package com.psymate.identity.admin.service;

import static com.psymate.testsupport.RightToLeftMails.RLM;
import static com.psymate.testsupport.RightToLeftMails.arabic;
import static com.psymate.testsupport.RightToLeftMails.arabicLocale;
import static com.psymate.testsupport.RightToLeftMails.assertArabic;
import static com.psymate.testsupport.RightToLeftMails.assertPlainTextOnly;
import static com.psymate.testsupport.RightToLeftMails.message;
import static com.psymate.testsupport.RightToLeftMails.recipient;
import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.admin.AdminTestConfig;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.testsupport.RightToLeftMails;
import com.psymate.testsupport.RightToLeftMails.Mail;
import com.psymate.testsupport.TestAdmins;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * M16-05 (G Q100, Q101) : les huit e-mails d'{@link AdminMailer} (décisions, suspension, recours,
 * relance) en arabe partent en {@code multipart/alternative} de droite à gauche ; en français et en
 * anglais, ils restent un corps {@code text/plain} seul. La relance est aussi exercée de bout en
 * bout pour un administrateur dont {@code practitioner.locale} vaut {@code ar} : date de soumission
 * en chiffres latins et en calendrier grégorien. Même configuration qu'AbstractAccountsIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminTestConfig.class)
class AdminMailerArabicIT {

  private static final String APP = "PsyMate";
  private static final String NAME = "Claire Lambert";

  @Autowired private AdminMailer mailer;
  @Autowired private AdminVerificationService service;
  @Autowired private JdbcTemplate jdbc;

  private final RightToLeftMails mails = new RightToLeftMails();
  private final Locale ar = arabicLocale();
  private UUID tenant;

  @BeforeEach
  void setUp() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet arabe')", tenant);
  }

  /** Aucun administrateur actif ni dossier en attente laissé en base partagée. */
  @AfterEach
  void cleanUp() {
    TestAdmins.closeInTenant(jdbc, tenant);
    jdbc.update(
        "update practitioner set verification_status = 'closed' where tenant_id = ?"
            + " and role <> 'admin'",
        tenant);
  }

  @Test
  void approvedInArabic() throws Exception {
    String to = recipient("ar-approved");
    mailer.sendApproved(to, ar, NAME);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("admin.mail.approved.subject", APP), true);
    assertThat(mail.html()).contains("<span dir=\"ltr\">" + NAME + "</span>");
  }

  @Test
  void needsMoreInArabic() throws Exception {
    String to = recipient("ar-needs-more");
    mailer.sendNeedsMore(to, ar, NAME, "يُرجى إضافة الشهادة <PDF>.");
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("admin.mail.needs_more.subject", APP), true);
    assertThat(mail.html())
        .contains("&lt;<span dir=\"ltr\">PDF</span>&gt;")
        .contains("/compte/dossier\" dir=\"ltr\">");
  }

  @Test
  void rejectedInArabic() throws Exception {
    String to = recipient("ar-rejected");
    mailer.sendRejected(to, ar, NAME, "مستند غير مقروء");
    assertArabic(mails.await(to), arabic("admin.mail.rejected.subject", APP), false);
  }

  /** Motif latin ponctué : sa ligne est isolée entière, sa ponctuation reste à sa place. */
  @Test
  void rejectedWithAPunctuatedLatinReasonInArabic() throws Exception {
    String to = recipient("ar-rejected-latin");
    String reason = "Diplôme illisible (scan flou) ; merci de le renvoyer ? Cordialement.";
    mailer.sendRejected(to, ar, NAME, reason);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("admin.mail.rejected.subject", APP), false);
    assertThat(mail.html()).contains("<span dir=\"ltr\">" + reason + "</span>");
    assertThat(mail.text()).contains("\n" + reason + "\n");
  }

  @Test
  void suspendedInArabic() throws Exception {
    String to = recipient("ar-suspended");
    mailer.sendSuspended(to, ar, NAME, "سبب الإيقاف");
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("admin.mail.suspended.subject", APP), true);
    assertThat(mail.html()).contains("/compte-suspendu\" dir=\"ltr\">");
  }

  @Test
  void reactivatedInArabic() throws Exception {
    String to = recipient("ar-reactivated");
    mailer.sendReactivated(to, ar, NAME);
    assertArabic(mails.await(to), arabic("admin.mail.reactivated.subject", APP), true);
  }

  @Test
  void appealAcceptedInArabic() throws Exception {
    String to = recipient("ar-appeal-accepted");
    mailer.sendAppealAccepted(to, ar, NAME, "تمت مراجعة الملف.");
    assertArabic(mails.await(to), arabic("admin.mail.appeal_accepted.subject", APP), true);
  }

  @Test
  void appealDismissedInArabic() throws Exception {
    String to = recipient("ar-appeal-dismissed");
    mailer.sendAppealDismissed(to, ar, NAME, "القرار نهائي.");
    assertArabic(mails.await(to), arabic("admin.mail.appeal_dismissed.subject", APP), false);
  }

  @Test
  void overdueInArabic() throws Exception {
    String to = recipient("ar-overdue");
    mailer.sendOverdue(to, ar, 1234, "- Dr Test — تاريخ التقديم: 23/11/2026\n");
    Mail mail = mails.await(to);
    // {1} est un nombre : MessageFormat l'écrit en chiffres latins avec ar-u-nu-latn (G Q101).
    assertArabic(mail, arabic("admin.mail.overdue.subject", APP, 1234), true);
    assertThat(mail.subject()).contains("1,234");
    assertThat(mail.text()).contains("1,234").contains(RLM + "- Dr Test — ");
  }

  /**
   * De bout en bout : relance quotidienne à un administrateur en arabe, date en chiffres latins.
   */
  @Test
  void overdueReminderToAnArabicAdminHasLatinDigitsAndAGregorianDate() throws Exception {
    UUID admin = insert("Admin Arabe", "approved", "admin", true);
    UUID late = insert("Dr Arabe En Retard", "under_review", "practitioner", false);
    Instant submittedAt = Instant.parse("2026-01-15T10:00:00Z");
    jdbc.update(
        "insert into security_event (id, practitioner_id, kind, occurred_at) values (?, ?,"
            + " 'verification_submitted', ?)",
        UUID.randomUUID(),
        late,
        Timestamp.from(submittedAt));

    int overdue = service.sendOverdueReminders();
    assertThat(overdue).isPositive();

    Mail mail = mails.await(admin + "@example.test");
    assertArabic(mail, arabic("admin.mail.overdue.subject", APP, overdue), true);
    // Calendrier grégorien : l'année 2026, pas 1447 ; chiffres latins (vérifiés par assertArabic).
    assertThat(mail.text()).contains("Dr Arabe En Retard").contains("15").doesNotContain("1447");
    assertThat(mail.html())
        .contains("<span dir=\"ltr\">Dr Arabe En Retard</span>")
        .contains("<span dir=\"ltr\">2026</span>");
  }

  @Test
  void frenchAndEnglishStayPlainText() throws Exception {
    String fr = recipient("fr-approved");
    mailer.sendApproved(fr, ServedLanguage.FR.locale(), NAME);
    assertPlainTextOnly(
        mails.await(fr), message(ServedLanguage.FR, "admin.mail.approved.subject", APP));

    String en = recipient("en-overdue");
    mailer.sendOverdue(en, ServedLanguage.EN.locale(), 2, "- Dr Test\n");
    assertPlainTextOnly(
        mails.await(en), message(ServedLanguage.EN, "admin.mail.overdue.subject", APP, 2));
  }

  private UUID insert(String displayName, String status, String role, boolean totp) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, locale, profession, phone,"
            + " professional_number, address_street, address_postal_code, address_city,"
            + " verification_status, role, totp_secret_enc, totp_enabled_at)"
            + " values (?, ?, ?, ?, now(), 'x', 'Maya', 'Kaddour', ?, 'ar', 'psychologist',"
            + " '06 12 34 56 78', 'ADELI123456', '12 rue des Lilas', '69003', 'Lyon',"
            + " ?, ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        displayName,
        status,
        role,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null);
    return id;
  }
}
