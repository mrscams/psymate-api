package com.psymate.identity.repository;

import com.psymate.identity.model.PractitionerAppeal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code practitioner_appeal}. La page des recours de l'administration (trois tables,
 * curseur keyset) est dans le fragment {@link AppealNativeQueries}.
 */
public interface PractitionerAppealJpaRepository
    extends JpaRepository<PractitionerAppeal, UUID>, AppealNativeQueries {

  /** Recours d'un compte, vu du détail administrateur. */
  record Summary(
      UUID id,
      UUID decisionId,
      OffsetDateTime submittedAt,
      OffsetDateTime reviewedAt,
      String outcome,
      String message,
      String responseMessage) {}

  /** Recours sur une décision : identifiant et état d'examen. */
  record Existing(UUID id, boolean reviewed) {}

  /** Vrai si le praticien a un recours non encore examiné, toutes décisions confondues. */
  boolean existsByPractitionerIdAndReviewedAtIsNull(UUID practitionerId);

  @Query(
      "select new com.psymate.identity.repository.PractitionerAppealJpaRepository$Existing("
          + "a.id, case when a.reviewedAt is not null then true else false end)"
          + " from PractitionerAppeal a where a.decisionId = :d")
  Optional<Existing> findByDecision(@Param("d") UUID decisionId);

  /** Recours d'un praticien, du plus récent au plus ancien. */
  @Query(
      "select new com.psymate.identity.repository.PractitionerAppealJpaRepository$Summary("
          + "a.id, a.decisionId, a.submittedAt, a.reviewedAt, a.outcome, a.message,"
          + " a.responseMessage) from PractitionerAppeal a where a.practitionerId = :p"
          + " order by a.submittedAt desc, a.id")
  List<Summary> summaries(@Param("p") UUID practitionerId);
}
