package com.psymate.agenda.repository;

import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
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

  // ---- M11-07 : séries (G Q55)

  /**
   * Occurrences d'une série appartenant au praticien, tous statuts, triées par début : une série
   * d'un autre praticien est vide, donc introuvable (G Q58).
   */
  List<Appointment> findBySeriesIdAndPractitionerIdOrderByStartsAtAscIdAsc(
      UUID seriesId, UUID practitionerId);

  // ---- lectures de l'agenda (M11-06)

  /**
   * Rendez-vous du praticien dont le créneau chevauche [from, to[ ({@code startsAt < to AND endsAt
   * > from}), parmi ces statuts, triés par début (I3, fenêtre de {@code GET /api/agenda}).
   */
  @Query(
      """
      select a from Appointment a
      where a.practitionerId = :practitionerId
        and a.startsAt < :to and a.endsAt > :from
        and a.status in :statuses
      order by a.startsAt, a.id
      """)
  List<Appointment> findWindow(
      @Param("practitionerId") UUID practitionerId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to,
      @Param("statuses") Collection<AppointmentStatus> statuses);

  /**
   * Rendez-vous du praticien terminés au plus tard à {@code before} et commencés au plus tôt à
   * {@code since}, du plus tard terminé au plus tôt (freebusy : « précédent » du même jour).
   */
  @Query(
      """
      select a from Appointment a
      where a.practitionerId = :practitionerId
        and a.endsAt <= :before and a.startsAt >= :since
        and a.status in :statuses
        and a.id <> :excludeId
      order by a.endsAt desc, a.startsAt desc, a.id
      """)
  List<Appointment> findEndingBefore(
      @Param("practitionerId") UUID practitionerId,
      @Param("since") OffsetDateTime since,
      @Param("before") OffsetDateTime before,
      @Param("statuses") Collection<AppointmentStatus> statuses,
      @Param("excludeId") UUID excludeId,
      Limit limit);

  /**
   * Rendez-vous du praticien commencés dans [after, until[, du plus tôt au plus tard (freebusy : «
   * suivant » du même jour).
   */
  @Query(
      """
      select a from Appointment a
      where a.practitionerId = :practitionerId
        and a.startsAt >= :after and a.startsAt < :until
        and a.status in :statuses
        and a.id <> :excludeId
      order by a.startsAt, a.id
      """)
  List<Appointment> findStartingAfter(
      @Param("practitionerId") UUID practitionerId,
      @Param("after") OffsetDateTime after,
      @Param("until") OffsetDateTime until,
      @Param("statuses") Collection<AppointmentStatus> statuses,
      @Param("excludeId") UUID excludeId,
      Limit limit);

  /**
   * Rendez-vous d'un patient chez ce praticien, parmi ces statuts, non terminés à {@code from}
   * ({@code endsAt > from}), triés par début ({@code GET /api/patients/{id}/appointments}).
   */
  @Query(
      """
      select a from Appointment a
      where a.practitionerId = :practitionerId
        and a.patientId = :patientId
        and a.endsAt > :from
        and a.status in :statuses
      order by a.startsAt, a.id
      """)
  List<Appointment> findOfPatient(
      @Param("practitionerId") UUID practitionerId,
      @Param("patientId") UUID patientId,
      @Param("from") OffsetDateTime from,
      @Param("statuses") Collection<AppointmentStatus> statuses,
      Limit limit);
}
