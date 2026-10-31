package com.psymate.directory;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès SQL du module directory (practitioner_profile, directory_vocabulary). */
@Repository
class ProfileRepository {

  record VocabularyRow(String code, String labelFr, String labelEn) {}

  private static final String COLS =
      "slug, published, display_name, title_label, show_professional_number, photo_asset_key, bio,"
          + " specialties, audiences, modalities, languages, city, postal_code, country,"
          + " contact_phone, contact_email, website_url, address, show_phone, show_email,"
          + " show_address, response_delay, fees, availability, next_slot_at, published_at,"
          + " updated_at";

  private final JdbcClient jdbc;

  ProfileRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<ProfileDto> find(UUID practitionerId, String profession) {
    return jdbc.sql("select " + COLS + " from practitioner_profile where practitioner_id = ?")
        .param(practitionerId)
        .query((rs, i) -> map(rs, profession))
        .optional();
  }

  Optional<String> findSlug(UUID practitionerId) {
    return jdbc.sql("select slug from practitioner_profile where practitioner_id = ?")
        .param(practitionerId)
        .query(String.class)
        .optional();
  }

  boolean slugExists(String slug) {
    return jdbc.sql("select exists(select 1 from practitioner_profile where slug = ?)")
        .param(slug)
        .query(Boolean.class)
        .single();
  }

  /** Crée le profil vide s'il n'existe pas (concurrence : le premier gagne). */
  void insertIfAbsent(UUID practitionerId, String slug, String displayName) {
    jdbc.sql(
            "insert into practitioner_profile (practitioner_id, slug, display_name)"
                + " values (?, ?, ?) on conflict do nothing")
        .param(practitionerId)
        .param(slug)
        .param(displayName)
        .update();
  }

  void update(UUID practitionerId, UpdateProfileRequest r) {
    jdbc.sql(
            "update practitioner_profile set display_name = ?, title_label = ?,"
                + " show_professional_number = ?, bio = ?, specialties = ?, audiences = ?,"
                + " modalities = ?, languages = ?, city = ?, postal_code = ?, country = ?,"
                + " contact_phone = ?, contact_email = ?, website_url = ?, address = ?,"
                + " show_phone = ?, show_email = ?, show_address = ?, response_delay = ?,"
                + " fees = ?, availability = ?, next_slot_at = ?, updated_at = now()"
                + " where practitioner_id = ?")
        .param(r.displayName().trim())
        .param(blankToNull(r.titleLabel()))
        .param(Boolean.TRUE.equals(r.showProfessionalNumber()))
        .param(blankToNull(r.bio()))
        .param(r.specialtiesOrEmpty().toArray(String[]::new))
        .param(r.audiencesOrEmpty().toArray(String[]::new))
        .param(r.modalitiesOrEmpty().toArray(String[]::new))
        .param(r.languagesOrEmpty().toArray(String[]::new))
        .param(blankToNull(r.city()))
        .param(blankToNull(r.postalCode()))
        .param(r.country() == null || r.country().isBlank() ? "FR" : r.country().toUpperCase())
        .param(blankToNull(r.contactPhone()))
        .param(blankToNull(r.contactEmail()))
        .param(blankToNull(r.websiteUrl()))
        .param(blankToNull(r.address()))
        .param(Boolean.TRUE.equals(r.showPhone()))
        .param(Boolean.TRUE.equals(r.showEmail()))
        .param(Boolean.TRUE.equals(r.showAddress()))
        .param(blankToNull(r.responseDelay()))
        .param(blankToNull(r.fees()))
        .param(r.availability() == null ? "unknown" : r.availability())
        .param(r.nextSlotAt())
        .param(practitionerId)
        .update();
  }

  void setPhoto(UUID practitionerId, String key) {
    jdbc.sql(
            "update practitioner_profile set photo_asset_key = ?, updated_at = now()"
                + " where practitioner_id = ?")
        .param(key)
        .param(practitionerId)
        .update();
  }

  /**
   * Publie (published_at = maintenant) ou dépublie (published_at inchangé). Renvoie vrai si l'état
   * a changé.
   */
  boolean setPublished(UUID practitionerId, boolean published) {
    String sql =
        published
            ? "update practitioner_profile set published = true, published_at = now(),"
                + " updated_at = now() where practitioner_id = ? and not published"
            : "update practitioner_profile set published = false, updated_at = now()"
                + " where practitioner_id = ? and published";
    return jdbc.sql(sql).param(practitionerId).update() > 0;
  }

  Set<String> activeCodes(String kind) {
    return new HashSet<>(
        jdbc.sql("select code from directory_vocabulary where kind = ? and active")
            .param(kind)
            .query(String.class)
            .list());
  }

  List<VocabularyRow> activeVocabulary(String kind) {
    return jdbc.sql(
            "select code, label_fr, label_en from directory_vocabulary"
                + " where kind = ? and active order by sort_order, label_fr")
        .param(kind)
        .query((rs, i) -> new VocabularyRow(rs.getString(1), rs.getString(2), rs.getString(3)))
        .list();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static List<String> strings(ResultSet rs, String col) throws SQLException {
    Array a = rs.getArray(col);
    return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
  }

  private static ProfileDto map(ResultSet rs, String profession) throws SQLException {
    Date next = rs.getDate("next_slot_at");
    return new ProfileDto(
        rs.getString("slug"),
        rs.getBoolean("published"),
        rs.getString("display_name"),
        profession,
        rs.getString("title_label"),
        rs.getBoolean("show_professional_number"),
        rs.getString("photo_asset_key"),
        rs.getString("bio"),
        strings(rs, "specialties"),
        strings(rs, "audiences"),
        strings(rs, "modalities"),
        strings(rs, "languages"),
        rs.getString("city"),
        rs.getString("postal_code"),
        rs.getString("country"),
        rs.getString("contact_phone"),
        rs.getString("contact_email"),
        rs.getString("website_url"),
        rs.getString("address"),
        rs.getBoolean("show_phone"),
        rs.getBoolean("show_email"),
        rs.getBoolean("show_address"),
        rs.getString("response_delay"),
        rs.getString("fees"),
        rs.getString("availability"),
        next == null ? null : next.toLocalDate(),
        rs.getObject("published_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }
}
