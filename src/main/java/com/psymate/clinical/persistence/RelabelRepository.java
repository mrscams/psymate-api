package com.psymate.clinical.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Réécriture de {@code entry_feeling} et {@code entry_tag} après une fusion du vocabulaire (M9-03),
 * en JdbcClient comme le reste du module. Chaque méthode est idempotente : elle ne touche que les
 * lignes qui ne sont pas déjà dans l'état voulu, et rend les entrées réellement modifiées. Chaque
 * réécriture tient en une instruction, donc un seul instantané : une ligne écrite en parallèle est
 * soit traitée, soit laissée intacte, jamais à moitié.
 */
@Repository
public class RelabelRepository {

  /** Entrée dont l'émotion ou les tags viennent d'être réécrits. */
  public record RelabeledEntry(UUID entryId, UUID patientId) {}

  private static final String CHANGED_ENTRIES =
      " select distinct e.id, e.patient_id from entry e join changed c on c.entry_id = e.id";

  private final JdbcClient jdbc;

  RelabelRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Sérialise jusqu'au commit les réécritures d'un même praticien : l'état des fusions est relu
   * après le verrou, donc une réécriture ne peut pas en écraser une plus récente.
   */
  public void lockPractitioner(UUID practitionerId) {
    jdbc.sql("select pg_advisory_xact_lock(hashtext('clinical.vocabulary-merge'), hashtext(:p))")
        .param("p", practitionerId.toString())
        .query()
        .listOfRows();
  }

  /** Nombre de ressentis dont l'émotion courante est {@code emotionCode}. */
  public int countFeelings(String emotionCode) {
    return jdbc.sql("select count(*) from entry_feeling where emotion_code = :c")
        .param("c", emotionCode)
        .query(Integer.class)
        .single();
  }

  /**
   * Source fusionnée (cible courante {@code current} différente de {@code source}) : passent à la
   * cible les ressentis qui portent la source, quelle que soit leur origine (un ressenti corrigé à
   * la main vers la source suit la fusion, comme avant M9-03), et ceux dont l'origine est la source
   * et l'émotion un code de son historique (fusions en chaîne). L'origine déjà posée est gardée.
   */
  public List<RelabeledEntry> moveFeelings(String source, String current, Set<String> history) {
    return jdbc.sql(
            "with changed as ("
                + " update entry_feeling set emotion_code = :current,"
                + " original_emotion_code = coalesce(original_emotion_code, :source)"
                + " where (emotion_code = :source"
                + " or (original_emotion_code = :source and emotion_code = any(:history)))"
                + " and emotion_code <> :current"
                + " returning entry_id)"
                + CHANGED_ENTRIES)
        .param("current", current)
        .param("source", source)
        .param("history", history.toArray(new String[0]))
        .query(RelabelRepository::relabeled)
        .list();
  }

  /**
   * Source rendue à elle-même (toutes ses fusions annulées) : les ressentis dont l'origine est la
   * source et l'émotion un code de son historique la reprennent, sans origine. Un ressenti corrigé
   * à la main vers une émotion hors de l'historique n'est pas touché ; un ressenti corrigé vers un
   * code de l'historique est indiscernable d'un ressenti fusionné et revient à la source, comme
   * avant M9-03.
   */
  public List<RelabeledEntry> restoreFeelings(String source, Set<String> history) {
    return jdbc.sql(
            "with changed as ("
                + " update entry_feeling set emotion_code = :source, original_emotion_code = null"
                + " where original_emotion_code = :source and emotion_code = any(:history)"
                + " returning entry_id)"
                + CHANGED_ENTRIES)
        .param("source", source)
        .param("history", history.toArray(new String[0]))
        .query(RelabelRepository::relabeled)
        .list();
  }

  /**
   * Remplace chaque tag archivé par sa cible dans {@code entry_tag}, sans doublon : les lignes de
   * la source sont retirées (suppression physique autorisée, E9) et la cible posée sur leurs
   * entrées là où elle manque, dans la même instruction.
   */
  public List<RelabeledEntry> relabelTags(Map<UUID, UUID> targetBySource) {
    if (targetBySource.isEmpty()) {
      return List.of();
    }
    List<UUID> sources = new ArrayList<>(targetBySource.keySet());
    return jdbc.sql(
            "with m(s, t) as (select * from unnest(cast(:s as uuid[]), cast(:t as uuid[]))),"
                + " changed as (delete from entry_tag et using m where et.tag_id = m.s"
                + " returning et.entry_id, m.t),"
                + " added as (insert into entry_tag (entry_id, tag_id)"
                + " select distinct entry_id, t from changed on conflict do nothing)"
                + CHANGED_ENTRIES)
        .param("s", sources.toArray(new UUID[0]))
        .param("t", sources.stream().map(targetBySource::get).toArray(UUID[]::new))
        .query(RelabelRepository::relabeled)
        .list();
  }

  private static RelabeledEntry relabeled(ResultSet rs, int row) throws SQLException {
    return new RelabeledEntry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
  }
}
