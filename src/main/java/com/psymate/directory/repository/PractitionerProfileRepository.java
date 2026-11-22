package com.psymate.directory.repository;

import com.psymate.directory.model.PractitionerProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Accès JPA à {@code practitioner_profile} ; la recherche publique reste en JdbcClient. */
public interface PractitionerProfileRepository extends JpaRepository<PractitionerProfile, UUID> {

  /** Praticien visé par une fiche publiée et adresse de contact de sa fiche (peut être nulle). */
  record Recipient(UUID practitionerId, String contactEmail) {}

  /** Identité publique minimale d'une fiche publiée (façade {@code PublishedProfileFacade}). */
  record PublishedSummary(UUID practitionerId, String slug, String displayName) {}

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
  @Transactional
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

  /** Fiche publiée uniquement : un slug inconnu ou non publié donne un résultat vide. */
  @Query(
      "select new com.psymate.directory.repository.PractitionerProfileRepository$Recipient("
          + "p.practitionerId, p.contactEmail) from PractitionerProfile p"
          + " where p.slug = :slug and p.published = true")
  Optional<Recipient> findPublishedRecipient(@Param("slug") String slug);

  /** Fiche publiée par slug ; un slug inconnu ou une fiche non publiée donne un résultat vide. */
  @Query(
      "select new com.psymate.directory.repository.PractitionerProfileRepository$PublishedSummary("
          + "p.practitionerId, p.slug, p.displayName) from PractitionerProfile p"
          + " where p.slug = :slug and p.published = true")
  Optional<PublishedSummary> findPublishedSummaryBySlug(@Param("slug") String slug);

  /** Fiche publiée d'un praticien ; vide s'il n'a pas de fiche ou si elle n'est pas publiée. */
  @Query(
      "select new com.psymate.directory.repository.PractitionerProfileRepository$PublishedSummary("
          + "p.practitionerId, p.slug, p.displayName) from PractitionerProfile p"
          + " where p.practitionerId = :id and p.published = true")
  Optional<PublishedSummary> findPublishedSummaryByPractitioner(@Param("id") UUID practitionerId);
}
