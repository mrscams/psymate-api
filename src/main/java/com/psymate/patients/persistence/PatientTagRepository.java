package com.psymate.patients.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientTagRepository
    extends JpaRepository<PatientTag, PatientTagId>, PatientTagNativeQueries {

  /** Retire les tags du patient absents de {@code tagIds} (liste non vide). */
  @Modifying(clearAutomatically = true)
  @Query("delete from PatientTag t where t.id.patientId = :patientId and t.id.tagId not in :tagIds")
  int deleteOthers(@Param("patientId") UUID patientId, @Param("tagIds") Collection<UUID> tagIds);

  @Modifying(clearAutomatically = true)
  @Query("delete from PatientTag t where t.id.patientId = :patientId")
  int deleteAllOf(@Param("patientId") UUID patientId);

  /**
   * Insertion idempotente, gardée native (checklist §6) : {@code on conflict do nothing} évite la
   * course entre deux PUT /tags concurrents qui ajoutent le même tag.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "insert into patient_tag (patient_id, tag_id) values (:patientId, :tagId)"
              + " on conflict do nothing",
      nativeQuery = true)
  void insertIgnoringDuplicate(@Param("patientId") UUID patientId, @Param("tagId") UUID tagId);

  /** Tags de plusieurs patients en une requete (pas de N+1). */
  List<PatientTag> findByIdPatientIdIn(Collection<UUID> patientIds);
}
