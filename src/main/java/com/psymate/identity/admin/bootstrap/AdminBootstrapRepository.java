package com.psymate.identity.admin.bootstrap;

import com.psymate.identity.persistence.Practitioner;
import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.SecurityEvent;
import com.psymate.identity.persistence.SecurityEventJpaRepository;
import com.psymate.identity.persistence.Tenant;
import com.psymate.identity.persistence.TenantJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Accès du bootstrap : tenant, practitioner et security_event, rien d'autre. */
@Repository
class AdminBootstrapRepository {

  private final TenantJpaRepository tenants;
  private final PractitionerJpaRepository practitioners;
  private final SecurityEventJpaRepository events;

  AdminBootstrapRepository(
      TenantJpaRepository tenants,
      PractitionerJpaRepository practitioners,
      SecurityEventJpaRepository events) {
    this.tenants = tenants;
    this.practitioners = practitioners;
    this.events = events;
  }

  boolean emailExists(String emailNormalized) {
    return practitioners.existsByEmailNormalized(emailNormalized);
  }

  void insertTenant(UUID id, String name, Instant now) {
    tenants.save(new Tenant(id, name, now));
  }

  /**
   * Insère l'administrateur : role admin, approuvé, e-mail confirmé, préférence
   * security.mustChangePassword. La 2FA reste inactive (totp_* NULL, contrainte
   * ck_practitioner_admin_totp respectée).
   */
  void insertAdmin(
      UUID id, UUID tenantId, String email, String passwordHash, String lastName, Instant now) {
    practitioners.save(
        Practitioner.bootstrapAdmin(id, tenantId, email, passwordHash, lastName, now));
  }

  void insertSecurityEvent(UUID practitionerId, String kind, Instant now) {
    events.save(new SecurityEvent(UUID.randomUUID(), practitionerId, null, kind, null, null, now));
  }
}
