package com.psymate.clinical.export.repository;

import com.psymate.clinical.export.model.ExportJob;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès Spring Data à {@code export_job} ; interne au module, jamais exposé hors de {@code export}.
 * Le SQL natif du sous-domaine passe par ses fragments (G Q38) : le verrou praticien ({@link
 * ExportJobNativeQueries}) et la lecture du dossier à exporter ({@link ExportNativeQueries}).
 */
interface ExportJobJpaRepository
    extends JpaRepository<ExportJob, UUID>, ExportJobNativeQueries, ExportNativeQueries {

  /** Ligne verrouillée ({@code for update}) pour une transition de statut sous verrou. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from ExportJob j where j.id = :id")
  Optional<ExportJob> findForUpdate(@Param("id") UUID id);

  boolean existsByPractitionerIdAndStatusIn(UUID practitionerId, Collection<String> statuses);

  /**
   * Marque en échec ({@code interrupted}) les jobs queued ou running du praticien plus anciens que
   * {@code before}. UPDATE en masse : aucune entité chargée, appelé dans la transaction de la
   * demande ({@code @Transactional} explicite : une méthode {@code @Modifying} d'interface hérite
   * sinon d'une transaction en lecture seule).
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ExportJob j set j.status = 'failed', j.error = 'interrupted', j.finishedAt = :now"
          + " where j.practitionerId = :p and j.status in ('queued', 'running')"
          + " and j.requestedAt < :before")
  int failStale(
      @Param("p") UUID practitionerId,
      @Param("before") OffsetDateTime before,
      @Param("now") OffsetDateTime now);

  /** Jobs terminés dont l'échéance est passée et dont le fichier n'est pas encore supprimé. */
  @Query(
      "select j from ExportJob j where j.status = 'done' and j.purgedAt is null"
          + " and j.expiresAt < :now order by j.expiresAt")
  List<ExportJob> dueForPurge(@Param("now") OffsetDateTime now);
}
