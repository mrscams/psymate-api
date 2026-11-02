package com.psymate.patients.repository;

import com.psymate.patients.model.PatientVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientVersionRepository extends JpaRepository<PatientVersion, UUID> {

  List<PatientVersion> findByPatientIdOrderByVersion(UUID patientId);
}
