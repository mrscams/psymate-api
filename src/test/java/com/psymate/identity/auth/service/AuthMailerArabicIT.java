package com.psymate.identity.auth.service;

import static com.psymate.testsupport.RightToLeftMails.arabic;
import static com.psymate.testsupport.RightToLeftMails.arabicLocale;
import static com.psymate.testsupport.RightToLeftMails.assertArabic;
import static com.psymate.testsupport.RightToLeftMails.assertPlainTextOnly;
import static com.psymate.testsupport.RightToLeftMails.message;
import static com.psymate.testsupport.RightToLeftMails.recipient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.auth.AuthTestConfig;
import com.psymate.identity.auth.RegistrationFixtures;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.testsupport.RightToLeftMails;
import com.psymate.testsupport.RightToLeftMails.Mail;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * M16-05 (G Q100, Q101) : les six e-mails d'{@link AuthMailer} en arabe partent en {@code
 * multipart/alternative} de droite à gauche ; en français et en anglais, ils restent un corps
 * {@code text/plain} seul. Même configuration qu'AuthIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
class AuthMailerArabicIT {

  private static final String APP = "PsyMate";

  @Autowired private AuthMailer mailer;
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private final RightToLeftMails mails = new RightToLeftMails();
  private final Locale ar = arabicLocale();

  @Test
  void passwordResetInArabic() throws Exception {
    String to = recipient("ar-reset");
    mailer.sendPasswordReset(to, "tok_reset-1", ar);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("mail.reset.subject", APP), true);
    assertThat(mail.html()).contains("/reset-password?token=tok_reset-1");
    // {2} = 30 minutes, en chiffres latins (G Q101).
    assertThat(mail.text()).contains(": 30)");
  }

  @Test
  void emailConfirmationInArabic() throws Exception {
    String to = recipient("ar-confirm");
    mailer.sendEmailConfirmation(to, "tok_confirm-1", ar);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("mail.confirm.subject", APP), true);
    assertThat(mail.html()).contains("/confirmer-email/tok_confirm-1\" dir=\"ltr\">");
    assertThat(mail.text()).contains(": 24)");
  }

  @Test
  void accountExistsInArabic() throws Exception {
    String to = recipient("ar-exists");
    mailer.sendAccountExists(to, ar);
    assertArabic(mails.await(to), arabic("mail.exists.subject", APP), true);
  }

  @Test
  void emailChangeInArabic() throws Exception {
    String to = recipient("ar-change");
    mailer.sendEmailChange(to, "tok_change-1", ar);
    assertArabic(mails.await(to), arabic("mail.change.subject", APP), true);
  }

  @Test
  void emailChangedInArabicIsolatesBothAddresses() throws Exception {
    String to = recipient("ar-changed");
    String newEmail = recipient("ar-new");
    mailer.sendEmailChanged(to, newEmail, ar);
    Mail mail = mails.await(to);
    assertArabic(mail, arabic("mail.changed.subject", APP), false);
    assertThat(mail.html()).contains("<span dir=\"ltr\">" + newEmail + "</span>");
    assertThat(mail.html()).containsPattern("<span dir=\"ltr\">[^<@]+@[^<]+</span>");
  }

  @Test
  void changeTargetExistsInArabic() throws Exception {
    String to = recipient("ar-change-exists");
    mailer.sendChangeTargetExists(to, ar);
    assertArabic(mails.await(to), arabic("mail.changeExists.subject", APP), true);
  }

  /** Les e-mails français et anglais restent un corps text/plain seul. */
  @Test
  void frenchAndEnglishStayPlainText() throws Exception {
    String fr = recipient("fr-reset");
    mailer.sendPasswordReset(fr, "tok_fr", ServedLanguage.FR.locale());
    Mail french = mails.await(fr);
    assertPlainTextOnly(french, message(ServedLanguage.FR, "mail.reset.subject", APP));
    // Texte inchangé (au saut de ligne final près, que Mailpit peut ôter).
    assertThat(french.text().replace("\r\n", "\n").strip())
        .isEqualTo(
            message(
                    ServedLanguage.FR,
                    "mail.reset.body",
                    APP,
                    "http://localhost:5173/reset-password?token=tok_fr",
                    30)
                .strip());

    String en = recipient("en-changed");
    mailer.sendEmailChanged(en, "new@example.test", ServedLanguage.EN.locale());
    assertPlainTextOnly(mails.await(en), message(ServedLanguage.EN, "mail.changed.subject", APP));
  }

  /** De bout en bout : une inscription en arabe reçoit la confirmation en arabe. */
  @Test
  void arabicRegistrationGetsTheArabicConfirmation() throws Exception {
    String email = recipient("ar-register");
    mvc.perform(
            post("/api/auth/register")
                .header("Accept-Language", "ar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JsonMapper.builder()
                        .build()
                        .writeValueAsString(
                            RegistrationFixtures.payload(
                                email, RegistrationFixtures.PASSWORD, "psychologist"))))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "select locale from practitioner where email_normalized = ?", String.class, email))
        .isEqualTo("ar");

    Mail mail = mails.await(email);
    assertArabic(mail, arabic("mail.confirm.subject", APP), true);
    assertThat(mail.html()).contains("/confirmer-email/");
  }
}
