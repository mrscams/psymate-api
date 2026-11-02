package com.psymate.clinical.persistence;

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
 * lignes qui ne sont pas déjà dans l'état voulu, et rend les entrées réellement modifiées.
 */
@Repository
public class RelabelRepository {

  /** Entrée dont l'émotion ou les tags viennent d'être réécrits. */
  public record RelabeledEntry(UUID entryId, UUID patientId) {}

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
   * Fait porter {@code current} aux ressentis issus de {@code source} : ceux qui portent encore la
   * source sans origine, et ceux dont l'origine est la source et l'émotion courante un code de son
   * historique de fusions (un ressenti corrigé à la main vers une autre émotion n'est pas touché).
   * L'origine vaut la source, ou NULL quand la source est de nouveau l'émotion courante.
   */
  public List<RelabeledEntry> relabelFeelings(String source, String current, Set<String> history) {
    String original = current.equals(source) ? null : source;
    return jdbc.sql(
            "with changed as ("
                + " update entry_feeling set emotion_code = :current,"
                + " original_emotion_code = cast(:original as text)"
                + " where ((original_emotion_code = :source and emotion_code = any(:history))"
                + " or (original_emotion_code is null and emotion_code = :source))"
                + " and (emotion_code <> :current"
                + " or original_emotion_code is distinct from cast(:original as text))"
                + " returning entry_id)"
                + " select e.id, e.patient_id from entry e join changed c on c.entry_id = e.id")
        .param("current", current)
        .param("original", original)
        .param("source", source)
        .param("history", history.toArray(new String[0]))
        .query(
            (rs, i) -> new RelabeledEntry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
        .list();
  }

  /**
   * Remplace chaque tag archivé par sa cible dans {@code entry_tag}, sans doublon : la cible est
   * ajoutée là où elle manque, puis la source retirée (suppression physique autorisée, E9).
   */
  public List<RelabeledEntry> relabelTags(Map<UUID, UUID> targetBySource) {
    if (targetBySource.isEmpty()) {
      return List.of();
    }
    List<UUID> sources = new ArrayList<>(targetBySource.keySet());
    UUID[] s = sources.toArray(new UUID[0]);
    UUID[] t = sources.stream().map(targetBySource::get).toArray(UUID[]::new);
    jdbc.sql(
            "insert into entry_tag (entry_id, tag_id)"
                + " select et.entry_id, m.t from entry_tag et"
                + " join unnest(cast(:s as uuid[]), cast(:t as uuid[])) as m(s, t)"
                + " on et.tag_id = m.s on conflict do nothing")
        .param("s", s)
        .param("t", t)
        .update();
    return jdbc.sql(
            "with removed as ("
                + " delete from entry_tag where tag_id = any(:s) returning entry_id)"
                + " select distinct e.id, e.patient_id from entry e"
                + " join removed r on r.entry_id = e.id")
        .param("s", s)
        .query(
            (rs, i) -> new RelabeledEntry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
        .list();
  }
}
