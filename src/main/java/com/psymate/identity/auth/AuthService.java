package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerRegistered;
import com.psymate.identity.auth.AuthDtos.RegisterRequest;
import com.psymate.identity.auth.AuthExceptions.EmailAlreadyConfirmedException;
import com.psymate.identity.auth.AuthExceptions.FieldValidationException;
import com.psymate.identity.auth.AuthExceptions.InvalidTokenException;
import com.psymate.identity.auth.AuthExceptions.PasswordPolicyException;
import com.psymate.identity.auth.AuthExceptions.TooManyRequestsException;
import com.psymate.identity.auth.PractitionerRepository.Contact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
  static final String KIND_PASSWORD_RESET = "password_reset";
  static final String KIND_EMAIL_CONFIRM = "email_confirm";
  static final Duration RESET_TTL = Duration.ofMinutes(30);
  static final Duration CONFIRM_TTL = Duration.ofHours(24);
  static final Duration RESEND_MIN_INTERVAL = Duration.ofMinutes(1);
  static final int RESEND_MAX_PER_DAY = 5;

  private static final String PREFS_COACH =
      "{\"vocabulary.subjectLabel\":\"client\",\"entryTypes.medicationVisible\":false}";
  private static final String PREFS_PSYCHIATRIST = "{\"entryTypes.medicationVisible\":true}";

  private final PractitionerRepository practitioners;
  private final AuthTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final AuthMailer mailer;
  private final TaskExecutor mailExecutor;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();

  AuthService(
      PractitionerRepository practitioners,
      AuthTokenRepository tokens,
      PasswordEncoder encoder,
      AuthMailer mailer,
      TaskExecutor mailExecutor,
      Clock clock,
      ApplicationEventPublisher events,
      PlatformTransactionManager txManager) {
    this.practitioners = practitioners;
    this.tokens = tokens;
    this.encoder = encoder;
    this.mailer = mailer;
    this.mailExecutor = mailExecutor;
    this.clock = clock;
    this.events = events;
    this.tx = new TransactionTemplate(txManager);
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

  /**
   * Inscription ouverte (G Q21). La réponse est la même que l'adresse existe déjà ou non : les
   * contrôles de validité passent avant, le mot de passe est haché dans les deux cas (temps
   * comparable) et les e-mails partent hors de la requête.
   */
  public void register(RegisterRequest r, Locale locale) {
    String normalized = normalizeEmail(r.email());
    List<String> violations =
        PasswordPolicy.violations(r.password(), normalized, r.firstName(), r.lastName());
    if (!violations.isEmpty()) {
      throw new PasswordPolicyException(violations);
    }
    Contact contact = contactOf(r);
    Locale mailLocale = AuthMessages.normalize(locale);
    // Toujours haché, même si le compte existe : le coût dominant est identique dans les deux cas.
    String hash = encoder.encode(r.password());

    if (practitioners.findByEmailNormalized(normalized).isPresent()) {
      sendAsync(
          "account-exists e-mail", () -> mailer.sendAccountExists(r.email().trim(), mailLocale));
      return;
    }
    Instant now = clock.instant();
    UUID tenantId = UUID.randomUUID();
    String display = displayName(r.profession(), r.firstName(), r.lastName());
    PractitionerRow row =
        new PractitionerRow(
            UUID.randomUUID(),
            tenantId,
            r.email().trim(),
            hash,
            r.firstName().trim(),
            r.lastName().trim(),
            display,
            mailLocale.getLanguage(),
            r.profession(),
            "practitioner",
            "email_pending",
            defaultPreferences(r.profession()));
    String token = newToken();
    try {
      tx.executeWithoutResult(
          status -> {
            practitioners.insertTenant(tenantId, display, now);
            practitioners.insertPractitioner(row, normalized, contact, now);
            issueConfirmationToken(row.id(), token, now);
            events.publishEvent(
                new PractitionerRegistered(
                    UUID.randomUUID(), now, row.id(), tenantId, row.profession()));
          });
    } catch (DuplicateKeyException e) {
      // Inscription concurrente avec la même adresse : même issue silencieuse.
      sendAsync("account-exists e-mail", () -> mailer.sendAccountExists(row.email(), mailLocale));
      return;
    }
    sendAsync(
        "confirmation e-mail", () -> mailer.sendEmailConfirmation(row.email(), token, mailLocale));
  }

  /** Téléphone, adresse (sauf visio) et numéro professionnel contrôlés ; erreurs par champ. */
  private static Contact contactOf(RegisterRequest r) {
    List<String[]> errors = new ArrayList<>();
    Optional<String> phone = PhoneNumbers.normalize(r.phone());
    if (phone.isEmpty()) {
      errors.add(new String[] {"phone", "PHONE_INVALID", "error.phone.invalid"});
    }
    boolean remoteOnly = Boolean.TRUE.equals(r.remoteOnly());
    String street = null;
    String postal = null;
    String city = null;
    if (!remoteOnly) {
      AuthDtos.Address a = r.address();
      if (a == null) {
        errors.add(new String[] {"address", "ADDRESS_REQUIRED", "error.address.required"});
      } else {
        if (blank(a.street())) {
          errors.add(new String[] {"address.street", "REQUIRED", "error.street.required"});
        }
        if (a.postalCode() == null || !a.postalCode().trim().matches("\\d{5}")) {
          errors.add(
              new String[] {
                "address.postalCode", "POSTAL_CODE_INVALID", "error.postalCode.invalid"
              });
        }
        if (blank(a.city())) {
          errors.add(new String[] {"address.city", "REQUIRED", "error.city.required"});
        }
        if (errors.isEmpty()) {
          street = a.street().trim();
          postal = a.postalCode().trim();
          city = a.city().trim();
        }
      }
    }
    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }
    String pro = blank(r.professionalNumber()) ? null : r.professionalNumber().trim();
    return new Contact(phone.orElseThrow(), pro, street, postal, city, remoteOnly);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  CurrentPractitioner toCurrent(PractitionerRow r) {
    return new CurrentPractitioner(
        r.id(), r.tenantId(), r.email(), r.displayName(), r.profession(), r.verificationStatus());
  }

  void recordLogin(UUID id) {
    practitioners.touchLastLogin(id, clock.instant());
  }

  Optional<PractitionerRow> find(UUID id) {
    return practitioners.findById(id);
  }

  // --- Confirmation d'e-mail (fiche 25) ---

  /** Consomme le jeton (usage unique, 24 h) : docs_pending, email_confirmed_at, journal. */
  @Transactional
  public PractitionerRow confirmEmail(String token, String ip) {
    Instant now = clock.instant();
    UUID id =
        tokens
            .consume(KIND_EMAIL_CONFIRM, sha256(token), now)
            .orElseThrow(InvalidTokenException::new);
    practitioners.confirmEmail(id, now);
    practitioners.insertSecurityEvent(id, "email_confirmed", ip, now);
    return practitioners.findById(id).orElseThrow(InvalidTokenException::new);
  }

  /**
   * Toujours silencieux (202) : un compte inconnu, déjà confirmé ou au-delà des limites (1 par
   * minute, 5 par jour) ne reçoit rien et ne se distingue pas d'un envoi réussi. Chaque renvoi
   * invalide le jeton précédent.
   */
  @Transactional
  public void resendConfirmation(String email) {
    Optional<PractitionerRow> found = practitioners.findByEmailNormalized(normalizeEmail(email));
    if (found.isEmpty() || !"email_pending".equals(found.get().verificationStatus())) {
      return;
    }
    PractitionerRow p = found.get();
    Instant now = clock.instant();
    if (tokens.countCreatedSince(p.id(), KIND_EMAIL_CONFIRM, now.minus(RESEND_MIN_INTERVAL)) > 0
        || tokens.countCreatedSince(p.id(), KIND_EMAIL_CONFIRM, now.minus(Duration.ofDays(1)))
            >= RESEND_MAX_PER_DAY) {
      return;
    }
    String token = newToken();
    tokens.consumeActive(p.id(), KIND_EMAIL_CONFIRM, now);
    issueConfirmationToken(p.id(), token, now);
    Locale locale = Locale.forLanguageTag(p.locale());
    afterCommit(
        "confirmation e-mail", () -> mailer.sendEmailConfirmation(p.email(), token, locale));
  }

  /**
   * Corrige l'adresse d'un compte email_pending : nouveau jeton, ancien invalidé. Si l'adresse
   * appartient à un autre compte, rien ne change et elle reçoit l'e-mail « compte existant ».
   * Retourne le praticien à jour.
   */
  @Transactional
  public PractitionerRow changeEmail(UUID id, String newEmail) {
    PractitionerRow p = practitioners.findById(id).orElseThrow(InvalidTokenException::new);
    if (!"email_pending".equals(p.verificationStatus())) {
      throw new EmailAlreadyConfirmedException();
    }
    Instant now = clock.instant();
    if (tokens.countCreatedSince(id, KIND_EMAIL_CONFIRM, now.minus(Duration.ofDays(1)))
        >= RESEND_MAX_PER_DAY) {
      throw new TooManyRequestsException();
    }
    String normalized = normalizeEmail(newEmail);
    Locale locale = Locale.forLanguageTag(p.locale());
    Optional<PractitionerRow> other = practitioners.findByEmailNormalized(normalized);
    if (other.isPresent() && !other.get().id().equals(id)) {
      afterCommit("account-exists e-mail", () -> mailer.sendAccountExists(newEmail.trim(), locale));
      return p;
    }
    practitioners.updateEmail(id, newEmail.trim(), normalized, now);
    String token = newToken();
    tokens.consumeActive(id, KIND_EMAIL_CONFIRM, now);
    issueConfirmationToken(id, token, now);
    afterCommit(
        "confirmation e-mail", () -> mailer.sendEmailConfirmation(newEmail.trim(), token, locale));
    return practitioners.findById(id).orElse(p);
  }

  private void issueConfirmationToken(UUID practitionerId, String token, Instant now) {
    tokens.insert(practitionerId, KIND_EMAIL_CONFIRM, sha256(token), now.plus(CONFIRM_TTL), now);
  }

  private String newToken() {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  // --- Réinitialisation du mot de passe ---

  /** Toujours silencieux : ne révèle jamais l'existence du compte. */
  @Transactional
  public void requestPasswordReset(String email) {
    Optional<PractitionerRow> found = practitioners.findByEmailNormalized(normalizeEmail(email));
    if (found.isEmpty()) {
      return;
    }
    PractitionerRow p = found.get();
    Instant now = clock.instant();
    String token = newToken();
    tokens.consumeActive(p.id(), KIND_PASSWORD_RESET, now);
    tokens.insert(p.id(), KIND_PASSWORD_RESET, sha256(token), now.plus(RESET_TTL), now);
    Locale locale = Locale.forLanguageTag(p.locale());
    afterCommit("password reset e-mail", () -> mailer.sendPasswordReset(p.email(), token, locale));
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

  // --- Envoi d'e-mails ---

  /**
   * Envoi après commit et hors de la requête : le temps de réponse ne dépend pas du SMTP, donc ne
   * révèle pas l'existence du compte ; un échec d'envoi n'altère pas la réponse.
   */
  private void afterCommit(String what, Runnable send) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            sendAsync(what, send);
          }
        });
  }

  private void sendAsync(String what, Runnable send) {
    mailExecutor.execute(
        () -> {
          try {
            send.run();
          } catch (RuntimeException e) {
            LOG.error("{} could not be sent", what, e);
          }
        });
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
