package com.psymate.agenda.repository;

import com.psymate.agenda.model.Appointment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code appointment} (G Q32 ; fenêtres et chevauchements en JPQL, aucun SQL natif).
 * M11-02 ne pose que la lecture par propriétaire : M11-05 et M11-06 ajoutent leurs méthodes.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

  /** Le rendez-vous d'un autre praticien est introuvable, jamais interdit (G Q58). */
  Optional<Appointment> findByIdAndPractitionerId(UUID id, UUID practitionerId);

  // ---- M11-05 : chevauchements (G Q54)

  /**
   * Rendez-vous actifs ({@code planned}, {@code tentative}) du praticien qui chevauchent le créneau
   * {@code [from, to[}, sauf {@code excludeId} : intervalles semi-ouverts, un rendez-vous qui finit
   * à 10:00 ne chevauche pas un créneau qui commence à 10:00. Triés par début.
   */
  @Query(
      """
      select a from Appointment a
      where a.practitionerId = :practitionerId
        and a.status in (com.psymate.agenda.model.AppointmentStatus.planned,
                         com.psymate.agenda.model.AppointmentStatus.tentative)
        and a.startsAt < :to and a.endsAt > :from
        and a.id <> :excludeId
      order by a.startsAt, a.id
      """)
  List<Appointment> findActiveOverlaps(
      @Param("practitionerId") UUID practitionerId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to,
      @Param("excludeId") UUID excludeId);
}
