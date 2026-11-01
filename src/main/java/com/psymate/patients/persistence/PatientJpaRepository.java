package com.psymate.patients.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code patient} ; les deux requêtes f_unaccent sont dans PatientNativeQueries. */
public interface PatientJpaRepository extends JpaRepository<Patient, UUID>, PatientNativeQueries {

  /**
   * Incrément atomique côté SQL (G Q30) : des écritures cliniques concurrentes ne perdent aucun
   * incrément, ce qu'un UPDATE d'entité (valeur lue, écrasée) ne garantirait pas.
   */
  @Modifying
  @Query("update Patient p set p.clinicalRev = p.clinicalRev + 1 where p.id = :id")
  int touchClinicalRev(@Param("id") UUID id);

  /**
   * Charge la fiche avec un verrou de ligne (FOR UPDATE) : deux PATCH concurrents se sérialisent,
   * comme le faisait {@code current_version = current_version + 1} en SQL, au lieu de produire deux
   * fois la même version (ux_patient_version).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Patient p where p.id = :id")
  Optional<Patient> findForUpdate(@Param("id") UUID id);
}
