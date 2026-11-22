package com.psymate.agenda.repository;

import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.model.BookingRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès JPA à {@code booking_request} (V15, G Q32) ; SQL natif par le fragment {@link
 * BookingNativeQueries} (G Q38). Les jetons ne sont jamais cherchés en clair : seulement par leur
 * empreinte SHA-256. Expiration et purges : M14-07 (E9).
 */
public interface BookingRequestRepository
    extends JpaRepository<BookingRequest, UUID>, BookingNativeQueries {

  /** Demande d'un jeton de vérification, par son empreinte. */
  Optional<BookingRequest> findByVerifyTokenHash(String verifyTokenHash);

  /** Demande d'un jeton de gestion en cours, par son empreinte. */
  Optional<BookingRequest> findByManageTokenHash(String manageTokenHash);

  /** Demande à l'origine d'un rendez-vous en ligne. */
  Optional<BookingRequest> findByAppointmentId(UUID appointmentId);

  /**
   * Praticien d'une demande, par l'empreinte de son jeton de vérification, sans charger l'entité :
   * lu avant de prendre le verrou du praticien, la demande elle-même est relue après.
   */
  @Query("select b.practitionerId from BookingRequest b where b.verifyTokenHash = :verifyTokenHash")
  Optional<UUID> findPractitionerIdByVerifyTokenHash(
      @Param("verifyTokenHash") String verifyTokenHash);

  /** Comme {@link #findPractitionerIdByVerifyTokenHash}, par l'empreinte du jeton de gestion. */
  @Query("select b.practitionerId from BookingRequest b where b.manageTokenHash = :manageTokenHash")
  Optional<UUID> findPractitionerIdByManageTokenHash(
      @Param("manageTokenHash") String manageTokenHash);

  /**
   * Demandes créées depuis {@code since} pour une empreinte d'adresse, vérifiées ou non : plafond
   * de 3 e-mails de vérification par heure et par adresse (G Q79).
   */
  long countByEmailHashAndCreatedAtAfter(String emailHash, OffsetDateTime since);

  // ---- M14-07 : expiration et purges (G Q82, E9)

  /**
   * Rendez-vous en ligne encore en attente ({@code tentative}) dont la demande a été vérifiée avant
   * {@code verifiedBefore} ou dont le créneau a commencé à {@code now} : candidats de l'expiration,
   * triés par début. L'appelant relit chacun sous le verrou du praticien.
   */
  @Query(
      """
      select a.id from Appointment a, BookingRequest b
      where b.appointmentId = a.id
        and a.origin = :online
        and a.status = com.psymate.agenda.model.AppointmentStatus.tentative
        and (b.verifiedAt < :verifiedBefore or a.startsAt <= :now)
      order by a.startsAt, a.id
      """)
  List<UUID> findExpiringAppointmentIds(
      @Param("online") AppointmentOrigin online,
      @Param("verifiedBefore") OffsetDateTime verifiedBefore,
      @Param("now") OffsetDateTime now);

  /**
   * Purge (E9, G Q82 a) : supprime les demandes jamais vérifiées créées avant {@code cutoff} et
   * renvoie leur nombre.
   */
  @Transactional
  @Modifying
  @Query("delete from BookingRequest b where b.verifiedAt is null and b.createdAt < :cutoff")
  int deleteUnverifiedCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);

  /**
   * Purge (E9, G Q82 f) : supprime les demandes vérifiées dont le rendez-vous s'est terminé, ou a
   * été annulé, avant {@code cutoff}, et renvoie leur nombre. Le rendez-vous reste.
   */
  @Transactional
  @Modifying
  @Query(
      """
      delete from BookingRequest b
      where b.verifiedAt is not null
        and exists (select a.id from Appointment a
                    where a.id = b.appointmentId
                      and (a.endsAt < :cutoff or a.cancelledAt < :cutoff))
      """)
  int deleteClosedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
