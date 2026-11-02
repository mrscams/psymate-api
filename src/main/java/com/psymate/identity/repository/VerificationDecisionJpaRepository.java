package com.psymate.identity.repository;

import com.psymate.identity.model.VerificationDecision;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code verification_decision} : insertion et lecture seulement (append-only). */
public interface VerificationDecisionJpaRepository
    extends JpaRepository<VerificationDecision, UUID> {

  /** Message reçu par le praticien : la note interne n'est jamais chargée. */
  record Message(String kind, String message, OffsetDateTime decidedAt) {}

  /** Décision contestable : refus du dossier ou suspension. */
  record Contestable(UUID id, String kind, UUID adminId) {}

  /** Dernière décision de refus ou de suspension du praticien ({@code Limit.of(1)}). */
  @Query(
      "select new com.psymate.identity.repository.VerificationDecisionJpaRepository$Contestable("
          + "d.id, d.kind, d.adminId) from VerificationDecision d where d.practitionerId = :p"
          + " and d.kind in ('rejected','suspended') order by d.decidedAt desc, d.id desc")
  List<Contestable> latestContestable(@Param("p") UUID practitionerId, Limit limit);

  /** Décisions d'un praticien, de la plus récente à la plus ancienne. */
  List<VerificationDecision> findByPractitionerIdOrderByDecidedAtDescIdAsc(UUID practitionerId);

  /** Messages reçus uniquement, du plus récent au plus ancien. */
  @Query(
      "select new com.psymate.identity.repository.VerificationDecisionJpaRepository$Message("
          + "d.kind, d.messageToPractitioner, d.decidedAt) from VerificationDecision d"
          + " where d.practitionerId = :p and d.messageToPractitioner is not null"
          + " order by d.decidedAt desc, d.id")
  List<Message> messages(@Param("p") UUID practitionerId);
}
