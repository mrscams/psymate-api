package com.psymate.vocabulary.repository;

import com.psymate.vocabulary.model.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code tag}. Interne au module ; le compteur d'une fusion de tags est natif. */
public interface TagRepository extends JpaRepository<Tag, UUID>, TagNativeQueries {

  @Query(
      "select t from Tag t where t.practitionerId = :p and t.archivedAt is null"
          + " order by t.normalizedLabel")
  List<Tag> findActive(@Param("p") UUID practitionerId);

  @Query(
      "select t from Tag t where t.practitionerId = :p and t.normalizedLabel = :n"
          + " and t.archivedAt is null")
  Optional<Tag> findActiveByNormalized(
      @Param("p") UUID practitionerId, @Param("n") String normalized);

  @Query("select t from Tag t where t.id = :id and t.practitionerId = :p and t.archivedAt is null")
  Optional<Tag> findActiveById(@Param("p") UUID practitionerId, @Param("id") UUID id);

  /** Tags actifs du praticien parmi {@code ids}, en une seule requête (pas de N+1). */
  @Query(
      "select t from Tag t where t.practitionerId = :p and t.id in :ids"
          + " and t.archivedAt is null")
  List<Tag> findActiveByIds(@Param("p") UUID practitionerId, @Param("ids") Collection<UUID> ids);

  /** Tags du praticien archivés par une fusion ({@code merged_into} renseigné). */
  @Query("select t from Tag t where t.practitionerId = :p and t.mergedInto is not null")
  List<Tag> findMerged(@Param("p") UUID practitionerId);
}
