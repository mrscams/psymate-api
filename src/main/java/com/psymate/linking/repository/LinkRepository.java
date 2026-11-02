package com.psymate.linking.repository;

import com.psymate.linking.model.Link;
import com.psymate.linking.model.LinkProvenance;
import com.psymate.linking.model.LinkStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès SQL du module linking (JdbcClient) : table link. */
@Repository
public class LinkRepository {

  static final String COLS =
      "id, tenant_id, patient_id, source_id, target_id, link_type, directed, provenance, status,"
          + " rationale, author_id, decided_by, decided_at, superseded_by, version, created_at,"
          + " updated_at";

  private final JdbcClient jdbc;

  LinkRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static OffsetDateTime odt(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
    return o == null ? null : o.toInstant();
  }

  private static UUID uuid(ResultSet rs, String col) throws SQLException {
    return rs.getObject(col, UUID.class);
  }

  static Link link(ResultSet rs, int i) throws SQLException {
    return new Link(
        uuid(rs, "id"),
        uuid(rs, "tenant_id"),
        uuid(rs, "patient_id"),
        uuid(rs, "source_id"),
        uuid(rs, "target_id"),
        rs.getString("link_type"),
        rs.getBoolean("directed"),
        LinkProvenance.fromCode(rs.getString("provenance")),
        LinkStatus.fromCode(rs.getString("status")),
        rs.getString("rationale"),
        uuid(rs, "author_id"),
        uuid(rs, "decided_by"),
        instant(rs, "decided_at"),
        uuid(rs, "superseded_by"),
        rs.getInt("version"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  // ---- lecture

  public Optional<Link> find(UUID id) {
    return jdbc.sql("select " + COLS + " from link where id = :id")
        .param("id", id)
        .query(LinkRepository::link)
        .optional();
  }

  /** Verrouille la ligne pour la durée de la transaction (changement de type, suppression). */
  public Optional<Link> findForUpdate(UUID id) {
    return jdbc.sql("select " + COLS + " from link where id = :id for update")
        .param("id", id)
        .query(LinkRepository::link)
        .optional();
  }

  /**
   * Lien vivant (proposed ou accepted) entre ces deux entrées pour ce type. Avec {@code
   * bothDirections} (type non dirigé), le sens inverse compte aussi : A co_occurs B et B co_occurs
   * A sont le même lien.
   */
  public Optional<UUID> findActiveDuplicate(
      UUID sourceId, UUID targetId, String linkType, boolean bothDirections) {
    return jdbc.sql(
            "select id from link where link_type = :type and status in ('proposed','accepted')"
                + " and ((source_id = :s and target_id = :t)"
                + " or (:both and source_id = :t and target_id = :s)) order by created_at limit 1")
        .param("type", linkType)
        .param("s", sourceId)
        .param("t", targetId)
        .param("both", bothDirections)
        .query(UUID.class)
        .optional();
  }

  /** Liens acceptés dont l'entrée est source ou cible, du plus ancien au plus récent. */
  public List<Link> acceptedLinksOf(UUID entryId) {
    return jdbc.sql(
            "select "
                + COLS
                + " from link where status = 'accepted' and (source_id = :e or target_id = :e)"
                + " order by created_at, id")
        .param("e", entryId)
        .query(LinkRepository::link)
        .list();
  }

  /** Liens vivants (proposed ou accepted) de l'entrée, verrouillés, dans un ordre stable. */
  public List<Link> activeLinksOfForUpdate(UUID entryId) {
    return jdbc.sql(
            "select "
                + COLS
                + " from link where status in ('proposed','accepted')"
                + " and (source_id = :e or target_id = :e) order by created_at, id for update")
        .param("e", entryId)
        .query(LinkRepository::link)
        .list();
  }

  // ---- écriture

  public void insert(Link l) {
    jdbc.sql(
            "insert into link (id, tenant_id, patient_id, source_id, target_id, link_type,"
                + " directed, provenance, status, rationale, author_id, decided_by, decided_at,"
                + " superseded_by, version, created_at, updated_at) values (:id, :tenant,"
                + " :patient, :source, :target, :type, :directed, :provenance, :status,"
                + " :rationale, :author, :decidedBy, :decidedAt, :supersededBy, :version,"
                + " :createdAt, :updatedAt)")
        .param("id", l.id())
        .param("tenant", l.tenantId())
        .param("patient", l.patientId())
        .param("source", l.sourceId())
        .param("target", l.targetId())
        .param("type", l.linkType())
        .param("directed", l.directed())
        .param("provenance", l.provenance().code())
        .param("status", l.status().code())
        .param("rationale", l.rationale())
        .param("author", l.authorId())
        .param("decidedBy", l.decidedBy())
        .param("decidedAt", odt(l.decidedAt()))
        .param("supersededBy", l.supersededBy())
        .param("version", l.version())
        .param("createdAt", odt(l.createdAt()))
        .param("updatedAt", odt(l.updatedAt()))
        .update();
  }

  /**
   * Passe le lien en superseded (jamais de suppression physique). {@code supersededBy} est le
   * nouveau lien en cas de changement de type, nul pour une suppression ou un archivage ; {@code
   * decidedBy} est nul quand c'est le système (archivage d'une entrée).
   */
  public void supersede(UUID id, UUID supersededBy, UUID decidedBy, Instant now) {
    jdbc.sql(
            "update link set status = 'superseded', superseded_by = :by, decided_by = :decidedBy,"
                + " decided_at = :now, version = version + 1, updated_at = :now where id = :id")
        .param("id", id)
        .param("by", supersededBy)
        .param("decidedBy", decidedBy)
        .param("now", odt(now))
        .update();
  }
}
