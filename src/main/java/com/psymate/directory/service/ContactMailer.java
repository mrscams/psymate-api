package com.psymate.directory.service;

import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Relais par e-mail d'un message du formulaire de contact vers le praticien (SMTP : Mailpit en
 * local). Le corps contient le nom, l'e-mail, l'objet et le message du visiteur ; jamais son IP.
 */
@Component
class ContactMailer {

  /** Message à relayer. */
  record ContactMessage(
      String to, String senderName, String senderEmail, String subject, String message) {}

  private final JavaMailSender sender;
  private final MessageSource messages;
  private final String from;
  private final String appName;

  ContactMailer(
      JavaMailSender sender,
      @Value("${app.mail.from}") String from,
      @Value("${app.name}") String appName) {
    this.sender = sender;
    this.from = from;
    this.appName = appName;
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    this.messages = source;
  }

  /** Envoie le message ; lève une {@link MailException} en cas d'échec. */
  void send(ContactMessage m, Locale locale) {
    Locale l = locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
    String senderName = singleLine(m.senderName());
    String subject = m.subject() == null || m.subject().isBlank() ? null : singleLine(m.subject());
    String shownSubject =
        subject != null ? subject : messages.getMessage("contact.mail.noSubject", null, l);
    String mailSubject =
        messages.getMessage("contact.mail.subject", new Object[] {appName, senderName}, l);
    String body =
        messages.getMessage(
            "contact.mail.body",
            new Object[] {appName, senderName, m.senderEmail(), shownSubject, m.message()},
            l);
    try {
      MimeMessage mime = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
      helper.setFrom(from, appName);
      helper.setTo(m.to());
      helper.setReplyTo(m.senderEmail());
      helper.setSubject(mailSubject);
      helper.setText(body);
      sender.send(mime);
    } catch (jakarta.mail.MessagingException | UnsupportedEncodingException e) {
      throw new MailPreparationException(e);
    }
  }

  /** Une valeur saisie ne doit jamais pouvoir injecter un en-tête : retours à la ligne retirés. */
  static String singleLine(String value) {
    return value == null ? "" : value.replaceAll("[\\p{Cntrl}]+", " ").trim();
  }
}
