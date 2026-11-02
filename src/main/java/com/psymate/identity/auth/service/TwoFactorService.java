package com.psymate.identity.auth.service;

import com.psymate.identity.auth.model.AccountLockedException;
import com.psymate.identity.auth.model.InvalidCredentialsException;
import com.psymate.identity.auth.model.InvalidSecondFactorException;
import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.auth.model.Totp;
import com.psymate.identity.auth.model.TwoFactorStateException;
import com.psymate.identity.auth.repository.PractitionerRepository;
import com.psymate.identity.auth.repository.TotpRepository;
import com.psymate.identity.auth.repository.TotpRepository.TotpState;
import com.psymate.identity.auth.service.OtpVerifier.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activation, désactivation et codes de secours de la double authentification (UC-19). */
@Service
public class TwoFactorService {

  static final String ISSUER = "PsyMate";
  static final String ALREADY_ENABLED = "TWO_FACTOR_ALREADY_ENABLED";
  static final String NOT_ENABLED = "TWO_FACTOR_NOT_ENABLED";
  static final String NOT_STARTED = "TWO_FACTOR_NOT_STARTED";
  static final String ADMIN_REQUIRED = "TWO_FACTOR_REQUIRED_FOR_ADMIN";

  /** Secret en Base32 et URI otpauth:// du QR code, montrés une seule fois. */
  public record Enrollment(String secret, String otpauthUri) {}

  private final PractitionerRepository practitioners;
  private final TotpRepository totp;
  private final TotpSecretCipher cipher;
  private final OtpVerifier otp;
  private final LoginAttemptLimiter limiter;
  private final PasswordEncoder encoder;
  private final Clock clock;

  TwoFactorService(
      PractitionerRepository practitioners,
      TotpRepository totp,
      TotpSecretCipher cipher,
      OtpVerifier otp,
      LoginAttemptLimiter limiter,
      PasswordEncoder encoder,
      Clock clock) {
    this.practitioners = practitioners;
    this.totp = totp;
    this.cipher = cipher;
    this.otp = otp;
    this.limiter = limiter;
    this.encoder = encoder;
    this.clock = clock;
  }

  /** Vrai si la 2FA du praticien est active (garde de /api/admin/**, AdminTwoFactorFilter). */
  public boolean isEnabled(UUID id) {
    return totp.find(id).enabled();
  }

  /** Génère un secret en attente d'activation (rappel possible tant qu'il n'est pas vérifié). */
  @Transactional
  public Enrollment enable(UUID id) {
    PractitionerRow row = practitioners.findById(id).orElseThrow(InvalidCredentialsException::new);
    if (totp.find(id).enabled()) {
      throw new TwoFactorStateException(ALREADY_ENABLED);
    }
    byte[] secret = Totp.newSecret();
    totp.setPendingSecret(id, cipher.encrypt(secret, id), clock.instant());
    return new Enrollment(Totp.base32(secret), Totp.otpauthUri(ISSUER, row.email(), secret));
  }

  /** Vérifie le premier code, active la 2FA et renvoie les 10 codes de secours (une fois). */
  @Transactional
  public List<String> verify(UUID id, String code, String ip) {
    TotpState state = totp.find(id);
    if (state.enabled()) {
      throw new TwoFactorStateException(ALREADY_ENABLED);
    }
    if (!state.pending()) {
      throw new TwoFactorStateException(NOT_STARTED);
    }
    String key = "2fa:" + id;
    guardLock(key);
    if (!otp.consumeTotp(id, state, code)) {
      failure(key);
      throw new InvalidSecondFactorException("code");
    }
    limiter.reset(key);
    Instant now = clock.instant();
    totp.activate(id, now);
    List<String> codes = otp.issueRecoveryCodes(id);
    practitioners.insertSecurityEvent(id, "2fa_enabled", ip, null, now);
    return codes;
  }

  /** Désactive la 2FA : mot de passe et code (TOTP ou secours) exigés. */
  @Transactional
  public void disable(UUID id, String password, String code, String ip) {
    PractitionerRow row = practitioners.findById(id).orElseThrow(InvalidCredentialsException::new);
    // Un administrateur ne peut pas désactiver sa 2FA (M1-14) ; refus avant tout contrôle de code.
    if ("admin".equals(row.role())) {
      throw new TwoFactorStateException(ADMIN_REQUIRED);
    }
    TotpState state = totp.find(id);
    if (!state.enabled()) {
      throw new TwoFactorStateException(NOT_ENABLED);
    }
    String key = "2fa:" + id;
    guardLock(key);
    if (!encoder.matches(password, row.passwordHash())) {
      failure(key);
      throw new InvalidSecondFactorException("password");
    }
    if (otp.consume(id, state, code) == Kind.NONE) {
      failure(key);
      throw new InvalidSecondFactorException("code");
    }
    limiter.reset(key);
    Instant now = clock.instant();
    totp.clear(id, now);
    totp.retireRecoveryCodes(id, now);
    otp.forget(id);
    practitioners.insertSecurityEvent(id, "2fa_disabled", ip, null, now);
  }

  /** Remplace les codes de secours par 10 nouveaux ; les anciens ne fonctionnent plus. */
  @Transactional
  public List<String> regenerateRecoveryCodes(UUID id, String ip) {
    if (!totp.find(id).enabled()) {
      throw new TwoFactorStateException(NOT_ENABLED);
    }
    List<String> codes = otp.issueRecoveryCodes(id);
    practitioners.insertSecurityEvent(id, "recovery_codes_regenerated", ip, null, clock.instant());
    return codes;
  }

  private void guardLock(String key) {
    limiter
        .remainingLock(key)
        .ifPresent(
            d -> {
              throw new AccountLockedException(Math.max(1, d.toSeconds()));
            });
  }

  /** Même seuil que la connexion : 5 codes faux verrouillent les essais 15 minutes. */
  private void failure(String key) {
    var state = limiter.recordFailure(key);
    if (state.locked(clock.instant())) {
      throw new AccountLockedException(
          Math.max(1, Duration.between(clock.instant(), state.lockedUntil()).toSeconds()));
    }
  }
}
