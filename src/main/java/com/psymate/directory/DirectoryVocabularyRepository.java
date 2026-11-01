package com.psymate.directory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code directory_vocabulary} ; les comptes d'usage sont dans le fragment natif. */
interface DirectoryVocabularyRepository
    extends JpaRepository<DirectoryVocabulary, DirectoryVocabularyId>,
        DirectoryVocabularyUsageQueries {

  /** Entrée active proposée dans le profil (code, libellés), triée pour l'affichage. */
  record ActiveItem(String code, String labelFr, String labelEn) {}

  @Query("select v.id.code from DirectoryVocabulary v where v.id.kind = :kind and v.active = true")
  Set<String> activeCodes(@Param("kind") String kind);

  @Query(
      "select new com.psymate.directory.DirectoryVocabularyRepository$ActiveItem("
          + "v.id.code, v.labelFr, v.labelEn) from DirectoryVocabulary v"
          + " where v.id.kind = :kind and v.active = true order by v.sortOrder, v.labelFr")
  List<ActiveItem> activeItems(@Param("kind") String kind);

  /** Verrouille l'entrée le temps de la mise à jour. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from DirectoryVocabulary v where v.id.kind = :kind and v.id.code = :code")
  Optional<DirectoryVocabulary> findForUpdate(
      @Param("kind") String kind, @Param("code") String code);

  /** Rang suivant le plus grand du type (1 si le type est vide). */
  @Query(
      "select coalesce(max(v.sortOrder), 0) + 1 from DirectoryVocabulary v"
          + " where v.id.kind = :kind")
  int nextSortOrder(@Param("kind") String kind);

  /** Liste d'administration, les deux types : par type, rang puis libellé français. */
  List<DirectoryVocabulary> findAllByOrderByIdKindAscSortOrderAscLabelFrAsc();

  /** Liste d'administration d'un type : par rang puis libellé français. */
  List<DirectoryVocabulary> findByIdKindOrderBySortOrderAscLabelFrAsc(String kind);
}
