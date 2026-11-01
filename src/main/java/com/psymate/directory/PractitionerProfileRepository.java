package com.psymate.directory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code practitioner_profile} ; la recherche publique reste en JdbcClient. */
interface PractitionerProfileRepository extends JpaRepository<PractitionerProfile, UUID> {

  /** Slug et état de publication, sans charger la fiche (façade ProfileFacade). */
  record Summary(String slug, boolean published) {}

  /** Praticien visé par une fiche publiée et adresse de contact de sa fiche (peut être nulle). */
  record Recipient(UUID practitionerId, String contactEmail) {}

  boolean existsBySlug(String slug);

  /**
   * Charge la fiche avec un verrou de ligne. Toute écriture (modification, photo, publication,
   * dépublication) passe par ici : deux publications ou dépublications concurrentes se sérialisent,
   * et la seconde voit l'état validé de la première (aucun second événement). Ne pas charger
   * l'entité avant cet appel dans la même transaction : l'état déjà chargé ne serait pas relu.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PractitionerProfile p where p.practitionerId = :id")
  Optional<PractitionerProfile> findForUpdate(@Param("id") UUID id);

  /**
   * Crée le profil vide s'il n'existe pas (concurrence : le premier gagne). Gardée native
   * (checklist §6) : {@code on conflict do nothing} et les DEFAULT du DDL plutôt qu'une exception
   * d'unicité au flush. Vide le contexte de persistance pour que la relecture voie la ligne.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "insert into practitioner_profile (practitioner_id, slug, display_name)"
              + " values (:practitionerId, :slug, :displayName) on conflict do nothing",
      nativeQuery = true)
  void insertIfAbsent(
      @Param("practitionerId") UUID practitionerId,
      @Param("slug") String slug,
      @Param("displayName") String displayName);

  @Query(
      "select new com.psymate.directory.PractitionerProfileRepository$Summary(p.slug, p.published)"
          + " from PractitionerProfile p where p.practitionerId = :id")
  Optional<Summary> findSummary(@Param("id") UUID id);

  /** Fiche publiée uniquement : un slug inconnu ou non publié donne un résultat vide. */
  @Query(
      "select new com.psymate.directory.PractitionerProfileRepository$Recipient("
          + "p.practitionerId, p.contactEmail) from PractitionerProfile p"
          + " where p.slug = :slug and p.published = true")
  Optional<Recipient> findPublishedRecipient(@Param("slug") String slug);
}
