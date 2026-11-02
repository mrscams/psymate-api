package com.psymate.directory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Profil public d'un praticien (table {@code practitioner_profile}). Interne au module directory :
 * jamais exposé dans un DTO, un événement ni une classe {@code web} ou {@code api}.
 *
 * <p>Deux colonnes ne sont volontairement pas mappées. {@code search_vector} est écrite par le
 * trigger {@code profile_search_vector_before_write} à chaque INSERT et UPDATE Hibernate, et n'est
 * lue que par {@code PublicDirectoryRepository} (JdbcClient, plein texte). {@code geo} (point)
 * n'est jamais écrite par l'application (aucune géolocalisation en V1) et n'est lue que par ce même
 * dépôt. Une colonne non mappée est ignorée par {@code ddl-auto=validate} et n'est jamais touchée
 * par un UPDATE d'entité.
 *
 * <p>Toute écriture charge l'entité sous verrou ({@code
 * PractitionerProfileRepository.findForUpdate}) : deux publications concurrentes ne produisent
 * qu'un changement d'état, donc qu'un événement.
 */
@Entity
@Table(name = "practitioner_profile")
public class PractitionerProfile implements Persistable<UUID> {

  @Id private UUID practitionerId;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private boolean published;

  @Column(nullable = false)
  private String displayName;

  private String titleLabel;

  @Column(nullable = false)
  private boolean showProfessionalNumber;

  private String photoAssetKey;
  private String bio;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> specialties = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> audiences = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> modalities = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> languages = new ArrayList<>();

  private String city;
  private String postalCode;

  @Column(nullable = false)
  private String country = "FR";

  private String contactPhone;
  private String contactEmail;
  private String websiteUrl;
  private String address;

  @Column(nullable = false)
  private boolean showPhone;

  @Column(nullable = false)
  private boolean showEmail;

  @Column(nullable = false)
  private boolean showAddress;

  private String responseDelay;
  private String fees;

  @Column(nullable = false)
  private String availability = "unknown";

  private LocalDate nextSlotAt;
  private OffsetDateTime publishedAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  /**
   * Hibernate seul instancie cette entité : la création d'un profil passe toujours par la requête
   * native {@code PractitionerProfileRepository.insertIfAbsent} (le premier gagne, DEFAULT du DDL),
   * jamais par {@code save} d'une instance neuve.
   */
  protected PractitionerProfile() {}

  /**
   * Applique une modification de l'éditeur de profil, avec les normalisations de l'ancien UPDATE
   * SQL : espaces retirés, chaîne vide devenue null, pays vide devenu FR sinon en majuscules,
   * disponibilité absente devenue unknown, interrupteurs absents devenus faux, listes absentes
   * devenues vides. Le slug, l'état de publication et la photo ne changent pas.
   */
  public void applyEdit(ProfileEdit r, OffsetDateTime now) {
    this.displayName = r.displayName().trim();
    this.titleLabel = blankToNull(r.titleLabel());
    this.showProfessionalNumber = Boolean.TRUE.equals(r.showProfessionalNumber());
    this.bio = blankToNull(r.bio());
    this.specialties = new ArrayList<>(r.specialtiesOrEmpty());
    this.audiences = new ArrayList<>(r.audiencesOrEmpty());
    this.modalities = new ArrayList<>(r.modalitiesOrEmpty());
    this.languages = new ArrayList<>(r.languagesOrEmpty());
    this.city = blankToNull(r.city());
    this.postalCode = blankToNull(r.postalCode());
    this.country = r.country() == null || r.country().isBlank() ? "FR" : r.country().toUpperCase();
    this.contactPhone = blankToNull(r.contactPhone());
    this.contactEmail = blankToNull(r.contactEmail());
    this.websiteUrl = blankToNull(r.websiteUrl());
    this.address = blankToNull(r.address());
    this.showPhone = Boolean.TRUE.equals(r.showPhone());
    this.showEmail = Boolean.TRUE.equals(r.showEmail());
    this.showAddress = Boolean.TRUE.equals(r.showAddress());
    this.responseDelay = blankToNull(r.responseDelay());
    this.fees = blankToNull(r.fees());
    this.availability = r.availability() == null ? "unknown" : r.availability();
    this.nextSlotAt = r.nextSlotAt();
    this.updatedAt = now;
  }

  /** Remplace (ou retire, avec null) la clé de la photo. */
  public void setPhoto(String key, OffsetDateTime now) {
    this.photoAssetKey = key;
    this.updatedAt = now;
  }

  /** Publie le profil ; vrai seulement si l'état a changé (published_at = maintenant). */
  public boolean publish(OffsetDateTime now) {
    if (published) {
      return false;
    }
    this.published = true;
    this.publishedAt = now;
    this.updatedAt = now;
    return true;
  }

  /** Dépublie le profil ; vrai seulement si l'état a changé (published_at inchangé). */
  public boolean unpublish(OffsetDateTime now) {
    if (!published) {
      return false;
    }
    this.published = false;
    this.updatedAt = now;
    return true;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  @Override
  public UUID getId() {
    return practitionerId;
  }

  UUID getPractitionerId() {
    return practitionerId;
  }

  public String getSlug() {
    return slug;
  }

  public boolean isPublished() {
    return published;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getTitleLabel() {
    return titleLabel;
  }

  public boolean isShowProfessionalNumber() {
    return showProfessionalNumber;
  }

  public String getPhotoAssetKey() {
    return photoAssetKey;
  }

  public String getBio() {
    return bio;
  }

  public List<String> getSpecialties() {
    return specialties;
  }

  public List<String> getAudiences() {
    return audiences;
  }

  public List<String> getModalities() {
    return modalities;
  }

  public List<String> getLanguages() {
    return languages;
  }

  public String getCity() {
    return city;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCountry() {
    return country;
  }

  public String getContactPhone() {
    return contactPhone;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public String getWebsiteUrl() {
    return websiteUrl;
  }

  public String getAddress() {
    return address;
  }

  public boolean isShowPhone() {
    return showPhone;
  }

  public boolean isShowEmail() {
    return showEmail;
  }

  public boolean isShowAddress() {
    return showAddress;
  }

  public String getResponseDelay() {
    return responseDelay;
  }

  public String getFees() {
    return fees;
  }

  public String getAvailability() {
    return availability;
  }

  public LocalDate getNextSlotAt() {
    return nextSlotAt;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof PractitionerProfile other
            && practitionerId != null
            && practitionerId.equals(other.practitionerId));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(practitionerId);
  }
}
