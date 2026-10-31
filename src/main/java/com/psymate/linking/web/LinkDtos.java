package com.psymate.linking.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO REST du module linking (camelCase). La provenance et le statut ne s'écrivent jamais : en V1
 * un lien posé à la main est toujours manual et accepted.
 */
final class LinkDtos {

  private LinkDtos() {}

  /**
   * Corps de POST /api/entries/{id}/links. {@code direction} dit où se place l'entrée de l'URL :
   * outgoing (défaut), elle est la source et {@code targetId} la cible ; incoming, {@code targetId}
   * est la source et l'entrée de l'URL la cible.
   */
  record CreateLinkRequest(
      @NotNull UUID targetId,
      @NotBlank String linkType,
      @Pattern(regexp = "incoming|outgoing") String direction,
      @Size(max = 500) String rationale) {}

  /** Corps de PATCH /api/links/{id} : le nouveau type du lien. */
  record ChangeLinkTypeRequest(@NotBlank String linkType) {}

  /**
   * Lien tel que le client l'affiche. {@code label} est le verbe vu depuis l'entrée de référence
   * (le verbe inverse quand elle est la cible d'un lien dirigé) et {@code sentence} la phrase
   * lisible « entrée verbe autre entrée », dans la langue de la requête.
   */
  record LinkDto(
      UUID id,
      UUID sourceId,
      UUID targetId,
      String linkType,
      String label,
      String sentence,
      String provenance,
      String status,
      String rationale,
      Instant createdAt,
      UUID authorId) {}
}
