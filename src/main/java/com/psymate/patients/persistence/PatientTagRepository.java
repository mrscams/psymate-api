package com.psymate.patients.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientTagRepository extends JpaRepository<PatientTag, PatientTagId> {

  List<PatientTag> findByIdPatientId(UUID patientId);

  /** Tags de plusieurs patients en une requete (pas de N+1). */
  List<PatientTag> findByIdPatientIdIn(Collection<UUID> patientIds);
}
