package com.psymate.identity.auth;

import com.psymate.identity.auth.TotpRepository.TotpState;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Vérifie un code à usage unique : TOTP (6 chiffres) ou code de secours (XXXXX-XXXXX). Un code TOTP
 * déjà accepté pour un pas donné ne l'est plus (rejeu refusé) ; le dernier pas accepté est gardé en
 * mémoire (limite mono-instance). Un code de secours est consommé par mise à jour atomique de
 * used_at.
 */
@Component
class OtpVerifier {

  enum Kind {
    NONE,
    TOTP,
    RECOVERY
  }

  static final int RECOVERY_CODES = 10;
  private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private final TotpRepository totp;
  private final TotpSecretCipher cipher;
  private final Clock clock;
  private final Map<UUID, Long> lastStep = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  OtpVerifier(TotpRepository totp, TotpSecretCipher cipher, Clock clock) {
    this.totp = totp;
    this.cipher = cipher;
    this.clock = clock;
  }

  /** Contrôle sans rien consommer (activation : le code vient d'être scanné). */
  boolean matchesTotp(UUID id, TotpState state, String code) {
    if (state.secret() == null || code == null) {
      return false;
    }
    byte[] secret = cipher.decrypt(state.secret(), id);
    return Totp.match(secret, code.trim(), clock.instant().getEpochSecond()) >= 0;
  }

  /** Contrôle et consomme : un pas TOTP déjà accepté ou un code de secours déjà utilisé échoue. */
  Kind consume(UUID id, TotpState state, String input) {
    if (!state.enabled() || input == null || input.isBlank()) {
      return Kind.NONE;
    }
    String code = input.trim();
    if (code.matches("[0-9]{6}")) {
      byte[] secret = cipher.decrypt(state.secret(), id);
      long step = Totp.match(secret, code, clock.instant().getEpochSecond());
      if (step < 0) {
        return Kind.NONE;
      }
      boolean[] fresh = {false};
      lastStep.compute(
          id,
          (k, old) -> {
            if (old == null || step > old) {
              fresh[0] = true;
              return step;
            }
            return old;
          });
      return fresh[0] ? Kind.TOTP : Kind.NONE;
    }
    String hash = AuthService.sha256(normalizeRecovery(input));
    return totp.consumeRecoveryCode(id, hash, clock.instant()) ? Kind.RECOVERY : Kind.NONE;
  }

  /** 10 nouveaux codes de secours en clair (renvoyés une seule fois) ; seuls les hachés restent. */
  List<String> issueRecoveryCodes(UUID id) {
    var now = clock.instant();
    totp.retireRecoveryCodes(id, now);
    List<String> codes = new ArrayList<>(RECOVERY_CODES);
    for (int i = 0; i < RECOVERY_CODES; i++) {
      StringBuilder sb = new StringBuilder(11);
      for (int j = 0; j < 10; j++) {
        if (j == 5) {
          sb.append('-');
        }
        sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
      }
      String code = sb.toString();
      totp.insertRecoveryCode(id, AuthService.sha256(normalizeRecovery(code)), now);
      codes.add(code);
    }
    return codes;
  }

  static String normalizeRecovery(String input) {
    return input.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
  }

  void forget(UUID id) {
    lastStep.remove(id);
  }
}
