package com.psymate.identity.auth.service;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerRegistered;
import com.psymate.identity.auth.dto.Address;
import com.psymate.identity.auth.dto.RegisterRequest;
import com.psymate.identity.auth.model.AccountLockedException;
import com.psymate.identity.auth.model.AccountState;
import com.psymate.identity.auth.model.FieldValidationException;
import com.psymate.identity.auth.model.InvalidCredentialsException;
import com.psymate.identity.auth.model.InvalidSecondFactorException;
import com.psymate.identity.auth.model.InvalidTokenException;
import com.psymate.identity.auth.model.PasswordPolicy;
import com.psymate.identity.auth.model.PasswordPolicyException;
import com.psymate.identity.auth.model.PasswordUnchangedException;
import com.psymate.identity.auth.model.PhoneNumbers;
import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.identity.auth.repository.PractitionerRepository.Contact;
import com.psymate.identity.model.AuthToken;
import com.psymate.identity.repository.AuthTokenJpaRepository;
import com.psymate.identity.repository.AuthTokenNativeQueries.ConsumedToken;
import com.psymate.identity.repository.UniqueViolations;
import com.psymate.shared.time.Timestamps;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
  static final String KIND_PASSWORD_RESET = "password_reset";
  static final String KIND_EMAIL_CONFIRM = "email_confirm";
  static final String KIND_EMAIL_CHANGE = "email_change";

  /** Statuts qui peuvent changer d'e-mail avec une session (hors email_pending, refusé, clos). */
  static final Set<String> EMAIL_CHANGE_STATUSES =
      Set.of("docs_pending", "under_review", "needs_more", "approved");

  static final Duration RESET_TTL = Duration.ofMinutes(30);
  static final Duration CONFIRM_TTL = Duration.ofHours(24);
  static final Duration RESEND_MIN_INTERVAL = Duration.ofMinutes(1);
  public static final int RESEND_MAX_PER_DAY = 5;

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<Map<String, Object>> PREFS_TYPE = new TypeReference<>() {};
  private static final String PREFS_COACH =
      "{\"vocabulary.subjectLabel\":\"client\",\"entryTypes.medicationVisible\":false}";
  private static final String PREFS_PSYCHIATRIST = "{\"entryTypes.medicationVisible\":true}";

  private final PractitionerRepository practitioners;
  private final AuthTokenJpaRepository tokens;
  private final PasswordEncoder encoder;
  private final AuthMailer mailer;
  private final TaskExecutor mailExecutor;
  private final Clock clock;
  private final DomainEventPublisher events;
  private final LoginAttemptLimiter limiter;
  private final String dummyHash;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();

  AuthService(
      PractitionerRepository practitioners,
      AuthTokenJpaRepository tokens,
      PasswordEncoder encoder,
      AuthMailer mailer,
      TaskExecutor mailExecutor,
      Clock clock,
      DomainEventPublisher events,
      LoginAttemptLimiter limiter,
      PlatformTransactionManager txManager) {
    this.practitioners = practitioners;
    this.tokens = tokens;
    this.encoder = encoder;
    this.mailer = mailer;
    this.mailExecutor = mailExecutor;
    this.clock = clock;
    this.events = events;
    this.limiter = limiter;
    this.dummyHash = encoder.encode("psymate-timing-equalizer");
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
  public void register(RegisterRequest r, Locale locale, String ip) {
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
            JSON.readValue(defaultPreferences(r.profession()), PREFS_TYPE));
    String token = newToken();
    try {
      tx.executeWithoutResult(
          status -> {
            practitioners.insertTenant(tenantId, display, now);
            practitioners.insertPractitioner(row, normalized, contact, now);
            issueConfirmationToken(row.id(), token, now);
            // Preuve des consentements : aucun ALTER TABLE, journal append-only.
            practitioners.insertSecurityEvent(
                row.id(), "consent_given", ip, consentDetails(now), now);
            events.publish(new PractitionerRegistered(tenantId, row.id(), row.profession()));
          });
    } catch (DataIntegrityViolationException e) {
      if (!UniqueViolations.isUniqueViolation(e)) {
        throw e;
      }
      // Inscription concurrente avec la même adresse : même issue silencieuse.
      sendAsync("account-exists e-mail", () -> mailer.sendAccountExists(row.email(), mailLocale));
      return;
    }
    sendAsync(
        "confirmation e-mail", () -> mailer.sendEmailConfirmation(row.email(), token, mailLocale));
  }

  public static final String CONSENT_VERSION = "cgu-privacy-2026-10";

  static String consentDetails(Instant now) {
    return "{\"terms\":true,\"privacy\":true,\"version\":\""
        + CONSENT_VERSION
        + "\",\"acceptedAt\":\""
        + now
        + "\"}";
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
      Address a = r.address();
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

  public CurrentPractitioner toCurrent(PractitionerRow r) {
    return new CurrentPractitioner(
        r.id(), r.tenantId(), r.email(), r.displayName(), r.profession(), r.verificationStatus());
  }

  /**
   * État de sécurité à jour du compte (GET /api/auth/me, M10-03) : relu en base par une seule
   * requête de projection, car la 2FA et l'obligation de changement de mot de passe évoluent
   * pendant la session. Ni le haché du mot de passe ni le secret TOTP ne sont lus. Le rôle est relu
   * lui aussi, alors que les autorités de la session sont figées à la connexion.
   */
  public AccountState accountState(UUID id) {
    return practitioners.accountState(id).orElseThrow(InvalidCredentialsException::new);
  }

  public void recordLogin(UUID id) {
    practitioners.touchLastLogin(id, clock.instant());
  }

  /** Révoque les sessions autres que la courante et le consigne (session_revoked). */
  public int revokeOtherSessions(UUID id, String currentSessionId, String ip) {
    int n = practitioners.deleteOtherSessions(id, currentSessionId);
    practitioners.insertSecurityEvent(
        id, "session_revoked", ip, "{\"scope\":\"others\",\"count\":" + n + "}", clock.instant());
    return n;
  }

  public Optional<PractitionerRow> find(UUID id) {
    return practitioners.findById(id);
  }

  // --- Confirmation d'e-mail (fiche 25) ---

  /** Résultat de la consommation d'un jeton reçu par e-mail (POST /api/auth/email/confirm). */
  public sealed interface Confirmation {
    /**
     * Première confirmation d'un compte (email_pending vers docs_pending) : une session s'ouvre.
     */
    record AccountConfirmed(PractitionerRow practitioner) implements Confirmation {}

    /** Changement d'e-mail confirmé : {@code practitioner} porte déjà la nouvelle adresse. */
    record EmailChanged(PractitionerRow practitioner, int revokedSessions)
        implements Confirmation {}
  }

  /**
   * Consomme le jeton (usage unique, 24 h). Un jeton d'inscription confirme le compte
   * (docs_pending, email_confirmed_at, journal). Un jeton de changement d'e-mail (M10-04) bascule
   * l'adresse, journalise email_changed, révoque les sessions du compte hors celle qui porte
   * l'appel ({@code sessionOwner} et {@code sessionId}, nuls sans session ou si elle est celle d'un
   * autre compte) et prévient l'ANCIENNE adresse. Inconnu, expiré, consommé : {@link
   * InvalidTokenException}.
   */
  public Confirmation confirm(String token, String ip, UUID sessionOwner, String sessionId) {
    String hash = sha256(token);
    try {
      return tx.execute(status -> confirmInTransaction(hash, ip, sessionOwner, sessionId));
    } catch (DataIntegrityViolationException e) {
      if (!UniqueViolations.isUniqueViolation(e)) {
        throw e;
      }
      // Course : la nouvelle adresse a été prise entre-temps, la transaction est annulée.
      throw new InvalidTokenException();
    }
  }

  private Confirmation confirmInTransaction(
      String hash, String ip, UUID sessionOwner, String sessionId) {
    Instant now = clock.instant();
    Optional<UUID> confirmed = tokens.consume(KIND_EMAIL_CONFIRM, hash, now);
    if (confirmed.isPresent()) {
      UUID id = confirmed.get();
      practitioners.confirmEmail(id, now);
      practitioners.insertSecurityEvent(id, "email_confirmed", ip, null, now);
      return new Confirmation.AccountConfirmed(
          practitioners.findById(id).orElseThrow(InvalidTokenException::new));
    }
    ConsumedToken change =
        tokens
            .consumeWithPayload(KIND_EMAIL_CHANGE, hash, now)
            .orElseThrow(InvalidTokenException::new);
    return applyEmailChange(change, ip, sessionOwner, sessionId, now);
  }

  private Confirmation applyEmailChange(
      ConsumedToken change, String ip, UUID sessionOwner, String sessionId, Instant now) {
    UUID id = change.practitionerId();
    PractitionerRow before = practitioners.findById(id).orElseThrow(InvalidTokenException::new);
    // Compte refusé, suspendu ou clos depuis la demande : le lien ne sert plus (rollback, 410).
    if (!EMAIL_CHANGE_STATUSES.contains(before.verificationStatus())) {
      throw new InvalidTokenException();
    }
    String newEmail = newEmailOf(change.payload());
    String normalized = normalizeEmail(newEmail);
    Optional<PractitionerRow> other = practitioners.findByEmailNormalized(normalized);
    if (other.isPresent() && !other.get().id().equals(id)) {
      throw new InvalidTokenException();
    }
    practitioners.updateEmail(id, newEmail, normalized, now);
    // Un lien de réinitialisation envoyé à l'ancienne adresse ne doit plus servir.
    tokens.consumeActive(id, KIND_PASSWORD_RESET, Timestamps.utc(now));
    practitioners.insertSecurityEvent(
        id,
        "email_changed",
        ip,
        JSON.writeValueAsString(Map.of("previousEmail", before.email(), "newEmail", newEmail)),
        now);
    boolean keepCurrent = id.equals(sessionOwner) && sessionId != null;
    int revoked =
        keepCurrent
            ? practitioners.deleteOtherSessions(id, sessionId)
            : practitioners.deleteSessionsCount(id);
    practitioners.insertSecurityEvent(
        id,
        "session_revoked",
        ip,
        JSON.writeValueAsString(Map.of("scope", keepCurrent ? "others" : "all", "count", revoked)),
        now);
    Locale locale = Locale.forLanguageTag(before.locale());
    String oldAddress = before.email();
    afterCommit(
        "email-changed e-mail", () -> mailer.sendEmailChanged(oldAddress, newEmail, locale));
    return new Confirmation.EmailChanged(
        practitioners.findById(id).orElseThrow(InvalidTokenException::new), revoked);
  }

  private static String newEmailOf(String payload) {
    if (payload == null) {
      throw new InvalidTokenException();
    }
    String value = JSON.readTree(payload).path("newEmail").asString(null);
    if (value == null || value.isBlank()) {
      throw new InvalidTokenException();
    }
    return value;
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
    if (resendLimitReached(p.id(), KIND_EMAIL_CONFIRM, now)) {
      return;
    }
    String token = newToken();
    tokens.consumeActive(p.id(), KIND_EMAIL_CONFIRM, Timestamps.utc(now));
    issueConfirmationToken(p.id(), token, now);
    Locale locale = Locale.forLanguageTag(p.locale());
    afterCommit(
        "confirmation e-mail", () -> mailer.sendEmailConfirmation(p.email(), token, locale));
  }

  /**
   * Corrige l'adresse d'un compte email_pending (public, fiche 25) : sans session, donc le mot de
   * passe est exigé. Toujours silencieux (202) : le mot de passe est vérifié même si le compte est
   * inconnu (Argon2 exécuté), les mots de passe faux comptent dans le verrou e-mail + IP du login
   * (423 une fois le seuil atteint), et les limites de renvoi (1 par minute, 5 par jour)
   * s'appliquent sans le révéler. Si la nouvelle adresse appartient à un autre compte, rien ne
   * change et elle reçoit l'e-mail « compte existant ».
   */
  public void changeEmail(String email, String password, String newEmail, String ip) {
    String normalized = normalizeEmail(email);
    String key = normalized + "|" + (ip == null ? "" : ip);
    limiter
        .remainingLock(key)
        .ifPresent(
            d -> {
              throw new AccountLockedException(Math.max(1, d.toSeconds()));
            });
    Optional<PractitionerRow> found = practitioners.findByEmailNormalized(normalized);
    boolean ok =
        encoder.matches(password, found.map(PractitionerRow::passwordHash).orElse(dummyHash));
    if (found.isEmpty() || !ok) {
      var state = limiter.recordFailure(key);
      if (state.locked(clock.instant())) {
        throw new AccountLockedException(
            Math.max(1, Duration.between(clock.instant(), state.lockedUntil()).toSeconds()));
      }
      return;
    }
    PractitionerRow p = found.get();
    if (!"email_pending".equals(p.verificationStatus())) {
      return;
    }
    changePendingEmail(p, newEmail);
  }

  /**
   * Changement d'e-mail d'un compte connecté (M10-04, G Q49). Le mot de passe est vérifié : un
   * échec donne 422 INVALID_PASSWORD, la session prouvant déjà l'identité de l'appelant, et compte
   * sur une clé par compte (emailchange:{id}, 5 essais puis 423 pendant 15 min quelle que soit
   * l'IP), distincte du verrou e-mail + IP du login. Ensuite la réponse (202) ne dépend jamais de
   * la nouvelle adresse : un jeton de 24 h qui porte l'adresse est toujours créé (les précédents
   * sont invalidés), puis l'adresse reçoit soit le lien de confirmation, soit, si elle appartient
   * déjà à un autre compte, l'e-mail « vous avez déjà un compte ». Dans ce second cas le jeton
   * n'est envoyé à personne : il ne peut pas être consommé, et le travail en base reste identique.
   * L'ancienne adresse reste valide jusqu'à la confirmation. Un compte email_pending suit la
   * correction publique ; les autres statuts (refusé, suspendu, clos) ne changent rien.
   */
  public void changeEmail(UUID id, String password, String newEmail) {
    PractitionerRow p = practitioners.findById(id).orElseThrow(InvalidCredentialsException::new);
    String key = "emailchange:" + id;
    limiter
        .remainingLock(key)
        .ifPresent(
            d -> {
              throw new AccountLockedException(Math.max(1, d.toSeconds()));
            });
    if (!encoder.matches(password, p.passwordHash())) {
      var state = limiter.recordFailure(key);
      if (state.locked(clock.instant())) {
        throw new AccountLockedException(
            Math.max(1, Duration.between(clock.instant(), state.lockedUntil()).toSeconds()));
      }
      throw new InvalidSecondFactorException("password");
    }
    limiter.reset(key);
    if (PractitionerRow.EMAIL_PENDING.equals(p.verificationStatus())) {
      changePendingEmail(p, newEmail);
      return;
    }
    if (!EMAIL_CHANGE_STATUSES.contains(p.verificationStatus())) {
      return;
    }
    tx.executeWithoutResult(status -> requestEmailChange(p, newEmail));
  }

  private void requestEmailChange(PractitionerRow p, String newEmail) {
    UUID id = p.id();
    Instant now = clock.instant();
    if (resendLimitReached(id, KIND_EMAIL_CHANGE, now)) {
      return;
    }
    String address = newEmail.trim();
    Locale locale = Locale.forLanguageTag(p.locale());
    Optional<PractitionerRow> owner = practitioners.findByEmailNormalized(normalizeEmail(address));
    String token = newToken();
    tokens.consumeActive(id, KIND_EMAIL_CHANGE, Timestamps.utc(now));
    tokens.save(
        new AuthToken(
            UUID.randomUUID(),
            id,
            KIND_EMAIL_CHANGE,
            sha256(token),
            Map.of("newEmail", address),
            now.plus(CONFIRM_TTL),
            now));
    if (owner.isEmpty()) {
      afterCommit("email-change e-mail", () -> mailer.sendEmailChange(address, token, locale));
    } else if (!owner.get().id().equals(id)) {
      afterCommit("account-exists e-mail", () -> mailer.sendChangeTargetExists(address, locale));
    }
  }

  private void changePendingEmail(PractitionerRow p, String newEmail) {
    try {
      tx.executeWithoutResult(status -> doChangeEmail(p, newEmail));
    } catch (DataIntegrityViolationException e) {
      if (!UniqueViolations.isUniqueViolation(e)) {
        throw e;
      }
      // Course : l'adresse a été prise entre la lecture et l'écriture (transaction annulée).
      Locale locale = Locale.forLanguageTag(p.locale());
      sendAsync("account-exists e-mail", () -> mailer.sendAccountExists(newEmail.trim(), locale));
    }
  }

  private void doChangeEmail(PractitionerRow p, String newEmail) {
    UUID id = p.id();
    Instant now = clock.instant();
    if (resendLimitReached(id, KIND_EMAIL_CONFIRM, now)) {
      return;
    }
    String normalized = normalizeEmail(newEmail);
    Locale locale = Locale.forLanguageTag(p.locale());
    Optional<PractitionerRow> other = practitioners.findByEmailNormalized(normalized);
    if (other.isPresent() && !other.get().id().equals(id)) {
      afterCommit("account-exists e-mail", () -> mailer.sendAccountExists(newEmail.trim(), locale));
      return;
    }
    practitioners.updateEmail(id, newEmail.trim(), normalized, now);
    String token = newToken();
    tokens.consumeActive(id, KIND_EMAIL_CONFIRM, Timestamps.utc(now));
    issueConfirmationToken(id, token, now);
    afterCommit(
        "confirmation e-mail", () -> mailer.sendEmailConfirmation(newEmail.trim(), token, locale));
  }

  /** Limites de renvoi du lien : 1 par minute, 5 par jour (jetons consommés ou non). */
  private boolean resendLimitReached(UUID id, String kind, Instant now) {
    return tokens.countCreatedSince(id, kind, Timestamps.utc(now.minus(RESEND_MIN_INTERVAL))) > 0
        || tokens.countCreatedSince(id, kind, Timestamps.utc(now.minus(Duration.ofDays(1))))
            >= RESEND_MAX_PER_DAY;
  }

  private void issueConfirmationToken(UUID practitionerId, String token, Instant now) {
    tokens.save(
        new AuthToken(
            UUID.randomUUID(),
            practitionerId,
            KIND_EMAIL_CONFIRM,
            sha256(token),
            now.plus(CONFIRM_TTL),
            now));
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
    tokens.consumeActive(p.id(), KIND_PASSWORD_RESET, Timestamps.utc(now));
    tokens.save(
        new AuthToken(
            UUID.randomUUID(),
            p.id(),
            KIND_PASSWORD_RESET,
            sha256(token),
            now.plus(RESET_TTL),
            now));
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
    // Un mot de passe choisi par l'utilisateur lève aussi l'obligation de changement (M7-08).
    practitioners.clearMustChangePassword(id);
    practitioners.deleteSessions(id);
  }

  /**
   * Contrôles du nouveau mot de passe qui ne dépendent que de la requête (identité, longueur,
   * casse, liste des mots de passe courants, e-mail) : exécutés AVANT la vérification des
   * identifiants pour ne pas consommer un code TOTP ou un code de secours sur un 422. Les contrôles
   * sur le nom (prénom, nom) sont refaits dans changePassword, une fois le compte connu.
   */
  public static void precheckNewPassword(String email, String currentPassword, String newPassword) {
    if (newPassword.equals(currentPassword)) {
      throw new PasswordUnchangedException();
    }
    List<String> violations = PasswordPolicy.violations(newPassword, email, null, null);
    if (!violations.isEmpty()) {
      throw new PasswordPolicyException(violations);
    }
  }

  /**
   * Remplace le mot de passe d'un compte dont les identifiants viennent d'être vérifiés
   * (LoginService.verifyForPasswordChange) : politique de mot de passe, nouveau mot de passe
   * différent de l'actuel, retrait de l'obligation de changement, révocation de toutes les
   * sessions, événement password_changed. Aucun e-mail.
   */
  @Transactional
  public void changePassword(
      PractitionerRow p, String currentPassword, String newPassword, String ip) {
    List<String> violations =
        PasswordPolicy.violations(newPassword, p.email(), p.firstName(), p.lastName());
    if (!violations.isEmpty()) {
      throw new PasswordPolicyException(violations);
    }
    if (newPassword.equals(currentPassword)) {
      throw new PasswordUnchangedException();
    }
    Instant now = clock.instant();
    practitioners.updatePassword(p.id(), encoder.encode(newPassword), now);
    practitioners.clearMustChangePassword(p.id());
    practitioners.deleteSessions(p.id());
    practitioners.insertSecurityEvent(p.id(), "password_changed", ip, null, now);
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

  public static String sha256(String value) {
    try {
      byte[] d =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
