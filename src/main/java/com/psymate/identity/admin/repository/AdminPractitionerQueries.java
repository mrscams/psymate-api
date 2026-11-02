package com.psymate.identity.admin.repository;

import com.psymate.identity.admin.model.AdminCursors.Position;
import com.psymate.identity.persistence.Practitioner;
import com.psymate.identity.persistence.PractitionerAppealJpaRepository;
import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.Rows;
import com.psymate.shared.time.Timestamps;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Couture entre les services de gestion des comptes (liste, détail, suspension, réactivation,
 * M1-14b) et les entités du module. Ne lit que practitioner, verification_decision,
 * practitioner_appeal et security_event : aucune table patient, clinique, de liens ni d'annuaire.
 * La liste, les compteurs et le détail sont des lectures natives (fragment {@code
 * AdminPractitionerNativeQueries}, une requête chacun) ; les écritures passent par les méthodes
 * métier de l'entité.
 */
@Repository
public class AdminPractitionerQueries {

  /** Critères de la liste ; un critère null est ignoré. */
  public record Filter(String q, String status, String role, String profession) {}

  /** Compte tel que listé : aucune donnée de sécurité autre que l'état de la 2FA. */
  public record AccountRow(
      UUID id,
      String displayName,
      String email,
      String profession,
      String city,
      String role,
      String status,
      Instant verifiedAt,
      Instant submittedAt,
      Instant updatedAt,
      boolean twoFactorEnabled,
      Instant createdAt,
      Instant lastLoginAt,
      Instant suspendedAt,
      Instant purgeAt,
      Instant sortKey) {}

  public record AppealRow(
      UUID id,
      UUID decisionId,
      Instant submittedAt,
      Instant reviewedAt,
      String outcome,
      String message,
      String responseMessage) {}

  /** Statuts comptés pour les onglets de la liste. */
  static final List<String> COUNTED =
      List.of("approved", "under_review", "needs_more", "suspended", "rejected");

  private final PractitionerJpaRepository practitioners;
  private final PractitionerAppealJpaRepository appeals;

  AdminPractitionerQueries(
      PractitionerJpaRepository practitioners, PractitionerAppealJpaRepository appeals) {
    this.practitioners = practitioners;
    this.appeals = appeals;
  }

  private static AccountRow map(Tuple t) {
    return new AccountRow(
        Rows.uuid(t, "id"),
        Rows.string(t, "display_name"),
        Rows.string(t, "email"),
        Rows.string(t, "profession"),
        Rows.string(t, "address_city"),
        Rows.string(t, "role"),
        Rows.string(t, "verification_status"),
        Rows.instant(t, "verified_at"),
        Rows.instant(t, "submitted_at"),
        Rows.instant(t, "updated_at"),
        Rows.bool(t, "totp"),
        Rows.instant(t, "created_at"),
        Rows.instant(t, "last_login_at"),
        Rows.instant(t, "suspended_at"),
        Rows.instant(t, "purge_after"),
        Rows.instant(t, "sort_key"));
  }

  /** Page triée par création décroissante, puis identifiant décroissant. Une seule requête. */
  @Transactional(readOnly = true)
  public List<AccountRow> list(Filter f, Position cursor, int limit) {
    return practitioners
        .adminAccounts(
            f.q(),
            f.status(),
            f.role(),
            f.profession(),
            cursor == null ? null : cursor.key(),
            cursor == null ? null : cursor.id(),
            limit)
        .stream()
        .map(AdminPractitionerQueries::map)
        .toList();
  }

  /** Nombre de comptes par statut suivi, hors filtre de statut. */
  @Transactional(readOnly = true)
  public Map<String, Integer> counts(Filter f) {
    Map<String, Integer> found = practitioners.adminStatusCounts(f.q(), f.role(), f.profession());
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String s : COUNTED) {
      counts.put(s, found.getOrDefault(s, 0));
    }
    return counts;
  }

  @Transactional(readOnly = true)
  public Optional<AccountRow> find(UUID id) {
    return practitioners.adminAccount(id).map(AdminPractitionerQueries::map);
  }

  @Transactional(readOnly = true)
  public List<AppealRow> appeals(UUID practitionerId) {
    return appeals.summaries(practitionerId).stream()
        .map(
            a ->
                new AppealRow(
                    a.id(),
                    a.decisionId(),
                    Timestamps.instant(a.submittedAt()),
                    Timestamps.instant(a.reviewedAt()),
                    a.outcome(),
                    a.message(),
                    a.responseMessage()))
        .toList();
  }

  // ---- suspension et réactivation

  /**
   * Verrouille tous les administrateurs actifs, par id croissant, et renvoie leurs identifiants.
   * L'ordre unique de verrouillage sérialise deux suspensions croisées d'administrateurs ; la cible
   * exclue, une liste vide signifie qu'elle est le dernier administrateur.
   */
  @Transactional
  public List<UUID> lockActiveAdmins() {
    return practitioners.lockActiveAdmins().stream().map(Practitioner::getId).toList();
  }

  /** Le compte est déjà verrouillé par l'appelant (lockAccount) dans la même transaction. */
  @Transactional
  public void markSuspended(UUID practitionerId, Instant now) {
    practitioners.findById(practitionerId).ifPresent(p -> p.markSuspended(now));
  }

  /** Le compte est déjà verrouillé par l'appelant (lockAccount) dans la même transaction. */
  @Transactional
  public void markReactivated(UUID practitionerId, String status, Instant now) {
    practitioners.findById(practitionerId).ifPresent(p -> p.markReactivated(status, now));
  }

  /** Statut que le compte avait avant sa dernière suspension manuelle, lu dans la checklist. */
  @Transactional(readOnly = true)
  public Optional<String> previousStatusBeforeSuspension(UUID practitionerId) {
    return practitioners.previousStatusBeforeSuspension(practitionerId);
  }
}
