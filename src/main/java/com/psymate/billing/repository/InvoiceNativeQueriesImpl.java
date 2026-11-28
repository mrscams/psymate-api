package com.psymate.billing.repository;

import com.psymate.billing.model.InvoiceCursor;
import com.psymate.billing.model.InvoiceListCriteria;
import com.psymate.billing.model.InvoiceSummaryRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link InvoiceNativeQueries}. */
class InvoiceNativeQueriesImpl implements InvoiceNativeQueries {

  /**
   * Résumés calculés : {@code base} (total d'un brouillon recalculé, échéance effective), {@code
   * calc} (reste dû), {@code shown} (statut exposé, retard, rang de tri). La clause {@code where}
   * et la limite sont ajoutées par l'appelant.
   */
  private static final String SUMMARY =
      """
      with base as (
        select i.id, i.kind, i.status as stored_status, i.number, i.patient_id, i.issue_date,
          i.cancels_invoice_id, i.recipient_name, i.paid_cents, i.credited_cents,
          case when i.status = 'draft'
               then i.subtotal_cents + coalesce(cast(floor(
                 i.subtotal_cents * cast(:rate as integer) / 10000.0 + 0.5) as bigint), 0)
               else i.total_cents end as total,
          coalesce(i.due_on, case i.due_term when 'days_15' then i.issue_date + 15
                                             when 'days_30' then i.issue_date + 30
                                             else i.issue_date end) as due_on
        from invoice i
        where i.practitioner_id = :practitioner
      ), calc as (
        select b.*,
          case when b.kind = 'credit_note' then cast(0 as bigint)
               else greatest(b.total - b.paid_cents - b.credited_cents, 0) end as balance_due
        from base b
      ), shown as (
        select c.*,
          case when c.stored_status = 'draft' then 'draft'
               when c.kind = 'credit_note' then 'issued'
               when c.stored_status = 'void' then 'void'
               when c.balance_due = 0 then 'paid'
               when c.paid_cents + c.credited_cents > 0 then 'partial'
               else 'due' end as status,
          (c.kind = 'invoice' and c.stored_status = 'issued' and c.balance_due > 0
            and c.due_on < :today) as overdue,
          case when c.stored_status = 'draft' then 0 else 1 end as rank
        from calc c
      )
      select s.id, s.kind, s.number, s.status, s.overdue,
        case when s.overdue then :today - s.due_on end as overdue_days,
        s.patient_id, s.issue_date, s.due_on,
        l.session_count, l.first_on, l.last_on,
        case when l.session_count = 0 then fl.label end as label,
        s.total, s.balance_due,
        (select p.mode from payment p
          where p.invoice_id = s.id and p.kind = 'payment' and p.cancelled_at is null
          order by p.received_on desc, p.created_at desc, p.id desc limit 1) as last_payment_mode,
        (select cn.number from invoice cn
          where cn.cancels_invoice_id = s.id and cn.practitioner_id = :practitioner
          order by cn.issue_date desc, cn.number desc limit 1) as credit_note_number,
        ci.number as cancels_number
      from shown s
      left join lateral (
        select count(x.session_entry_id) as session_count, min(x.occurred_on) as first_on,
          max(x.occurred_on) as last_on
        from invoice_line x where x.invoice_id = s.id) l on true
      left join lateral (
        select y.label from invoice_line y where y.invoice_id = s.id
        order by y.position limit 1) fl on true
      left join invoice ci on ci.id = s.cancels_invoice_id and ci.practitioner_id = :practitioner
      where true
      """;

