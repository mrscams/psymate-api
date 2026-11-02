package com.psymate.patients.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link PatientTagNativeQueries} (SQL natif). */
class PatientTagNativeQueriesImpl implements PatientTagNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public void lockPractitionerForTagMerge(UUID practitionerId) {
    em.createNativeQuery(
            "select count(*) from (select pg_advisory_xact_lock("
                + "hashtext('patients.vocabulary-merge'), hashtext(:p))) as locked")
        .setParameter("p", practitionerId.toString())
        .getSingleResult();
  }

  @Override
  public List<UUID> replaceTag(UUID source, UUID target) {
    List<?> rows =
        em.createNativeQuery(
                "with removed as (delete from patient_tag where tag_id = :s"
                    + " returning patient_id, added_at),"
                    + " added as (insert into patient_tag (patient_id, tag_id, added_at)"
                    + " select patient_id, :t, added_at from removed on conflict do nothing)"
                    + " select patient_id from removed")
            .setParameter("s", source)
            .setParameter("t", target)
            .getResultList();
    // Les lignes de patient_tag ont changé en SQL : aucune entité ne doit garder l'ancien état.
    em.clear();
    return rows.stream()
        .map(o -> o instanceof UUID u ? u : UUID.fromString(String.valueOf(o)))
        .toList();
  }
}
