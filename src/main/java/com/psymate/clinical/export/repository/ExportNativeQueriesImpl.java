package com.psymate.clinical.export.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link ExportNativeQueries}. */
class ExportNativeQueriesImpl implements ExportNativeQueries {

  /**
   * Chronologique ; à début égal (une précision jour et une précision mois commencent toutes deux
   * au 1er à 00:00), la période la plus courte d'abord (une période en cours, occurred_to nul, en
   * dernier), puis l'ordre de saisie.
   */
  private static final String ORDER =
      " order by e.occurred_from, e.occurred_to nulls last, e.created_at, e.id";

  @PersistenceContext private EntityManager em;

  @Override
  public List<Tuple> exportableSessions(UUID patientId) {
    return tuples(
        em.createNativeQuery(
                "select e.id, e.occurred_from, e.occurred_to, e.occurred_precision,"
                    + " e.occurred_label, s.kind, s.modality, s.duration_min, v.title, v.body"
                    + " from entry e join entry_session s on s.entry_id = e.id"
                    + " left join entry_version v on v.id = e.current_version_id"
                    + " where e.patient_id = :p and e.type = 'session' and e.archived_at is null"
                    + " and e.visibility = 'clinical'"
                    + ORDER,
                Tuple.class)
            .setParameter("p", patientId));
  }

  @Override
  public List<Tuple> exportableEntries(UUID patientId) {
    return tuples(
        em.createNativeQuery(
                "select e.id, e.session_id, e.type, e.status, e.occurred_from, e.occurred_to,"
                    + " e.occurred_precision, e.occurred_label, v.title, v.body, f.emotion_code,"
                    + " f.intensity, m.molecule, m.dosage, m.frequency, m.period_from,"
                    + " m.period_to"
                    + " from entry e left join entry_version v on v.id = e.current_version_id"
                    + " left join entry_feeling f on f.entry_id = e.id"
                    + " left join entry_medication m on m.entry_id = e.id"
                    + " where e.patient_id = :p and e.type <> 'session' and e.archived_at is null"
                    + " and e.visibility = 'clinical'"
                    + ORDER,
                Tuple.class)
            .setParameter("p", patientId));
  }

  @Override
  public List<Tuple> exportableEntryTags(UUID patientId) {
    return tuples(
        em.createNativeQuery(
                "select t.entry_id, t.tag_id from entry_tag t join entry e on e.id = t.entry_id"
                    + " where e.patient_id = :p and e.archived_at is null"
                    + " and e.visibility = 'clinical'",
                Tuple.class)
            .setParameter("p", patientId));
  }

  @SuppressWarnings("unchecked")
  private static List<Tuple> tuples(Query query) {
    return (List<Tuple>) query.getResultList();
  }
}
