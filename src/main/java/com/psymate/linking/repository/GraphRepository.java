package com.psymate.linking.repository;

import com.psymate.linking.model.Link;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Requêtes de graphe (A4.4) sur la seule table link : voisinage par CTE récursive, degrés, liens
 * entre des nœuds. Aucune lecture d'entry : les entrées se lisent par la façade de clinical. Tous
 * les prédicats portent le littéral {@code status = 'accepted'} pour que les index partiels
 * ix_link_source et ix_link_target servent.
 */
@Repository
public class GraphRepository {

  private static final String[] NO_TYPES = new String[0];

  /**
   * Les identifiants passés en un seul paramètre tableau, lu par les sous-requêtes. Un tableau cité
   * deux fois dans le SQL serait envoyé deux fois au serveur ; au-delà d'un paquet, la seconde
   * écriture attend l'accusé de réception de la première (délai de 40 ms sur certaines piles TCP).
   */
  private static final String WITH_IDS = "WITH ids AS (SELECT unnest(CAST(:ids AS uuid[])) AS id) ";

  private final JdbcClient jdbc;

  GraphRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static UUID[] array(Collection<UUID> ids) {
    return ids.toArray(new UUID[0]);
  }

  private static String[] typesOf(List<String> linkTypes) {
    return linkTypes == null || linkTypes.isEmpty() ? NO_TYPES : linkTypes.toArray(new String[0]);
  }

  /**
   * Voisinage de {@code rootId} jusqu'à {@code maxDepth} sauts, en suivant les liens acceptés dans
   * les deux sens ; les types de {@code linkTypes} (tous si nul ou vide) sont les seuls suivis. CTE
   * récursive sur les nœuds, bornée par la profondeur : l'UNION écarte les lignes (nœud,
   * profondeur) déjà vues, donc un cycle (part_of réciproque) ne boucle pas.
   *
   * <p>Seuls les liens du patient sont suivis : par construction un lien relie deux entrées d'un
   * même patient, le prédicat est une défense en profondeur.
   *
   * @return identifiant du nœud vers sa distance minimale à la racine (la racine vaut 0)
   */
  public Map<UUID, Integer> neighborhood(
      UUID patientId, UUID rootId, int maxDepth, List<String> linkTypes) {
    String[] types = typesOf(linkTypes);
    Map<UUID, Integer> out = new HashMap<>();
    jdbc.sql(
            "WITH RECURSIVE hood(id, depth) AS ("
                + " SELECT CAST(:root AS uuid), 0"
                + " UNION"
                + " SELECT CASE WHEN l.source_id = h.id THEN l.target_id ELSE l.source_id END,"
                + " h.depth + 1"
                + " FROM hood h JOIN link l ON l.status = 'accepted' AND l.patient_id = :patient"
                + " AND (l.source_id = h.id OR l.target_id = h.id)"
                + " AND (:noTypeFilter OR l.link_type = ANY(:types))"
                + " WHERE h.depth < :maxDepth)"
                + " SELECT id, min(depth) AS distance FROM hood GROUP BY id")
        .param("patient", patientId)
        .param("root", rootId)
        .param("noTypeFilter", types.length == 0)
        .param("types", types)
        .param("maxDepth", maxDepth)
        .query((rs, i) -> Map.entry(rs.getObject("id", UUID.class), rs.getInt("distance")))
        .list()
        .forEach(e -> out.put(e.getKey(), e.getValue()));
    return out;
  }

