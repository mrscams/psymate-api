package com.psymate.linking.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lien typé entre deux entrées d'un même patient (E3.7). Une ligne n'est jamais supprimée : elle
 * passe {@code superseded} (modification de type, suppression, archivage d'une entrée).
 *
 * @param supersededBy nouveau lien qui remplace celui-ci (modification de type), nul sinon
 * @param authorId praticien auteur, nul pour une suggestion de l'IA (V2)
 * @param decidedBy praticien qui a mis fin au lien, nul si c'est le système (archivage)
 */
public record Link(
    UUID id,
    UUID tenantId,
    UUID patientId,
    UUID sourceId,
    UUID targetId,
    String linkType,
    boolean directed,
    LinkProvenance provenance,
    LinkStatus status,
    String rationale,
    UUID authorId,
    UUID decidedBy,
    Instant decidedAt,
    UUID supersededBy,
    int version,
    Instant createdAt,
    Instant updatedAt) {

  /** Lien manuel accepté, tel qu'une création à la main le produit (A7 : [*] vers accepted). */
  public static Link manual(
      UUID tenantId,
      UUID patientId,
      UUID sourceId,
      UUID targetId,
      String linkType,
      boolean directed,
      String rationale,
      UUID authorId,
      Instant now) {
    return new Link(
        UUID.randomUUID(),
        tenantId,
        patientId,
        sourceId,
        targetId,
        linkType,
        directed,
        LinkProvenance.MANUAL,
        LinkStatus.ACCEPTED,
        rationale,
        authorId,
        null,
        null,
        null,
        1,
        now,
        now);
  }

  /** L'entrée est l'une des deux extrémités. */
  public boolean involves(UUID entryId) {
    return sourceId.equals(entryId) || targetId.equals(entryId);
  }

  /** L'autre extrémité du lien, vue depuis {@code entryId}. */
  public UUID otherEnd(UUID entryId) {
    return sourceId.equals(entryId) ? targetId : sourceId;
  }
}
