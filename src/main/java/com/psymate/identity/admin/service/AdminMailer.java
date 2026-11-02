package com.psymate.identity.admin.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** E-mails de l'administration : décisions envoyées aux praticiens, rappel aux administrateurs. */
@Component
class AdminMailer {

  private final JavaMailSender sender;
  private final AdminMessages messages;
  private final String from;
  private final String appName;
  private final String webBaseUrl;

  AdminMailer(
      JavaMailSender sender,
      AdminMessages messages,
      @Value("${app.mail.from}") String from,
      @Value("${app.name}") String appName,
      @Value("${app.web.base-url}") String webBaseUrl) {
    this.sender = sender;
    this.messages = messages;
    this.from = from;
    this.appName = appName;
    this.webBaseUrl = webBaseUrl;
  }

  void sendApproved(String to, Locale locale, String name) {
    send(
        to,
        messages.get("admin.mail.approved.subject", locale, appName),
        messages.get("admin.mail.approved.body", locale, appName, name, webBaseUrl));
  }

  void sendNeedsMore(String to, Locale locale, String name, String message) {
    send(
        to,
        messages.get("admin.mail.needs_more.subject", locale, appName),
        messages.get(
            "admin.mail.needs_more.body",
            locale,
            appName,
            name,
            message,
            webBaseUrl + "/compte/dossier"));
  }

  void sendRejected(String to, Locale locale, String name, String reason) {
    send(
        to,
        messages.get("admin.mail.rejected.subject", locale, appName),
        messages.get("admin.mail.rejected.body", locale, appName, name, reason));
  }

  /** Suspension manuelle (M1-14b) : motif et rappel du recours. */
  void sendSuspended(String to, Locale locale, String name, String reason) {
    send(
        to,
        messages.get("admin.mail.suspended.subject", locale, appName),
        messages.get(
            "admin.mail.suspended.body",
            locale,
            appName,
            name,
            reason,
            webBaseUrl + "/compte-suspendu"));
  }

  void sendReactivated(String to, Locale locale, String name) {
    send(
        to,
        messages.get("admin.mail.reactivated.subject", locale, appName),
        messages.get("admin.mail.reactivated.body", locale, appName, name, webBaseUrl));
  }

  void sendAppealAccepted(String to, Locale locale, String name, String message) {
    send(
        to,
        messages.get("admin.mail.appeal_accepted.subject", locale, appName),
        messages.get(
            "admin.mail.appeal_accepted.body", locale, appName, name, message, webBaseUrl));
  }

  void sendAppealDismissed(String to, Locale locale, String name, String message) {
    send(
        to,
        messages.get("admin.mail.appeal_dismissed.subject", locale, appName),
        messages.get("admin.mail.appeal_dismissed.body", locale, appName, name, message));
  }

  void sendOverdue(String to, Locale locale, int count, String list) {
    send(
        to,
        messages.get("admin.mail.overdue.subject", locale, appName, count),
        messages.get(
            "admin.mail.overdue.body",
            locale,
            appName,
            count,
            list,
            webBaseUrl + "/admin/verifications"));
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