  /**
   * Les {@code limit} entrées (source ou cible) les plus liées parmi les liens acceptés du patient,
   * des types de {@code linkTypes} (tous si nul ou vide), par nombre de liens décroissant puis
   * identifiant. Les entrées archivées ne sont pas connues ici : l'appelant les retire ensuite.
   */
  public List<UUID> mostLinkedEntries(UUID patientId, int limit, List<String> linkTypes) {
    String[] types = typesOf(linkTypes);
    return jdbc.sql(
            "SELECT e FROM ("
                + " SELECT source_id AS e FROM link WHERE patient_id = :p AND status = 'accepted'"
                + " AND (:noTypeFilter OR link_type = ANY(:types))"
                + " UNION ALL"
                + " SELECT target_id AS e FROM link WHERE patient_id = :p AND status = 'accepted'"
                + " AND (:noTypeFilter OR link_type = ANY(:types))"
                + ") t GROUP BY e ORDER BY count(*) DESC, e LIMIT :limit")
        .param("p", patientId)
        .param("noTypeFilter", types.length == 0)
        .param("types", types)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  /**
   * Liens acceptés dont les deux extrémités sont dans {@code ids}, filtrés par type si {@code
   * linkTypes} est fourni, du plus ancien au plus récent.
   */
  public List<Link> acceptedEdgesAmong(Collection<UUID> ids, List<String> linkTypes) {
    if (ids.isEmpty()) {
      return List.of();
    }
    String[] types = typesOf(linkTypes);
    return jdbc.sql(
            WITH_IDS
                + "SELECT "
                + LinkRepository.COLS
                + " FROM link WHERE status = 'accepted'"
                + " AND source_id IN (SELECT id FROM ids) AND target_id IN (SELECT id FROM ids)"
                + " AND (:noTypeFilter OR link_type = ANY(:types))"
                + " ORDER BY created_at, id")
        .param("ids", array(ids))
        .param("noTypeFilter", types.length == 0)
        .param("types", types)
        .query(LinkRepository::link)
        .list();
  }

  /** Liens acceptés dont au moins une extrémité est dans {@code ids}. */
  public List<Link> acceptedEdgesTouching(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            WITH_IDS
                + "SELECT "
                + LinkRepository.COLS
                + " FROM link WHERE status = 'accepted' AND source_id IN (SELECT id FROM ids)"
                + " UNION"
                + " SELECT "
                + LinkRepository.COLS
                + " FROM link WHERE status = 'accepted' AND target_id IN (SELECT id FROM ids)"
                + " ORDER BY created_at, id")
        .param("ids", array(ids))
        .query(LinkRepository::link)
        .list();
  }

  /**
   * Nombre de liens acceptés incidents à chaque entrée, en une requête. Une entrée sans lien est
   * absente de la map.
   */
  public Map<UUID, Integer> acceptedDegrees(Collection<UUID> ids) {
    Map<UUID, Integer> out = new HashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    jdbc.sql(
            WITH_IDS
                + "SELECT e, count(*) AS degree FROM ("
                + " SELECT source_id AS e FROM link WHERE status = 'accepted'"
                + " AND source_id IN (SELECT id FROM ids)"
                + " UNION ALL"
                + " SELECT target_id AS e FROM link WHERE status = 'accepted'"
                + " AND target_id IN (SELECT id FROM ids)"
                + ") t GROUP BY e")
        .param("ids", array(ids))
        .query((rs, i) -> Map.entry(rs.getObject("e", UUID.class), rs.getInt("degree")))
        .list()
        .forEach(e -> out.put(e.getKey(), e.getValue()));
    return out;
  }

  /**
   * Entrées reliées au hub par un lien accepté et dont le degré accepté est exactement 1 (leur seul
   * lien est celui du hub) : les membres des super-nœuds du hub.
   */
  public List<UUID> degreeOneNeighborsOf(UUID hubId) {
    return jdbc.sql(
            "SELECT n.id FROM ("
                + " SELECT DISTINCT CASE WHEN source_id = :hub THEN target_id ELSE source_id END"
                + " AS id FROM link WHERE status = 'accepted'"
                + " AND (source_id = :hub OR target_id = :hub)) n"
                + " WHERE (SELECT count(*) FROM link x WHERE x.status = 'accepted'"
                + " AND (x.source_id = n.id OR x.target_id = n.id)) = 1"
                + " ORDER BY n.id")
        .param("hub", hubId)
        .query(UUID.class)
        .list();
  }
}
