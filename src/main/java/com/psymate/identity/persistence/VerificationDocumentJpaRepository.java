package com.psymate.identity.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès JPA à {@code verification_document}. Les écritures qui peuvent survenir hors transaction
 * (purge planifiée) ou qui doivent rester atomiques sont des UPDATE JPQL conditionnels avec leur
 * propre {@code @Transactional}.
 */
public interface VerificationDocumentJpaRepository
    extends JpaRepository<VerificationDocument, UUID> {

  /** Fichier à purger : identifiant et clé de stockage seulement. */
  record DuePurge(UUID id, String storageKey) {}

  /** Justificatifs de vérification non retirés par le praticien, du plus ancien au plus récent. */
  @Query(
      "select d from VerificationDocument d where d.practitionerId = :p"
          + " and d.purpose = 'verification' and d.deletedAt is null"
          + " order by d.uploadedAt, d.id")
  List<VerificationDocument> activeDocuments(@Param("p") UUID practitionerId);

  /** Justificatif d'un praticien donné, retiré ou non (l'appelant filtre deleted_at). */
  Optional<VerificationDocument> findByIdAndPractitionerId(UUID id, UUID practitionerId);

  /** Fichiers dont purge_after est dépassé et non encore purgés, les plus anciens d'abord. */
  @Query(
      "select new com.psymate.identity.persistence.VerificationDocumentJpaRepository$DuePurge("
          + "d.id, d.storageKey) from VerificationDocument d where d.purgeAfter < :now"
          + " and d.purgedAt is null order by d.purgeAfter, d.id")
  List<DuePurge> duePurges(@Param("now") OffsetDateTime now, Limit limit);

  /** Pose purged_at une seule fois : renvoie 1 la première fois, 0 ensuite (idempotent). */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update VerificationDocument d set d.purgedAt = :now where d.id = :id"
          + " and d.purgedAt is null")
  int markPurged(@Param("id") UUID id, @Param("now") OffsetDateTime now);

  /** Planifie la purge des fichiers du dossier (décision + 90 jours). */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update VerificationDocument d set d.purgeAfter = :purgeAfter"
          + " where d.practitionerId = :p and d.purpose = 'verification'"
          + " and d.deletedAt is null and d.purgedAt is null")
  int schedulePurge(
      @Param("p") UUID practitionerId, @Param("purgeAfter") OffsetDateTime purgeAfter);
}
