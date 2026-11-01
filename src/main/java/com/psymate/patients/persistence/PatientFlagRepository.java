package com.psymate.patients.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientFlagRepository extends JpaRepository<PatientFlag, UUID> {

  boolean existsByPatientIdAndKindAndClearedAtIsNull(UUID patientId, String kind);

  Optional<PatientFlag> findByPatientIdAndKindAndClearedAtIsNull(UUID patientId, String kind);

  /** Drapeaux actifs de plusieurs patients en une requete (pas de N+1). */
  List<PatientFlag> findByPatientIdInAndClearedAtIsNullOrderBySetAt(Collection<UUID> patientIds);
}
