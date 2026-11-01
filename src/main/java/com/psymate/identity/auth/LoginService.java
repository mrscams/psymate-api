package com.psymate.identity.auth;

import com.psymate.identity.auth.AuthExceptions.AccountLockedException;
import com.psymate.identity.auth.AuthExceptions.AccountNotSuspendedException;
import com.psymate.identity.auth.AuthExceptions.AccountSuspendedException;
import com.psymate.identity.auth.AuthExceptions.EmailNotConfirmedException;
import com.psymate.identity.auth.AuthExceptions.InvalidCredentialsException;
import com.psymate.identity.auth.AuthExceptions.PasswordChangeRequiredException;
import com.psymate.identity.auth.LoginAttemptLimiter.State;
import com.psymate.identity.auth.OtpVerifier.Kind;
import com.psymate.identity.auth.TotpRepository.TotpState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Connexion durcie (F33, fiche 11). Le mot de passe est toujours vérifié (hachage factice si le
 * compte n'existe pas) : e-mail inconnu, mot de passe faux et code faux donnent la même réponse 401
 * en un temps comparable. Statuts et 2FA ne sont révélés qu'après un mot de passe correct.
 */
@Service
class LoginService {

  static final Set<String> SUSPENDED_STATUSES = Set.of("rejected", "suspended", "closed");

  /** Statuts qui donnent droit à un recours (fiche 27) : refus du dossier ou suspension. */
  static final Set<String> APPEALABLE_STATUSES = Set.of("rejected", "suspended");

  /** Préférence posée par le compte admin de bootstrap (M7-08) : mot de passe à changer. */
  static final String MUST_CHANGE_PASSWORD = "security.mustChangePassword";

  /** Résultat d'une tentative qui n'a pas échoué. */
  sealed interface Outcome {
    record Authenticated(PractitionerRow practitioner, boolean recoveryCodeUsed)
        implements Outcome {}

    /** Mot de passe correct, 2FA active et code absent. */
    record OtpRequired() implements Outcome {}
  }

  private final PractitionerRepository practitioners;
  private final TotpRepository totp;
  private final OtpVerifier otp;
  private final LoginAttemptLimiter limiter;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final String dummyHash;

  LoginService(
      PractitionerRepository practitioners,
      TotpRepository totp,
      OtpVerifier otp,
      LoginAttemptLimiter limiter,
      PasswordEncoder encoder,
      Clock clock) {
    this.practitioners = practitioners;
    this.totp = totp;
    this.otp = otp;
    this.limiter = limiter;
    this.encoder = encoder;
    this.clock = clock;
    this.dummyHash = encoder.encode("psymate-timing-equalizer");
  }

  Outcome login(String email, String password, String otpInput, String ip) {
    return authenticate(email, password, otpInput, ip, Mode.LOGIN);
  }

  /**
   * Vérification d'identifiants pour POST /api/auth/password/change : mêmes verrous, même hachage
   * factice, mêmes refus (EMAIL_NOT_CONFIRMED, ACCOUNT_SUSPENDED) et même 2FA que la connexion,
   * mais le drapeau mustChangePassword est ignoré (c'est précisément ce que l'appel lève) et aucune
   * connexion n'est journalisée.
   */
  Outcome verifyForPasswordChange(String email, String password, String otpInput, String ip) {
    return authenticate(email, password, otpInput, ip, Mode.PASSWORD_CHANGE);
  }

  /**
   * Vérification d'identifiants pour POST /api/me/appeal (M1-14b) : mêmes verrous, même hachage
   * factice et même 2FA que la connexion, mais réservée aux comptes refusés ou suspendus (409
   * ACCOUNT_NOT_SUSPENDED sinon). Aucune connexion n'est journalisée, aucune session n'est ouverte.
   */
  Outcome verifyForAppeal(String email, String password, String otpInput, String ip) {
    return authenticate(email, password, otpInput, ip, Mode.APPEAL);
  }

  /** Usage des identifiants : connexion, changement de mot de passe ou recours. */
  private enum Mode {
    LOGIN,
    PASSWORD_CHANGE,
    APPEAL
  }

  private Outcome authenticate(
      String email, String password, String otpInput, String ip, Mode mode) {
    String normalized = AuthService.normalizeEmail(email);
    String key = normalized + "|" + (ip == null ? "" : ip);

    Optional<Duration> locked = limiter.remainingLock(key);
    if (locked.isPresent()) {
      throw lockedException(locked.get());
    }

    Optional<PractitionerRow> found = practitioners.findByEmailNormalized(normalized);
    if (found.isEmpty()) {
      encoder.matches(password, dummyHash);
      throw failure(key, null, ip, "unknown_account");
    }
    PractitionerRow row = found.get();
    // Second garde : le verrou écrit en base (par e-mail) survit à un redémarrage ou à un reset du
    // limiteur mémoire.
    Optional<Instant> storedLock = practitioners.lockedUntil(row.id());
    if (storedLock.isPresent() && clock.instant().isBefore(storedLock.get())) {
      throw lockedException(Duration.between(clock.instant(), storedLock.get()));
    }
    if (!encoder.matches(password, row.passwordHash())) {
      throw failure(key, row, ip, "bad_password");
    }

    // Mot de passe correct : les états du compte peuvent maintenant être révélés.
    if (EmailConfirmationFilter.PENDING.equals(row.verificationStatus())) {
      throw new EmailNotConfirmedException();
    }
    if (mode == Mode.APPEAL) {
      // Recours (M1-14b) : seuls les comptes refusés ou suspendus peuvent contester ; leurs
      // sessions sont déjà révoquées, la 2FA s'applique comme d'habitude.
      if (!APPEALABLE_STATUSES.contains(row.verificationStatus())) {
        throw new AccountNotSuspendedException();
      }
    } else if (SUSPENDED_STATUSES.contains(row.verificationStatus())) {
      revokeSessions(row, ip);
      throw new AccountSuspendedException();
    }
    if (mode == Mode.LOGIN && mustChangePassword(row)) {
      throw new PasswordChangeRequiredException();
    }

    TotpState state = totp.find(row.id());
    boolean recoveryUsed = false;
    if (state.enabled()) {
      if (otpInput == null || otpInput.isBlank()) {
        return new Outcome.OtpRequired();
      }
      Kind kind = otp.consume(row.id(), state, otpInput);
      if (kind == Kind.NONE) {
        throw failure(key, row, ip, "bad_otp");
      }
      recoveryUsed = kind == Kind.RECOVERY;
    }

    Instant now = clock.instant();
    limiter.reset(key);
    practitioners.resetFailedLogins(row.id());
    if (recoveryUsed) {
      practitioners.insertSecurityEvent(row.id(), "recovery_code_used", ip, null, now);
    }
    if (mode == Mode.LOGIN) {
      practitioners.insertSecurityEvent(row.id(), "login_ok", ip, null, now);
    }
    return new Outcome.Authenticated(row, recoveryUsed);
  }

  private static boolean mustChangePassword(PractitionerRow row) {
    return row.preferences() != null
        && row.preferences().get(MUST_CHANGE_PASSWORD) instanceof Boolean flag
        && flag;
  }

  /** Échec : compteur par clé, reflet en base, événements ; 423 si le seuil est atteint. */
  private RuntimeException failure(String key, PractitionerRow row, String ip, String reason) {
    State state = limiter.recordFailure(key);
    Instant now = clock.instant();
    boolean nowLocked = state.locked(now);
    if (row != null) {
      practitioners.recordFailedLogin(row.id(), state.failures(), state.lockedUntil());
      practitioners.insertSecurityEvent(
          row.id(), "login_failed", ip, "{\"reason\":\"" + reason + "\"}", now);
      if (nowLocked) {
        practitioners.insertSecurityEvent(
            row.id(),
            "locked",
            ip,
            "{\"failures\":" + state.failures() + ",\"until\":\"" + state.lockedUntil() + "\"}",
            now);
      }
    }
    return nowLocked
        ? lockedException(Duration.between(now, state.lockedUntil()))
        : new InvalidCredentialsException();
  }

  private static AccountLockedException lockedException(Duration remaining) {
    return new AccountLockedException(Math.max(1, remaining.toSeconds()));
  }

  private void revokeSessions(PractitionerRow row, String ip) {
    int n = practitioners.deleteSessionsCount(row.id());
    practitioners.insertSecurityEvent(
        row.id(),
        "session_revoked",
        ip,
        "{\"reason\":\"" + row.verificationStatus() + "\",\"count\":" + n + "}",
        clock.instant());
  }
}
