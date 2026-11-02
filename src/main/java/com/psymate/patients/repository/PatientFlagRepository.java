package com.psymate.patients.repository;

import com.psymate.patients.model.PatientFlag;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientFlagRepository extends JpaRepository<PatientFlag, UUID> {

  boolean existsByPatientIdAndKindAndClearedAtIsNull(UUID patientId, String kind);

  /**
   * Lève le drapeau actif en une seule instruction conditionnelle ({@code cleared_at is null}) :
   * deux levées concurrentes ne réussissent qu'une fois, la seconde voit 0 ligne (404), comme
   * l'ancien UPDATE SQL.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "update PatientFlag f set f.clearedAt = :now, f.clearedBy = :by"
          + " where f.patientId = :patientId and f.kind = :kind and f.clearedAt is null")
  int clearActive(
      @Param("patientId") UUID patientId,
      @Param("kind") String kind,
      @Param("by") UUID by,
      @Param("now") OffsetDateTime now);

  /** Drapeaux actifs de plusieurs patients en une requete (pas de N+1). */
  List<PatientFlag> findByPatientIdInAndClearedAtIsNullOrderBySetAt(Collection<UUID> patientIds);
}
