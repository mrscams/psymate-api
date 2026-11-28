package com.psymate.billing.repository;

import com.psymate.billing.model.BillingInvoiceRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link BillableSessionNativeQueries}. */
class BillableSessionNativeQueriesImpl implements BillableSessionNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public int insertPendingIfAbsent(
      UUID sessionEntryId,
      UUID tenantId,
      UUID practitionerId,
      UUID patientId,
      LocalDate occurredOn,
      String modality,
      Integer durationMin) {
    return em.createNativeQuery(
            """
            insert into billable_session (session_entry_id, tenant_id, practitioner_id, \
            patient_id, occurred_on, modality, duration_min, state)
            values (:id, :tenant, :practitioner, :patient, :occurredOn, :modality, \
            cast(:duration as integer), 'pending')
            on conflict (session_entry_id) do nothing
            """)
        .setParameter("id", sessionEntryId)
        .setParameter("tenant", tenantId)
        .setParameter("practitioner", practitionerId)
        .setParameter("patient", patientId)
        .setParameter("occurredOn", occurredOn)
        .setParameter("modality", modality)
        .setParameter("duration", durationMin)
        .executeUpdate();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public int dismissIfPending(UUID sessionEntryId, OffsetDateTime now) {
    return em.createNativeQuery(
            """
            update billable_session set state = 'dismissed', updated_at = :now
            where session_entry_id = :id and state = 'pending'
            """)
        .setParameter("now", now)
        .setParameter("id", sessionEntryId)
        .executeUpdate();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Integer> openDraftCounts(UUID practitionerId, Collection<UUID> patientIds) {
    Map<UUID, Integer> counts = new HashMap<>();
    if (patientIds.isEmpty()) {
      return counts;
    }
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                """
                select patient_id, count(*) from invoice
                where practitioner_id = :practitioner and patient_id in (:patients)
                  and kind = 'invoice' and status = 'draft'
                group by patient_id
                """)
            .setParameter("practitioner", practitionerId)
            .setParameter("patients", patientIds)
            .getResultList();
    for (Object[] row : rows) {
      counts.put((UUID) row[0], ((Number) row[1]).intValue());
    }
    return counts;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, BillingInvoiceRef> lastInvoices(
      UUID practitionerId, Collection<UUID> patientIds) {
    Map<UUID, BillingInvoiceRef> last = new HashMap<>();
    if (patientIds.isEmpty()) {
      return last;
    }
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                """
                select distinct on (patient_id) patient_id, number, issue_date from invoice
                where practitioner_id = :practitioner and patient_id in (:patients)
                  and kind = 'invoice' and status in ('issued', 'void')
                order by patient_id, issue_date desc, issued_at desc nulls last, id desc
                """)
            .setParameter("practitioner", practitionerId)
            .setParameter("patients", patientIds)
            .getResultList();
    for (Object[] row : rows) {
      last.put((UUID) row[0], new BillingInvoiceRef((String) row[1], localDate(row[2])));
    }
    return last;
  }

  private static LocalDate localDate(Object value) {
    return switch (value) {
      case LocalDate d -> d;
      case Date d -> d.toLocalDate();
      default -> LocalDate.parse(value.toString());
    };
  }
}
