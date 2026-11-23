package com.psymate.directory.service;

import static com.psymate.testsupport.RightToLeftMails.arabic;
import static com.psymate.testsupport.RightToLeftMails.arabicLocale;
import static com.psymate.testsupport.RightToLeftMails.assertArabic;
import static com.psymate.testsupport.RightToLeftMails.assertPlainTextOnly;
import static com.psymate.testsupport.RightToLeftMails.message;
import static com.psymate.testsupport.RightToLeftMails.recipient;
import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.directory.service.ContactMailer.ContactMessage;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.testsupport.RightToLeftMails;
import com.psymate.testsupport.RightToLeftMails.Mail;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * M16-05 (G Q100) : le relais du formulaire de contact ({@link ContactMailer}) en arabe part en
 * {@code multipart/alternative} de droite à gauche, le texte du visiteur échappé ; en français et
 * en anglais, il reste un corps {@code text/plain} seul. Même configuration que ContactPurgeIT.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class})
class ContactMailerArabicIT {

  @Autowired private ContactMailer mailer;

  private final RightToLeftMails mails = new RightToLeftMails();

  @Test
  void contactRelayInArabicEscapesTheVisitorMessage() throws Exception {
    String to = recipient("ar-contact");
    String visitor = recipient("visiteur");
    mailer.send(
        new ContactMessage(
            to,
            "Sara Benali",
            visitor,
            "موعد",
            "مرحبًا،\n<b>Bonjour</b> & merci, voir https://example.test/page?a=1&b=2"),
        arabicLocale());
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("contact.mail.subject", "PsyMate", "Sara Benali"), true);
    assertThat(mail.html())
        .contains("<span dir=\"ltr\">" + visitor + "</span>")
        .contains("<span dir=\"ltr\">Sara Benali</span>")
        .contains("&lt;<span dir=\"ltr\">b</span>&gt;")
        .doesNotContain("<b>")
        .contains(
            "<a href=\"https://example.test/page?a=1&amp;b=2\" dir=\"ltr\">"
                + "https://example.test/page?a=1&amp;b=2</a>");
    // Le texte du visiteur reste tel quel dans le texte brut ; aucune marque sur une ligne latine.
    assertThat(mail.text()).contains("<b>Bonjour</b> & merci");
    // « 30 » jours, en chiffres latins (G Q101).
    assertThat(mail.html()).contains("<span dir=\"ltr\">30</span>");
  }

  @Test
  void contactRelayWithoutSubjectInArabic() throws Exception {
    String to = recipient("ar-contact-nosubject");
    mailer.send(
        new ContactMessage(to, "سارة", recipient("visiteur"), null, "سؤال قصير"), arabicLocale());
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("contact.mail.subject", "PsyMate", "سارة"), false);
    assertThat(mail.text()).contains(arabic("contact.mail.noSubject"));
  }

  @Test
  void frenchAndEnglishStayPlainText() throws Exception {
    String fr = recipient("fr-contact");
    mailer.send(
        new ContactMessage(fr, "Sara Benali", recipient("visiteur"), null, "Bonjour"),
        ServedLanguage.FR.locale());
    assertPlainTextOnly(
        mails.await(fr),
        message(ServedLanguage.FR, "contact.mail.subject", "PsyMate", "Sara Benali"));

    String en = recipient("en-contact");
    mailer.send(
        new ContactMessage(en, "Sara Benali", recipient("visiteur"), "Hello", "Hello"),
        ServedLanguage.EN.locale());
    assertPlainTextOnly(
        mails.await(en),
        message(ServedLanguage.EN, "contact.mail.subject", "PsyMate", "Sara Benali"));
  }
}
