package com.psymate.agenda.repository;

import com.psymate.agenda.model.Appointment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès JPA à {@code appointment} (G Q32 ; fenêtres et chevauchements en JPQL, aucun SQL natif).
 * M11-02 ne pose que la lecture par propriétaire : M11-05 et M11-06 ajoutent leurs méthodes.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

  /** Le rendez-vous d'un autre praticien est introuvable, jamais interdit (G Q58). */
  Optional<Appointment> findByIdAndPractitionerId(UUID id, UUID practitionerId);
}
