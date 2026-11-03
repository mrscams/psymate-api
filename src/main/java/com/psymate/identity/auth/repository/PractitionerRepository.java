package com.psymate.identity.auth.repository;

import com.psymate.identity.auth.model.AccountState;
import com.psymate.identity.auth.model.LastLogin;
import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.model.Practitioner;
import com.psymate.identity.model.SecurityEvent;
import com.psymate.identity.model.Tenant;
import com.psymate.identity.repository.PractitionerJpaRepository;
import com.psymate.identity.repository.SecurityEventJpaRepository;
import com.psymate.identity.repository.SessionNativeQueries;
import com.psymate.identity.repository.TenantJpaRepository;
import com.psymate.shared.time.Timestamps;
import com.psymate.shared.web.IpAddresses;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Couture entre les services d'authentification et les entités du module (practitioner, tenant,
 * security_event). Renvoie des records, jamais d'entité. Les écritures d'entité exigent une
 * transaction : ouverte ici quand l'appelant n'en a pas.
 */
@Repository
public class PractitionerRepository {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final PractitionerJpaRepository practitioners;
  private final TenantJpaRepository tenants;
  private final SecurityEventJpaRepository events;
  private final JsonMapper json;

  PractitionerRepository(
      PractitionerJpaRepository practitioners,
      TenantJpaRepository tenants,
      SecurityEventJpaRepository events,
      JsonMapper json) {
    this.practitioners = practitioners;
    this.tenants = tenants;
    this.events = events;
    this.json = json;
  }

  private static PractitionerRow map(Practitioner p) {
    return new PractitionerRow(
        p.getId(),
        p.getTenantId(),
        p.getEmail(),
        p.getPasswordHash(),
        p.getFirstName(),
        p.getLastName(),
        p.getDisplayName(),
        p.getLocale(),
        p.getProfession(),
        p.getRole(),
        p.getVerificationStatus(),
        new LinkedHashMap<>(p.getPreferences()));
  }

  public Optional<PractitionerRow> findByEmailNormalized(String emailNormalized) {
    return practitioners.findByEmailNormalized(emailNormalized).map(PractitionerRepository::map);
  }

  /**
   * État de sécurité du compte en un seul SELECT de projection (rôle, 2FA active, drapeau de
   * changement de mot de passe) : ni le haché ni le secret TOTP ne sont lus.
   */
  public Optional<AccountState> accountState(UUID id) {
    return practitioners
        .accountSecurity(id)
        .map(
            v ->
                new AccountState(
                    v.role(),
                    v.twoFactorEnabled(),
                    PractitionerRow.mustChangePassword(v.preferences())));
  }

  public Optional<PractitionerRow> findById(UUID id) {
    return practitioners.findById(id).map(PractitionerRepository::map);
  }

  public void insertTenant(UUID id, String name, Instant now) {
    tenants.save(new Tenant(id, name, now));
  }

  /** Coordonnées professionnelles saisies à l'inscription (A10.2). */
  public record Contact(
      String phone,
      String professionalNumber,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly) {}

  public void insertPractitioner(
      PractitionerRow p, String emailNormalized, Contact c, Instant now) {
    practitioners.save(
        Practitioner.register(
            p.id(),
            p.tenantId(),
            p.email(),
            emailNormalized,
            p.passwordHash(),
            p.firstName(),
            p.lastName(),
            p.displayName(),
            p.locale(),
            p.profession(),
            p.preferences(),
            c.phone(),
            c.professionalNumber(),
            c.street(),
            c.postalCode(),
            c.city(),
            c.remoteOnly(),
            now));
  }

  /** Passe email_pending à docs_pending et pose email_confirmed_at (A10.3). */
  @Transactional
  public void confirmEmail(UUID id, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.confirmEmail(now));
  }

  @Transactional
  public void updateEmail(UUID id, String email, String emailNormalized, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.changeEmail(email, emailNormalized, now));
  }

  public void insertSecurityEvent(
      UUID practitionerId, String kind, String ip, String detailsJson, Instant now) {
    Map<String, Object> details = detailsJson == null ? null : json.readValue(detailsJson, MAP);
    events.save(
        new SecurityEvent(UUID.randomUUID(), practitionerId, null, kind, inet(ip), details, now));
  }

  /** Littéral déjà validé par IpAddresses.normalize : aucune résolution DNS. */
  private static InetAddress inet(String ip) {
    String literal = IpAddresses.normalize(ip);
    if (literal == null) {
      return null;
    }
    try {
      return InetAddress.getByName(literal);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  @Transactional
  public void updatePassword(UUID id, String hash, Instant now) {
    practitioners.findById(id).ifPresent(p -> p.changePassword(hash, now));
  }

  /** Retire l'obligation de changer de mot de passe (compte de bootstrap, M7-08). */
  public void clearMustChangePassword(UUID id) {
    practitioners.clearMustChangePassword(id);
  }

  public void touchLastLogin(UUID id, Instant now) {
    practitioners.touchLastLogin(id, Timestamps.utc(now));
  }

  /** Révoque toutes les sessions serveur du praticien (principal_name = id). */
  public void deleteSessions(UUID id) {
    practitioners.deleteSessions(id);
  }

  /** Reflète le compteur d'échecs et le verrou (audit) ; l'application du verrou est en mémoire. */
  public void recordFailedLogin(UUID id, int failures, Instant lockedUntil) {
    practitioners.recordFailedLogin(
        id, (short) Math.min(failures, Short.MAX_VALUE), Timestamps.utc(lockedUntil));
  }

  public void resetFailedLogins(UUID id) {
    practitioners.resetFailedLogins(id);
  }

  /** Révoque les autres sessions du praticien, retourne leur nombre. */
  public int deleteOtherSessions(UUID id, String currentSessionId) {
    return practitioners.deleteOtherSessions(id, currentSessionId);
  }

  /** Comme deleteSessions, avec le nombre de sessions révoquées (journal de sécurité). */
  public int deleteSessionsCount(UUID id) {
    return practitioners.deleteSessions(id);
  }

  /** Sessions du praticien non expirées à {@code now}, les plus récemment utilisées d'abord. */
  public List<SessionNativeQueries.ActiveSessionRow> activeSessions(UUID id, Instant now) {
    return practitioners.findActiveSessions(id, now);
  }

  /** Révoque une session du praticien désignée par sa clé interne ; 0 si elle n'existe plus. */
  public int deleteSession(UUID id, String primaryId) {
    return practitioners.deleteSessionByPrimaryId(id, primaryId);
  }

  /**
   * Dernière connexion réussie (événement login_ok), vide si le compte ne s'est jamais connecté.
   */
  public Optional<LastLogin> lastLogin(UUID id) {
    return events
        .findFirstByPractitionerIdAndKindOrderByOccurredAtDesc(id, "login_ok")
        .map(
            e -> {
              Object device = e.getDetails() == null ? null : e.getDetails().get("device");
              return new LastLogin(
                  Timestamps.instant(e.getOccurredAt()),
                  device instanceof String s ? s : null,
                  null);
            });
  }

  /** Fin de verrou enregistrée en base (second garde, survit à un redémarrage). */
  public Optional<Instant> lockedUntil(UUID id) {
    return practitioners.lockedUntil(id).map(Timestamps::instant);
  }
}
