package com.psymate.identity.repository;

import com.psymate.identity.model.TotpRecoveryCode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Accès JPA à {@code totp_recovery_code}. */
public interface TotpRecoveryCodeJpaRepository extends JpaRepository<TotpRecoveryCode, UUID> {

  /** Les codes de secours restants sont marqués utilisés (rien n'est supprimé). */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update TotpRecoveryCode c set c.usedAt = :now where c.practitionerId = :p"
          + " and c.usedAt is null")
  int retireRecoveryCodes(@Param("p") UUID practitionerId, @Param("now") OffsetDateTime now);

  /** Marque le code utilisé : 1 une seule fois (mise à jour atomique), 0 ensuite. */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update TotpRecoveryCode c set c.usedAt = :now where c.practitionerId = :p"
          + " and c.codeHash = :h and c.usedAt is null")
  int consumeRecoveryCode(
      @Param("p") UUID practitionerId,
      @Param("h") String codeHash,
      @Param("now") OffsetDateTime now);
}
