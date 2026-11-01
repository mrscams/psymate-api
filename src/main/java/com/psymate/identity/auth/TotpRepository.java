package com.psymate.identity.auth;

import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.Times;
import com.psymate.identity.persistence.TotpRecoveryCode;
import com.psymate.identity.persistence.TotpRecoveryCodeJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Secret TOTP chiffré et codes de secours (totp_recovery_code) d'un praticien. */
@Repository
class TotpRepository {

  /**
   * secret : chiffré (null = aucune 2FA) ; enabledAt : null tant que l'activation n'est pas
   * vérifiée.
   */
  record TotpState(byte[] secret, Instant enabledAt) {
    boolean enabled() {
      return secret != null && enabledAt != null;
    }

    boolean pending() {
      return secret != null && enabledAt == null;
    }
  }

  private final PractitionerJpaRepository practitioners;
  private final TotpRecoveryCodeJpaRepository recoveryCodes;

  TotpRepository(
      PractitionerJpaRepository practitioners, TotpRecoveryCodeJpaRepository recoveryCodes) {
    this.practitioners = practitioners;
    this.recoveryCodes = recoveryCodes;
  }

  TotpState find(UUID id) {
    return practitioners
        .findTotp(id)
        .map(v -> new TotpState(v.secret(), Times.instant(v.enabledAt())))
        .orElse(new TotpState(null, null));
  }

  @Transactional
  void setPendingSecret(UUID id, byte[] encrypted, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.startTotpEnrollment(encrypted, now));
  }

  @Transactional
  void activate(UUID id, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.activateTotp(now));
  }

  @Transactional
  void clear(UUID id, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.clearTotp(now));
  }

  /** Les codes de secours restants sont marqués utilisés (rien n'est supprimé). */
  void retireRecoveryCodes(UUID id, Instant now) {
    recoveryCodes.retireRecoveryCodes(id, Times.utc(now));
  }

  void insertRecoveryCode(UUID practitionerId, String hash, Instant now) {
    recoveryCodes.save(new TotpRecoveryCode(UUID.randomUUID(), practitionerId, hash, now));
  }

  /** Marque le code utilisé ; vrai une seule fois (mise à jour atomique). */
  boolean consumeRecoveryCode(UUID practitionerId, String hash, Instant now) {
    return recoveryCodes.consumeRecoveryCode(practitionerId, hash, Times.utc(now)) == 1;
  }
}
