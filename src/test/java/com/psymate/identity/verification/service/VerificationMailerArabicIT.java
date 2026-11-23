package com.psymate.identity.verification.service;

import static com.psymate.testsupport.RightToLeftMails.arabic;
import static com.psymate.testsupport.RightToLeftMails.arabicLocale;
import static com.psymate.testsupport.RightToLeftMails.assertArabic;
import static com.psymate.testsupport.RightToLeftMails.assertPlainTextOnly;
import static com.psymate.testsupport.RightToLeftMails.message;
import static com.psymate.testsupport.RightToLeftMails.recipient;
import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.verification.VerificationTestConfig;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.testsupport.RightToLeftMails;
import com.psymate.testsupport.RightToLeftMails.Mail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * M16-05 (G Q100) : l'e-mail de {@link VerificationMailer} (dossier soumis, aux administrateurs) en
 * arabe part en {@code multipart/alternative} de droite à gauche ; en français et en anglais, il
 * reste un corps {@code text/plain} seul. Même configuration que PurgeJobIT.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(VerificationTestConfig.class)
class VerificationMailerArabicIT {

  @Autowired private VerificationMailer mailer;

  private final RightToLeftMails mails = new RightToLeftMails();

  @Test
  void submittedDossierInArabic() throws Exception {
    String to = recipient("ar-submitted");
    mailer.sendSubmitted(to, arabicLocale(), "Claire Lambert", "psychologist");
    Mail mail = mails.await(to);
    assertArabic(
        mail, arabic("verification.mail.submitted.subject", "PsyMate", "Claire Lambert"), true);
    assertThat(mail.html())
        .contains("<span dir=\"ltr\">PsyMate: Claire Lambert</span>")
        .contains("(<span dir=\"ltr\">psychologist</span>)")
        .contains("/admin/verifications\" dir=\"ltr\">");
  }

  @Test
  void frenchAndEnglishStayPlainText() throws Exception {
    String fr = recipient("fr-submitted");
    mailer.sendSubmitted(fr, ServedLanguage.FR.locale(), "Claire Lambert", "psychologist");
    assertPlainTextOnly(
        mails.await(fr),
        message(
            ServedLanguage.FR, "verification.mail.submitted.subject", "PsyMate", "Claire Lambert"));

    String en = recipient("en-submitted");
    mailer.sendSubmitted(en, ServedLanguage.EN.locale(), "Claire Lambert", "psychologist");
    assertPlainTextOnly(
        mails.await(en),
        message(
            ServedLanguage.EN, "verification.mail.submitted.subject", "PsyMate", "Claire Lambert"));
  }
}
