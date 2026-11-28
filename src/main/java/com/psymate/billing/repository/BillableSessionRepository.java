package com.psymate.billing.repository;

import com.psymate.billing.model.BillableSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code billable_session} (V18, G Q32, Q86) ; insertion idempotente et lectures des
 * factures par le fragment {@link BillableSessionNativeQueries} (G Q38). Toute lecture faite pour
 * une requête est filtrée par praticien ; seule l'écoute de {@code EntryArchived}, sans praticien
 * connecté, lit par identifiant.
 */
public interface BillableSessionRepository
    extends JpaRepository<BillableSession, UUID>, BillableSessionNativeQueries {

  /**
   * Séance du praticien, verrouillée jusqu'à la fin de la transaction ({@code select … for update})
   * : « ne pas facturer », « remettre », changement de tarif et mise en facture (M15-08) se suivent
   * sur une même séance.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s from BillableSession s
      where s.sessionEntryId = :id and s.practitionerId = :practitionerId
      """)
  Optional<BillableSession> findForUpdate(
      @Param("id") UUID sessionEntryId, @Param("practitionerId") UUID practitionerId);

  /** Séances d'un état du praticien jusqu'au {@code until} inclus, par date. */
  @Query(
      """
      select s from BillableSession s
      where s.practitionerId = :practitionerId and s.state = :state and s.occurredOn <= :until
      order by s.occurredOn, s.sessionEntryId
      """)
  List<BillableSession> findByStateUntil(
      @Param("practitionerId") UUID practitionerId,
      @Param("state") String state,
      @Param("until") LocalDate until);

  /** Séances d'un état d'un patient du praticien jusqu'au {@code until} inclus, par date. */
  @Query(
      """
      select s from BillableSession s
      where s.practitionerId = :practitionerId and s.patientId = :patientId
        and s.state = :state and s.occurredOn <= :until
      order by s.occurredOn, s.sessionEntryId
      """)
  List<BillableSession> findByPatientAndStateUntil(
      @Param("practitionerId") UUID practitionerId,
      @Param("patientId") UUID patientId,
      @Param("state") String state,
      @Param("until") LocalDate until);

  /** Séances d'un état du praticien depuis {@code since} inclus, la plus récente d'abord. */
  @Query(
      """
      select s from BillableSession s
      where s.practitionerId = :practitionerId and s.state = :state and s.occurredOn >= :since
      order by s.occurredOn desc, s.sessionEntryId
      """)
  List<BillableSession> findByStateSince(
      @Param("practitionerId") UUID practitionerId,
      @Param("state") String state,
      @Param("since") LocalDate since);

  /** Nombre de séances d'un état du praticien depuis {@code since} inclus. */
  long countByPractitionerIdAndStateAndOccurredOnGreaterThanEqual(
      UUID practitionerId, String state, LocalDate since);

  /** Nombre de séances d'un état d'un patient du praticien depuis {@code since} inclus. */
  long countByPractitionerIdAndPatientIdAndStateAndOccurredOnGreaterThanEqual(
      UUID practitionerId, UUID patientId, String state, LocalDate since);
}
