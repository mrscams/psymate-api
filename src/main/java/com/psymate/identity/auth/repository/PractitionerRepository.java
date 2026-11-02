package com.psymate.identity.auth.repository;

import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.persistence.Practitioner;
import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.SecurityEvent;
import com.psymate.identity.persistence.SecurityEventJpaRepository;
import com.psymate.identity.persistence.Tenant;
import com.psymate.identity.persistence.TenantJpaRepository;
import com.psymate.shared.time.Timestamps;
import com.psymate.shared.web.IpAddresses;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
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

  /** Fin de verrou enregistrée en base (second garde, survit à un redémarrage). */
  public Optional<Instant> lockedUntil(UUID id) {
    return practitioners.lockedUntil(id).map(Timestamps::instant);
  }
}