  private static final String ORDER = " order by s.rank, s.issue_date desc, s.id";

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional(readOnly = true)
  public List<InvoiceSummaryRow> summaries(UUID practitionerId, InvoiceListCriteria criteria) {
    StringBuilder sql = new StringBuilder(SUMMARY);
    Map<String, Object> params = new LinkedHashMap<>();
    if (criteria.status() != null) {
      sql.append(" and s.status = :status");
      params.put("status", criteria.status());
    }
    if (criteria.overdue() != null) {
      sql.append(" and s.overdue = :overdue");
      params.put("overdue", criteria.overdue());
    }
    if (criteria.kind() != null) {
      sql.append(" and s.kind = :kind");
      params.put("kind", criteria.kind());
    }
    if (criteria.patientId() != null) {
      sql.append(" and s.patient_id = :patient");
      params.put("patient", criteria.patientId());
    }
    if (criteria.from() != null) {
      sql.append(" and s.issue_date >= :from");
      params.put("from", criteria.from());
    }
    if (criteria.pattern() != null) {
      // Numéro et destinataire en SQL ; nom affiché du patient par la façade (identifiants).
      sql.append(" and (lower(s.number) like :pattern escape '!'")
          .append(" or lower(f_unaccent(coalesce(s.recipient_name, ''))) like :pattern escape '!'");
      if (!criteria.matchingPatientIds().isEmpty()) {
        sql.append(" or s.patient_id in (:matching)");
        params.put("matching", criteria.matchingPatientIds());
      }
      sql.append(")");
      params.put("pattern", criteria.pattern());
    }
    InvoiceCursor after = criteria.after();
    if (after != null) {
      sql.append(
          " and (s.rank > :cursorRank or (s.rank = :cursorRank and (s.issue_date < :cursorDate"
              + " or (s.issue_date = :cursorDate and s.id > :cursorId))))");
      params.put("cursorRank", after.rank());
      params.put("cursorDate", after.issueDate());
      params.put("cursorId", after.id());
    }
    sql.append(ORDER).append(" limit :limit");
    params.put("limit", criteria.limit() + 1);
    return run(sql.toString(), practitionerId, criteria.today(), criteria.draftVatRateBp(), params);
  }

  @Override
  @Transactional(readOnly = true)
  public List<InvoiceSummaryRow> summariesOf(
      UUID practitionerId, Collection<UUID> invoiceIds, LocalDate today, Integer draftVatRateBp) {
    if (invoiceIds.isEmpty()) {
      return List.of();
    }
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("ids", invoiceIds);
    return run(
        SUMMARY + " and s.id in (:ids)" + ORDER, practitionerId, today, draftVatRateBp, params);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> invoicedPatientIds(UUID practitionerId) {
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(
                "select distinct patient_id from invoice where practitioner_id = :practitioner")
            .setParameter("practitioner", practitionerId)
            .getResultList();
    return new LinkedHashSet<>(ids);
  }

  private List<InvoiceSummaryRow> run(
      String sql,
      UUID practitionerId,
      LocalDate today,
      Integer draftVatRateBp,
      Map<String, Object> params) {
    Query query =
        em.createNativeQuery(sql)
            .setParameter("practitioner", practitionerId)
            .setParameter("today", today)
            .setParameter("rate", draftVatRateBp);
    params.forEach(query::setParameter);
    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    List<InvoiceSummaryRow> summaries = new ArrayList<>(rows.size());
    for (Object[] r : rows) {
      summaries.add(
          new InvoiceSummaryRow(
              (UUID) r[0],
              (String) r[1],
              (String) r[2],
              (String) r[3],
              Boolean.TRUE.equals(r[4]),
              r[5] == null ? null : ((Number) r[5]).intValue(),
              (UUID) r[6],
              localDate(r[7]),
              localDate(r[8]),
              r[9] == null ? 0 : ((Number) r[9]).intValue(),
              localDate(r[10]),
              localDate(r[11]),
              (String) r[12],
              ((Number) r[13]).longValue(),
              ((Number) r[14]).longValue(),
              (String) r[15],
              (String) r[16],
              (String) r[17]));
    }
    return summaries;
  }

  private static LocalDate localDate(Object value) {
    return switch (value) {
      case null -> null;
      case LocalDate d -> d;
      case Date d -> d.toLocalDate();
      default -> LocalDate.parse(value.toString());
    };
  }
}
