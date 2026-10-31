package com.psymate.identity.auth;

import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Envoi des e-mails d'authentification (SMTP : Mailpit en local). */
@Component
class AuthMailer {

  private final JavaMailSender sender;
  private final AuthMessages messages;
  private final String from;
  private final String appName;
  private final String webBaseUrl;

  AuthMailer(
      JavaMailSender sender,
      AuthMessages messages,
      @Value("${app.mail.from}") String from,
      @Value("${app.name}") String appName,
      @Value("${app.web.base-url}") String webBaseUrl) {
    this.sender = sender;
    this.messages = messages;
    this.from = from;
    this.appName = appName;
    this.webBaseUrl = webBaseUrl;
  }

  void sendPasswordReset(String to, String token, Locale locale) {
    String link = webBaseUrl + "/reset-password?token=" + token;
    send(
        to,
        messages.get("mail.reset.subject", locale, appName),
        messages.get("mail.reset.body", locale, appName, link, 30));
  }

  /** Lien de confirmation d'e-mail (24 h, usage unique). */
  void sendEmailConfirmation(String to, String token, Locale locale) {
    String link = webBaseUrl + "/confirmer-email/" + token;
    send(
        to,
        messages.get("mail.confirm.subject", locale, appName),
        messages.get("mail.confirm.body", locale, appName, link, 24));
  }

  /** Information envoyée quand une inscription vise une adresse déjà utilisée. */
  void sendAccountExists(String to, Locale locale) {
    String link = webBaseUrl + "/connexion";
    send(
        to,
        messages.get("mail.exists.subject", locale, appName),
        messages.get("mail.exists.body", locale, appName, link));
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
    } catch (jakarta.mail.MessagingException | UnsupportedEncodingException e) {
      throw new org.springframework.mail.MailPreparationException(e);
    } catch (MailException e) {
      throw e;
    }
  }
}
