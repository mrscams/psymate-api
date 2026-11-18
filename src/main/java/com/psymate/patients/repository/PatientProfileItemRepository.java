package com.psymate.patients.repository;

import com.psymate.patients.model.PatientProfileItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès JPA à {@code patient_profile_item} (Profil 360, G Q62) : méthodes dérivées seulement, aucun
 * SQL natif. Aucune suppression : une information se retire par {@code archived_at} (G Q68).
 */
public interface PatientProfileItemRepository extends JpaRepository<PatientProfileItem, UUID> {

  /** Informations actives d'un patient (index partiel ix_patient_profile_item_patient). */
  List<PatientProfileItem> findByPatientIdAndArchivedAtIsNull(UUID patientId);

  /** Nombre d'informations actives d'un patient, pour le plafond (G Q70). */
  long countByPatientIdAndArchivedAtIsNull(UUID patientId);

  /** Une information, seulement si elle appartient à ce patient (active ou retirée). */
  Optional<PatientProfileItem> findByIdAndPatientId(UUID id, UUID patientId);
}
