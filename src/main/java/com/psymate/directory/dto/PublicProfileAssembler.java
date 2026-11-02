package com.psymate.directory.dto;

import com.psymate.directory.model.PublicProfileDetail;

/** Règles d'affichage des contacts (A4.10) : un champ non coché n'est jamais émis. */
public final class PublicProfileAssembler {

  static final int EXCERPT_LENGTH = 200;

  private PublicProfileAssembler() {}

  /**
   * Toujours null tant que la route /public/media/{key} n'existe pas (tâche médias) : la clé de
   * stockage brute n'est jamais exposée.
   */
  public static String photoUrl(String assetKey) {
    return null;
  }

  /** Coupe au dernier espace avant la limite et ajoute « … » ; texte court inchangé. */
  public static String excerpt(String bio) {
    if (bio == null || bio.length() <= EXCERPT_LENGTH) {
      return bio;
    }
    String cut = bio.substring(0, EXCERPT_LENGTH);
    int space = cut.lastIndexOf(' ');
    if (space > 0) {
      cut = cut.substring(0, space);
    }
    return cut.stripTrailing() + "…";
  }

  public static PublicProfile toPublic(
      PublicProfileDetail d, String profession, boolean verified, String professionalNumber) {
    return new PublicProfile(
        d.slug(),
        d.displayName(),
        profession,
        verified,
        d.titleLabel(),
        d.showProfessionalNumber() ? professionalNumber : null,
        photoUrl(d.photoAssetKey()),
        d.bio(),
        d.specialties(),
        d.audiences(),
        d.modalities(),
        d.languages(),
        d.city(),
        d.postalCode(),
        d.country(),
        d.showPhone() ? d.contactPhone() : null,
        d.showEmail() ? d.contactEmail() : null,
        d.showAddress() ? d.address() : null,
        d.websiteUrl(),
        d.responseDelay(),
        d.fees(),
        d.availability(),
        d.nextSlotAt(),
        d.publishedAt());
  }
}
