package com.psymate.identity.admin.bootstrap;

import com.psymate.identity.model.Practitioner;
import com.psymate.identity.model.SecurityEvent;
import com.psymate.identity.model.Tenant;
import com.psymate.identity.repository.PractitionerJpaRepository;
import com.psymate.identity.repository.SecurityEventJpaRepository;
import com.psymate.identity.repository.TenantJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le premier administrateur (M7-08, G Q5). Le compte reçoit un tenant propre (un tenant par
 * praticien, E3.1), {@code role = 'admin'}, {@code verification_status = 'approved'}, l'e-mail
 * confirmé et la préférence {@code security.mustChangePassword = true} : le premier login est
 * refusé (403 PASSWORD_CHANGE_REQUIRED) jusqu'au changement de mot de passe, puis la 2FA est
 * obligatoire pour {@code /api/admin/**}.
 *
 * <p>Valeurs techniques imposées par le schéma : {@code profession = 'psychologist'} (colonne NOT
 * NULL avec CHECK sur psychologist, psychiatrist, coach ; un admin n'exerce pas, la valeur n'a
 * aucune portée métier), {@code first_name = 'Admin'}, {@code last_name} = partie locale de
 * l'e-mail, {@code display_name = 'Administrateur'}.
 *
 * <p>Le mot de passe en clair n'existe que dans le {@link Result} retourné : il n'est ni journalisé
 * ni écrit en base (seul son haché Argon2id l'est).
 */
@Service
@Transactional
class AdminBootstrapService {

  static final String EVENT_KIND = "admin_bootstrap";
  private static final int LAST_NAME_MAX = 100;

  enum Status {
    CREATED,
    ALREADY_EXISTS
  }

  /** password est null quand le compte existait déjà. */
  record Result(Status status, String email, String password) {}

  private final TenantJpaRepository tenants;
  private final PractitionerJpaRepository practitioners;
  private final SecurityEventJpaRepository securityEvents;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final PasswordGenerator generator = new PasswordGenerator();

  AdminBootstrapService(
      TenantJpaRepository tenants,
      PractitionerJpaRepository practitioners,
      SecurityEventJpaRepository securityEvents,
      PasswordEncoder encoder,
      Clock clock) {
    this.tenants = tenants;
    this.practitioners = practitioners;
    this.securityEvents = securityEvents;
    this.encoder = encoder;
    this.clock = clock;
  }

  /** Même normalisation que l'inscription : trim puis minuscules. */
  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  Result create(String email) {
    String normalized = normalizeEmail(email);
    if (practitioners.existsByEmailNormalized(normalized)) {
      return new Result(Status.ALREADY_EXISTS, normalized, null);
    }
    String password = generator.generate();
    Instant now = clock.instant();
    UUID tenantId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    String local = normalized.substring(0, normalized.indexOf('@'));
    String lastName = local.length() > LAST_NAME_MAX ? local.substring(0, LAST_NAME_MAX) : local;

    // tenant, practitioner et security_event, rien d'autre ; la 2FA reste inactive.
    tenants.save(new Tenant(tenantId, normalized, now));
    practitioners.save(
        Practitioner.bootstrapAdmin(
            id, tenantId, normalized, encoder.encode(password), lastName, now));
    securityEvents.save(
        new SecurityEvent(UUID.randomUUID(), id, null, EVENT_KIND, null, null, now));
    return new Result(Status.CREATED, normalized, password);
  }
}
