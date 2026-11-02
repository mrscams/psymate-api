package com.psymate.identity.auth.repository;

import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.TotpRecoveryCode;
import com.psymate.identity.persistence.TotpRecoveryCodeJpaRepository;
import com.psymate.shared.time.Timestamps;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Secret TOTP chiffré et codes de secours (totp_recovery_code) d'un praticien. */
@Repository
public class TotpRepository {

  /**
   * secret : chiffré (null = aucune 2FA) ; enabledAt : null tant que l'activation n'est pas
   * vérifiée.
   */
  public record TotpState(byte[] secret, Instant enabledAt) {
    public boolean enabled() {
      return secret != null && enabledAt != null;
    }

    public boolean pending() {
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

  public TotpState find(UUID id) {
    return practitioners
        .findTotp(id)
        .map(v -> new TotpState(v.secret(), Timestamps.instant(v.enabledAt())))
        .orElse(new TotpState(null, null));
  }

  @Transactional
  public void setPendingSecret(UUID id, byte[] encrypted, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.startTotpEnrollment(encrypted, now));
  }

  @Transactional
  public void activate(UUID id, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.activateTotp(now));
  }

  @Transactional
  public void clear(UUID id, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.clearTotp(now));
  }

  /** Les codes de secours restants sont marqués utilisés (rien n'est supprimé). */
  public void retireRecoveryCodes(UUID id, Instant now) {
    recoveryCodes.retireRecoveryCodes(id, Timestamps.utc(now));
  }

  public void insertRecoveryCode(UUID practitionerId, String hash, Instant now) {
    recoveryCodes.save(new TotpRecoveryCode(UUID.randomUUID(), practitionerId, hash, now));
  }

  /** Marque le code utilisé ; vrai une seule fois (mise à jour atomique). */
  public boolean consumeRecoveryCode(UUID practitionerId, String hash, Instant now) {
    return recoveryCodes.consumeRecoveryCode(practitionerId, hash, Timestamps.utc(now)) == 1;
  }
}
