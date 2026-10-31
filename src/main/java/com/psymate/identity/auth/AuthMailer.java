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
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(from, appName);
      helper.setTo(to);
      helper.setSubject(messages.get("mail.reset.subject", locale, appName));
      helper.setText(messages.get("mail.reset.body", locale, appName, link, 30));
      sender.send(message);
    } catch (jakarta.mail.MessagingException | UnsupportedEncodingException e) {
      throw new org.springframework.mail.MailPreparationException(e);
    } catch (MailException e) {
      throw e;
    }
  }
}
