package com.psymate.identity.admin.repository;

import com.psymate.identity.admin.model.AdminCursors.Position;
import com.psymate.identity.persistence.PractitionerAppeal;
import com.psymate.identity.persistence.PractitionerAppealJpaRepository;
import com.psymate.identity.persistence.Rows;
import com.psymate.identity.persistence.VerificationDecisionJpaRepository;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Couture entre les services de recours (M1-14b, fiche 27) et les entités du module. Ne lit que
 * practitioner_appeal, verification_decision et practitioner : aucune table patient. La page et le
 * détail d'un recours sont des lectures natives (fragment {@code AppealNativeQueries}) ; le dépôt
 * et l'examen passent par l'entité.
 */
@Repository
public class AppealQueries {

  /** Décision contestable : refus du dossier ou suspension. */
  public record DecisionRef(UUID id, String kind, UUID adminId) {}

  /** Recours existant sur une décision. */
  public record ExistingAppeal(UUID id, boolean reviewed) {}

  /** Recours avec la décision contestée et le praticien. */
  public record AppealRow(
      UUID id,
      UUID practitionerId,
      String practitionerName,
      UUID decisionId,
      String decisionKind,
      UUID decisionAdminId,
      String message,
      Instant submittedAt,
      Instant reviewedAt,
      UUID reviewedBy,
      String outcome,
      String responseMessage,
      Instant sortKey) {}

  private final PractitionerAppealJpaRepository appeals;
  private final VerificationDecisionJpaRepository decisions;

  AppealQueries(
      PractitionerAppealJpaRepository appeals, VerificationDecisionJpaRepository decisions) {
    this.appeals = appeals;
    this.decisions = decisions;
  }

  private static AppealRow map(Tuple t) {
    return new AppealRow(
        Rows.uuid(t, "id"),
        Rows.uuid(t, "practitioner_id"),
        Rows.string(t, "display_name"),
        Rows.uuid(t, "decision_id"),
        Rows.string(t, "decision_kind"),
        Rows.uuid(t, "decision_admin_id"),
        Rows.string(t, "message"),
        Rows.instant(t, "submitted_at"),
        Rows.instant(t, "reviewed_at"),
        Rows.uuid(t, "reviewed_by"),
        Rows.string(t, "outcome"),
        Rows.string(t, "response_message"),
        Rows.instant(t, "sort_key"));
  }

  /** Dernière décision de refus ou de suspension du praticien. */
  public Optional<DecisionRef> latestContestableDecision(UUID practitionerId) {
    return decisions.latestContestable(practitionerId, Limit.of(1)).stream()
        .findFirst()
        .map(d -> new DecisionRef(d.id(), d.kind(), d.adminId()));
  }

  /** Vrai si le praticien a un recours non encore examiné, toutes décisions confondues. */
  public boolean hasOpenAppeal(UUID practitionerId) {
    return appeals.existsByPractitionerIdAndReviewedAtIsNull(practitionerId);
  }

  public Optional<ExistingAppeal> findByDecision(UUID decisionId) {
    return appeals.findByDecision(decisionId).map(a -> new ExistingAppeal(a.id(), a.reviewed()));
  }

  /**
   * Enregistre le recours dans la transaction courante. L'INSERT part au flush : une violation de
   * ux_appeal_decision n'est levée qu'au commit, l'appelant la traite après celui-ci.
   */
  @Transactional
  public void insert(UUID id, UUID practitionerId, UUID decisionId, String message, Instant now) {
    appeals.save(new PractitionerAppeal(id, practitionerId, decisionId, message, now));
  }

  /**
   * Page de recours. open : non examinés, du plus ancien au plus récent ; decided et all : du plus
   * récent au plus ancien. Une seule requête native.
   */
  @Transactional(readOnly = true)
  public List<AppealRow> page(String status, Position cursor, int limit) {
    return appeals
        .appealPage(
            status,
            cursor == null ? null : cursor.key(),
            cursor == null ? null : cursor.id(),
            limit)
        .stream()
        .map(AppealQueries::map)
        .toList();
  }

  /**
   * Lecture native : l'auto-flush d'Hibernate écrit d'abord l'examen du recours modifié dans la
   * même transaction, la relecture après {@link #decide} voit donc l'état à jour.
   */
  @Transactional(readOnly = true)
  public Optional<AppealRow> find(UUID id) {
    return appeals.appealRow(id).map(AppealQueries::map);
  }

  @Transactional
  public void decide(UUID id, UUID adminId, String outcome, String responseMessage, Instant now) {
    appeals.findById(id).ifPresent(a -> a.decide(adminId, outcome, responseMessage, now));
  }
}
