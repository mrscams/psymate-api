package com.psymate.agenda.repository;

import com.psymate.agenda.model.BookingRequest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code booking_request} (V15, G Q32) ; SQL natif par le fragment {@link
 * BookingNativeQueries} (G Q38). Les jetons ne sont jamais cherchés en clair : seulement par leur
 * empreinte SHA-256. Les purges arrivent avec M14-07.
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
}
