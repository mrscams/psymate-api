package com.psymate.identity.verification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** E-mail aux administrateurs quand un dossier est soumis (aucun justificatif joint). */
@Component
class VerificationMailer {

  private final JavaMailSender sender;
  private final VerificationMessages messages;
  private final String from;
  private final String appName;
  private final String webBaseUrl;

  VerificationMailer(
      JavaMailSender sender,
      VerificationMessages messages,
      @Value("${app.mail.from}") String from,
      @Value("${app.name}") String appName,
      @Value("${app.web.base-url}") String webBaseUrl) {
    this.sender = sender;
    this.messages = messages;
    this.from = from;
    this.appName = appName;
    this.webBaseUrl = webBaseUrl;
  }

  void sendSubmitted(String to, Locale locale, String practitionerName, String profession) {
    String link = webBaseUrl + "/admin/verifications";
    send(
        to,
        messages.get("verification.mail.submitted.subject", locale, appName, practitionerName),
        messages.get(
            "verification.mail.submitted.body",
            locale,
            appName,
            practitionerName,
            profession,
            link));
  }

  private void send(String to, String subject, String text) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(from, appName);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text);
      sender.send(message);
    } catch (MessagingException | UnsupportedEncodingException e) {
      throw new MailPreparationException(e);
    }
  }
}
