package com.psymate.directory;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lecture seule des profils publiés (practitioner_profile uniquement). */
@Repository
class PublicDirectoryRepository {

  /** Critères de recherche ; la profession est filtrée par le service (elle vit dans identity). */
  record Criteria(
      String q,
      String city,
      Double lat,
      Double lng,
      double radiusKm,
      List<String> modalities,
      List<String> audiences,
      List<String> languages,
      boolean accepting,
      String sort) {}

  record Row(
      UUID practitionerId,
      String slug,
      String displayName,
      String titleLabel,
      String photoAssetKey,
      String bioExcerpt,
      List<String> specialties,
      List<String> audiences,
      List<String> modalities,
      List<String> languages,
      String city,
      String availability,
      Double distanceKm) {}

  record Detail(
      UUID practitionerId,
      String slug,
      String displayName,
      String titleLabel,
      boolean showProfessionalNumber,
      String photoAssetKey,
      String bio,
      List<String> specialties,
      List<String> audiences,
      List<String> modalities,
      List<String> languages,
      String city,
      String postalCode,
      String country,
      String contactPhone,
      String contactEmail,
      String websiteUrl,
      String address,
      boolean showPhone,
      boolean showEmail,
      boolean showAddress,
      String responseDelay,
      String fees,
      String availability,
      LocalDate nextSlotAt,
      OffsetDateTime publishedAt) {}

  // geo : point(x = longitude, y = latitude) ; distance de grand cercle en km (haversine).
  private static final String DISTANCE =
      "(6371 * 2 * asin(sqrt(least(1, power(sin(radians((geo)[1] - :lat) / 2), 2)"
          + " + cos(radians(:lat)) * cos(radians((geo)[1]))"
          + " * power(sin(radians((geo)[0] - :lng) / 2), 2)))))";
  private static final String TSQUERY =
      "(plainto_tsquery('french', f_unaccent(:q)) || plainto_tsquery('simple', f_unaccent(:q)))";
  private static final String SIMILARITY = "similarity(f_unaccent(display_name), f_unaccent(:q))";

  /** Borne dure du lot traité en mémoire avant pagination (filtre profession, facettes). */
  static final int MAX_CANDIDATES = 500;

  private final JdbcClient jdbc;

  PublicDirectoryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  List<Row> search(Criteria c) {
    boolean geo = c.lat() != null && c.lng() != null;
    boolean hasQ = c.q() != null && !c.q().isBlank();
    StringBuilder sql =
        new StringBuilder(
            "select practitioner_id, slug, display_name, title_label, photo_asset_key,"
                + " left(bio, 201) as bio_excerpt, specialties, audiences, modalities, languages,"
                + " city, availability, ");
    sql.append(geo ? "case when geo is null then null else " + DISTANCE + " end" : "null::float8");
    sql.append(" as distance_km from practitioner_profile where published");
    List<Object[]> params = new ArrayList<>();
    if (hasQ) {
      sql.append(" and (search_vector @@ " + TSQUERY + " or " + SIMILARITY + " > 0.3)");
      params.add(new Object[] {"q", c.q().trim()});
    }
    if (geo) {
      sql.append(" and geo is not null and " + DISTANCE + " <= :radius");
      params.add(new Object[] {"radius", c.radiusKm()});
    } else if (c.city() != null && !c.city().isBlank()) {
      sql.append(" and lower(f_unaccent(city)) = lower(f_unaccent(:city))");
      params.add(new Object[] {"city", c.city().trim()});
    }
    if (geo) {
      params.add(new Object[] {"lat", c.lat()});
      params.add(new Object[] {"lng", c.lng()});
    }
    overlap(sql, params, "modalities", c.modalities());
    overlap(sql, params, "audiences", c.audiences());
    overlap(sql, params, "languages", c.languages());
    if (c.accepting()) {
      sql.append(" and availability = 'accepting'");
    }
    sql.append(" order by ");
    if ("distance".equals(c.sort()) && geo) {
      sql.append("distance_km asc nulls last, ");
    } else if ("relevance".equals(c.sort()) && hasQ) {
      sql.append("(ts_rank(search_vector, " + TSQUERY + ") + " + SIMILARITY + ") desc, ");
    } else if ("relevance".equals(c.sort())) {
      sql.append("published_at desc nulls last, ");
    }
    sql.append("lower(display_name), slug limit ").append(MAX_CANDIDATES);
    JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
    for (Object[] p : params) {
      spec = spec.param((String) p[0], p[1]);
    }
    return spec.query((rs, i) -> row(rs)).list();
  }

  private static void overlap(
      StringBuilder sql, List<Object[]> params, String column, List<String> values) {
    if (values != null && !values.isEmpty()) {
      sql.append(" and ").append(column).append(" && cast(:").append(column).append(" as text[])");
      params.add(new Object[] {column, values.toArray(String[]::new)});
    }
  }

  Optional<Detail> findPublished(String slug) {
    return jdbc.sql(
            "select practitioner_id, slug, display_name, title_label, show_professional_number,"
                + " photo_asset_key, bio, specialties, audiences, modalities, languages, city,"
                + " postal_code, country, contact_phone, contact_email, website_url, address,"
                + " show_phone, show_email, show_address, response_delay, fees, availability,"
                + " next_slot_at, published_at from practitioner_profile"
                + " where slug = ? and published")
        .param(slug)
        .query((rs, i) -> detail(rs))
        .optional();
  }

  private static Row row(ResultSet rs) throws SQLException {
    double d = rs.getDouble("distance_km");
    Double distance = rs.wasNull() ? null : Math.round(d * 10.0) / 10.0;
    return new Row(
        rs.getObject("practitioner_id", UUID.class),
        rs.getString("slug"),
        rs.getString("display_name"),
        rs.getString("title_label"),
        rs.getString("photo_asset_key"),
        rs.getString("bio_excerpt"),
        strings(rs, "specialties"),
        strings(rs, "audiences"),
        strings(rs, "modalities"),
        strings(rs, "languages"),
        rs.getString("city"),
        rs.getString("availability"),
        distance);
  }

  private static Detail detail(ResultSet rs) throws SQLException {
    Date next = rs.getDate("next_slot_at");
    return new Detail(
        rs.getObject("practitioner_id", UUID.class),
        rs.getString("slug"),
        rs.getString("display_name"),
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
        rs.getObject("published_at", OffsetDateTime.class));
  }

  private static List<String> strings(ResultSet rs, String col) throws SQLException {
    Array a = rs.getArray(col);
    return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
  }
}
