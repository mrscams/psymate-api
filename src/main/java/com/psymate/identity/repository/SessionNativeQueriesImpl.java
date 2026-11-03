package com.psymate.identity.repository;

import com.psymate.identity.model.SessionAttributes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link SessionNativeQueries}. */
class SessionNativeQueriesImpl implements SessionNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public int deleteSessions(UUID practitionerId) {
    return em.createNativeQuery("delete from spring_session where principal_name = :p")
        .setParameter("p", practitionerId.toString())
        .executeUpdate();
  }

  @Override
  @Transactional
  public int deleteOtherSessions(UUID practitionerId, String currentSessionId) {
    return em.createNativeQuery(
            "delete from spring_session where principal_name = :p and session_id <> :s")
        .setParameter("p", practitionerId.toString())
        .setParameter("s", currentSessionId)
        .executeUpdate();
  }

  @Override
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<ActiveSessionRow> findActiveSessions(UUID practitionerId, Instant now) {
    // Les deux attributs PsyMate sont joints à la ligne (une seule requête) ; Spring Session les
    // sérialise en JDK dans attribute_bytes, décodés par stringAttribute.
    List<Object[]> rows =
        em.createNativeQuery(
                "select s.primary_id, s.session_id, s.creation_time, s.last_access_time,"
                    + " s.expiry_time, d.attribute_bytes, o.attribute_bytes"
                    + " from spring_session s"
                    + " left join spring_session_attributes d on d.session_primary_id ="
                    + " s.primary_id and d.attribute_name = :device"
                    + " left join spring_session_attributes o on o.session_primary_id ="
                    + " s.primary_id and o.attribute_name = :openedAt"
                    + " where s.principal_name = :p and s.expiry_time > :now"
                    + " order by s.last_access_time desc, s.creation_time desc")
            .setParameter("p", practitionerId.toString())
            .setParameter("now", now.toEpochMilli())
            .setParameter("device", SessionAttributes.DEVICE)
            .setParameter("openedAt", SessionAttributes.OPENED_AT)
            .getResultList();
    return rows.stream()
        .map(
            r ->
                new ActiveSessionRow(
                    ((String) r[0]).trim(),
                    ((String) r[1]).trim(),
                    Instant.ofEpochMilli(((Number) r[2]).longValue()),
                    Instant.ofEpochMilli(((Number) r[3]).longValue()),
                    Instant.ofEpochMilli(((Number) r[4]).longValue()),
                    stringAttribute((byte[]) r[5]),
                    instantAttribute((byte[]) r[6])))
        .toList();
  }

  @Override
  @Transactional
  public int deleteSessionByPrimaryId(UUID practitionerId, String primaryId) {
    return em.createNativeQuery(
            "delete from spring_session where principal_name = :p and primary_id = :id")
        .setParameter("p", practitionerId.toString())
        .setParameter("id", primaryId)
        .executeUpdate();
  }

  /**
   * Filtre des attributs lus : une chaîne seule (profondeur 2, 4 références, 4 Ko : nos deux
   * attributs font moins de 100 octets), aucun tableau (même vide ou de primitifs), aucune autre
   * classe. Construit une fois.
   */
  static final ObjectInputFilter STRING_ONLY =
      ObjectInputFilter.Config.createFilter(
          "maxdepth=2;maxrefs=4;maxbytes=4096;maxarray=0;java.lang.String;!*");

  /**
   * Taille maximale des octets lus. Le filtre JDK ne vérifie pas {@code maxbytes} pour une simple
   * chaîne : la limite est donc aussi posée ici, avant toute lecture.
   */
  private static final int MAX_ATTRIBUTE_BYTES = 4_096;

  /**
   * Chaîne sérialisée par Spring Session (sérialisation JDK). {@link #STRING_ONLY} refuse toute
   * autre classe avant qu'elle soit instanciée. null si l'attribut est absent ou illisible.
   */
  static String stringAttribute(byte[] bytes) {
    if (bytes == null || bytes.length > MAX_ATTRIBUTE_BYTES) {
      return null;
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      in.setObjectInputFilter(STRING_ONLY);
      return in.readObject() instanceof String value ? value : null;
    } catch (IOException | ClassNotFoundException | RuntimeException e) {
      return null;
    }
  }

  private static Instant instantAttribute(byte[] bytes) {
    String value = stringAttribute(bytes);
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
