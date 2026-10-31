package com.psymate.directory;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès SQL à contact_request (trace technique 30 jours) et au destinataire d'une fiche publiée.
 * Jamais de lien vers un dossier patient.
 */
@Repository
class ContactRequestRepository {

  /** Praticien visé par une fiche publiée et adresse de contact de sa fiche (peut être nulle). */
  record Recipient(UUID practitionerId, String contactEmail) {}

  record NewRequest(
      UUID id,
      UUID practitionerId,
      String senderName,
      String senderEmail,
      String subject,
      String message,
      OffsetDateTime consentAt,
      String ipHash,
      OffsetDateTime createdAt,
      OffsetDateTime purgeAfter) {}

  private final JdbcClient jdbc;

  ContactRequestRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Fiche publiée uniquement : un slug inconnu ou non publié donne un résultat vide. */
  Optional<Recipient> findPublishedRecipient(String slug) {
    return jdbc.sql(
            "select practitioner_id, contact_email from practitioner_profile"
                + " where slug = ? and published")
        .param(slug)
        .query(
            (rs, i) ->
                new Recipient(
                    rs.getObject("practitioner_id", UUID.class), rs.getString("contact_email")))
        .optional();
  }

  void insert(NewRequest r) {
    jdbc.sql(
            "insert into contact_request (id, practitioner_id, sender_name, sender_email, subject,"
                + " message, consent_at, ip_hash, created_at, purge_after)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        .param(r.id())
        .param(r.practitionerId())
        .param(r.senderName())
        .param(r.senderEmail())
        .param(r.subject())
        .param(r.message())
        .param(r.consentAt())
        .param(r.ipHash())
        .param(r.createdAt())
        .param(r.purgeAfter())
        .update();
  }

  void markRelayed(UUID id, OffsetDateTime at) {
    jdbc.sql("update contact_request set relayed_at = ?, relay_error = null where id = ?")
        .param(at)
        .param(id)
        .update();
  }

  void markRelayFailed(UUID id, String error) {
    jdbc.sql("update contact_request set relay_error = ? where id = ?")
        .param(error)
        .param(id)
        .update();
  }

  /** DELETE physique voulu (E2 : table technique) des lignes dont purge_after est dépassé. */
  int deleteDue() {
    return jdbc.sql("delete from contact_request where purge_after < now()").update();
  }
}
