package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.AuthExceptions.DuplicateEmailException;
import com.psymate.identity.auth.AuthExceptions.InvalidTokenException;
import com.psymate.identity.auth.AuthExceptions.PasswordPolicyException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
  static final String KIND_PASSWORD_RESET = "password_reset";
  static final Duration RESET_TTL = Duration.ofMinutes(30);

  private static final String PREFS_COACH =
      "{\"vocabulary.subjectLabel\":\"client\",\"entryTypes.medicationVisible\":false}";
  private static final String PREFS_PSYCHIATRIST = "{\"entryTypes.medicationVisible\":true}";

  private final PractitionerRepository practitioners;
  private final AuthTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final AuthMailer mailer;
  private final TaskExecutor mailExecutor;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  AuthService(
      PractitionerRepository practitioners,
      AuthTokenRepository tokens,
      PasswordEncoder encoder,
      AuthMailer mailer,
      TaskExecutor mailExecutor,
      Clock clock) {
    this.practitioners = practitioners;
    this.tokens = tokens;
    this.encoder = encoder;
    this.mailer = mailer;
    this.mailExecutor = mailExecutor;
    this.clock = clock;
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  static String displayName(String profession, String firstName, String lastName) {
    String base = firstName.trim() + " " + lastName.trim();
    return "psychiatrist".equals(profession) ? "Dr " + base : base;
  }

  static String defaultPreferences(String profession) {
    return switch (profession) {
      case "coach" -> PREFS_COACH;
      case "psychiatrist" -> PREFS_PSYCHIATRIST;
      default -> "{}";
    };
  }

  @Transactional
  public PractitionerRow signup(
      String email, String password, String firstName, String lastName, String profession) {
    String normalized = normalizeEmail(email);
    List<String> violations = PasswordPolicy.violations(password, normalized, firstName, lastName);
    if (!violations.isEmpty()) {
      throw new PasswordPolicyException(violations);
    }
    if (practitioners.findByEmailNormalized(normalized).isPresent()) {
      throw new DuplicateEmailException();
    }
    Instant now = clock.instant();
    UUID tenantId = UUID.randomUUID();
    String display = displayName(profession, firstName, lastName);
    PractitionerRow row =
        new PractitionerRow(
            UUID.randomUUID(),
            tenantId,
            email.trim(),
            encoder.encode(password),
            firstName.trim(),
            lastName.trim(),
            display,
            "fr",
            profession,
            "practitioner",
            "email_pending",
            defaultPreferences(profession));
    try {
      practitioners.insertTenant(tenantId, display, now);
      practitioners.insertPractitioner(row, normalized, now);
    } catch (DuplicateKeyException e) {
      throw new DuplicateEmailException();
    }
    return row;
  }

  CurrentPractitioner toCurrent(PractitionerRow r) {
    return new CurrentPractitioner(
        r.id(), r.tenantId(), r.email(), r.displayName(), r.profession(), r.verificationStatus());
  }

  void recordLogin(UUID id) {
    practitioners.touchLastLogin(id, clock.instant());
  }

  /** Toujours silencieux : ne révèle jamais l'existence du compte. */
  @Transactional
  public void requestPasswordReset(String email) {
    Optional<PractitionerRow> found = practitioners.findByEmailNormalized(normalizeEmail(email));
    if (found.isEmpty()) {
      return;
    }
    PractitionerRow p = found.get();
    Instant now = clock.instant();
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    tokens.consumeActive(p.id(), KIND_PASSWORD_RESET, now);
    tokens.insert(p.id(), KIND_PASSWORD_RESET, sha256(token), now.plus(RESET_TTL), now);
    Locale locale = Locale.forLanguageTag(p.locale());
    // Envoi après commit et hors de la requête : le temps de réponse ne dépend pas du SMTP, donc
    // ne révèle pas l'existence du compte ; un échec d'envoi laisse la réponse à 202.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            mailExecutor.execute(
                () -> {
                  try {
                    mailer.sendPasswordReset(p.email(), token, locale);
                  } catch (RuntimeException e) {
                    LOG.error("password reset e-mail could not be sent", e);
                  }
                });
          }
        });
  }

  /** Le jeton n'est consommé que si la politique est respectée (rollback sinon). */
  @Transactional
  public void confirmPasswordReset(String token, String password) {
    Instant now = clock.instant();
    UUID id =
        tokens
            .consume(KIND_PASSWORD_RESET, sha256(token), now)
            .orElseThrow(InvalidTokenException::new);
    PractitionerRow p = practitioners.findById(id).orElseThrow(InvalidTokenException::new);
    List<String> violations =
        PasswordPolicy.violations(password, p.email(), p.firstName(), p.lastName());
    if (!violations.isEmpty()) {
      throw new PasswordPolicyException(violations);
    }
    practitioners.updatePassword(id, encoder.encode(password), now);
    practitioners.deleteSessions(id);
  }

  static String sha256(String value) {
    try {
      byte[] d =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
