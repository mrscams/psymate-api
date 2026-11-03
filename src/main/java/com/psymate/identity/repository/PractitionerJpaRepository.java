package com.psymate.identity.repository;

import com.psymate.identity.model.Practitioner;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès JPA à {@code practitioner}. Les compteurs atomiques sont des UPDATE JPQL conditionnels
 * (jamais une lecture puis une écriture d'entité) ; le verrou de ligne passe par {@link
 * #findForUpdate}. Les suppressions de sessions (Spring Session) et l'opérateur jsonb {@code -}
 * sont dans les fragments {@link SessionNativeQueries} et {@link PractitionerNativeQueries}.
 */
public interface PractitionerJpaRepository
    extends JpaRepository<Practitioner, UUID>,
        SessionNativeQueries,
        PractitionerNativeQueries,
        AdminVerificationNativeQueries,
        AdminPractitionerNativeQueries {

  /**
   * Compte vu par l'administration des dossiers, lu comme projection (l'entité n'entre pas dans le
   * contexte de persistance : un verrou pris ensuite par {@link #findForUpdate} relit la ligne).
   */
  record AdminAccount(
      UUID id,
      UUID tenantId,
      String email,
      String firstName,
      String lastName,
      String displayName,
      String profession,
      String locale,
      String role,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime emailConfirmedAt,
      OffsetDateTime verifiedAt,
      String phone,
      String professionalNumber,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly) {}

  @Query(
      "select new com.psymate.identity.repository.PractitionerJpaRepository$AdminAccount("
          + "p.id, p.tenantId, p.email, p.firstName, p.lastName, p.displayName, p.profession,"
          + " p.locale, p.role, p.verificationStatus, p.createdAt, p.emailConfirmedAt,"
          + " p.verifiedAt, p.phone, p.professionalNumber, p.addressStreet, p.addressPostalCode,"
          + " p.addressCity, p.remoteOnly) from Practitioner p where p.id = :id")
  Optional<AdminAccount> adminAccountView(@Param("id") UUID id);

  /** Vue de la façade identity.api : cabinet, profession et statut, sans charger le compte. */
  record Summary(UUID id, UUID tenantId, String profession, String verificationStatus) {}

  /** Secret TOTP chiffré (null = aucune 2FA) et date d'activation (null = en attente). */
  record TotpView(byte[] secret, OffsetDateTime enabledAt) {}

  /**
   * État de sécurité lu pour GET /api/auth/me : rôle, préférences (drapeau de changement de mot de
   * passe) et 2FA active, calculée en base. Ni le haché du mot de passe ni le secret TOTP chiffré
   * ne sont sélectionnés.
   */
  record AccountSecurity(String role, boolean twoFactorEnabled, Map<String, Object> preferences) {}

  /** Destinataire des notifications d'administration : adresse et langue. */
  record Recipient(String email, String locale) {}

  /** Administrateurs dont le compte est actif (notifications de soumission de dossier). */
  @Query(
      "select new com.psymate.identity.repository.PractitionerJpaRepository$Recipient("
          + "p.email, p.locale) from Practitioner p where p.role = 'admin'"
          + " and p.verificationStatus not in ('rejected','suspended','closed','email_pending')")
  List<Recipient> adminRecipients();

  /** Mêmes destinataires, triés par adresse (ordre stable des rappels d'administration). */
  @Query(
      "select new com.psymate.identity.repository.PractitionerJpaRepository$Recipient("
          + "p.email, p.locale) from Practitioner p where p.role = 'admin'"
          + " and p.verificationStatus not in ('rejected','suspended','closed','email_pending')"
          + " order by p.email")
  List<Recipient> adminRecipientsByEmail();

  Optional<Practitioner> findByEmailNormalized(String emailNormalized);

  boolean existsByEmailNormalized(String emailNormalized);

  /**
   * Charge le compte avec un verrou de ligne : sérialise les écritures concurrentes d'un même
   * compte (PATCH des préférences, dépôt de justificatifs, décisions admin). Ne pas charger
   * l'entité avant cet appel dans la même transaction : l'état déjà chargé ne serait pas relu.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Practitioner p where p.id = :id")
  Optional<Practitioner> findForUpdate(@Param("id") UUID id);

  /**
   * Administrateurs actifs, verrouillés dans un ordre unique (id) : sérialise deux suspensions
   * croisées. Entités et non projection : Hibernate n'applique pas un verrou à une projection.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from Practitioner p where p.role = 'admin' and p.verificationStatus"
          + " not in ('suspended','rejected','closed') order by p.id")
  List<Practitioner> lockActiveAdmins();

  @Query(
      "select new com.psymate.identity.repository.PractitionerJpaRepository$Summary("
          + "p.id, p.tenantId, p.profession, p.verificationStatus) from Practitioner p"
          + " where p.id in :ids")
  List<Summary> findAll(@Param("ids") Collection<UUID> ids);

  @Query(
      "select new com.psymate.identity.repository.PractitionerJpaRepository$TotpView("
          + "p.totpSecretEnc, p.totpEnabledAt) from Practitioner p where p.id = :id")
  Optional<TotpView> findTotp(@Param("id") UUID id);

  /**
   * Un seul SELECT, sans le haché ni le secret TOTP ; la 2FA est active si secret et date existent.
   */
  @Query(
      "select new com.psymate.identity.repository.PractitionerJpaRepository$AccountSecurity("
          + "p.role, case when p.totpSecretEnc is not null and p.totpEnabledAt is not null"
          + " then true else false end, p.preferences) from Practitioner p where p.id = :id")
  Optional<AccountSecurity> accountSecurity(@Param("id") UUID id);

  /** Fin de verrou enregistrée en base (second garde, survit à un redémarrage). */
  @Query("select p.lockedUntil from Practitioner p where p.id = :id")
  Optional<OffsetDateTime> lockedUntil(@Param("id") UUID id);

  @Query("select p.professionalNumber from Practitioner p where p.id = :id")
  Optional<String> professionalNumber(@Param("id") UUID id);

  @Query("select p.email from Practitioner p where p.id = :id")
  Optional<String> emailOf(@Param("id") UUID id);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query("update Practitioner p set p.lastLoginAt = :now where p.id = :id")
  int touchLastLogin(@Param("id") UUID id, @Param("now") OffsetDateTime now);

  /** Reflète le compteur d'échecs et le verrou (audit) ; l'application du verrou est en mémoire. */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update Practitioner p set p.failedLogins = :failures, p.lockedUntil = :lockedUntil"
          + " where p.id = :id")
  int recordFailedLogin(
      @Param("id") UUID id,
      @Param("failures") short failures,
      @Param("lockedUntil") OffsetDateTime lockedUntil);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update Practitioner p set p.failedLogins = 0, p.lockedUntil = null where p.id = :id"
          + " and (p.failedLogins <> 0 or p.lockedUntil is not null)")
  int resetFailedLogins(@Param("id") UUID id);
}
