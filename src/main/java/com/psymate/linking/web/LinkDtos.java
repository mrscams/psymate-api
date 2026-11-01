package com.psymate.linking.web;

import io.swagger.v3.oas.annotations.media.Schema;
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
  @Schema(
      description =
          "Lien à poser entre l'entrée de l'URL et une autre entrée du même patient. La provenance"
              + " (manual) et le statut (accepted) ne s'envoient pas.")
  record CreateLinkRequest(
      @Schema(
              description =
                  "Identifiant de l'autre entrée à lier ; elle doit être du même patient, non"
                      + " archivée et différente de l'entrée de l'URL.",
              example = "c1a7e9d4-2b3f-4a6c-8d5e-0f1a2b3c4d5e")
          @NotNull
          UUID targetId,
      @Schema(
              description =
                  "Code du type de lien, parmi ceux de GET /api/vocabulary/link-types (par exemple"
                      + " triggers, caused_by, worsens, relieves, co_occurs, follows, part_of,"
                      + " treats). Le type doit être autorisé pour le couple (type de l'entrée"
                      + " source, type de l'entrée cible).",
              example = "triggers")
          @NotBlank
          String linkType,
      @Schema(
              description =
                  "Place de l'entrée de l'URL dans le lien : outgoing = elle est la source et"
                      + " targetId la cible ; incoming = targetId est la source et elle est la"
                      + " cible. Défaut : outgoing.",
              allowableValues = {"outgoing", "incoming"},
              defaultValue = "outgoing")
          @Pattern(regexp = "incoming|outgoing")
          String direction,
      @Schema(
              description =
                  "Justification libre du lien, 500 caractères au plus ; espaces de début et de"
                      + " fin retirés, vide traité comme absent. Optionnelle.",
              maxLength = 500,
              example = "Depuis le décès, les réveils sont angoissés.")
          @Size(max = 500)
          String rationale) {}

  /** Corps de PATCH /api/links/{id} : le nouveau type du lien. */
  @Schema(description = "Nouveau type d'un lien existant ; les extrémités ne changent pas.")
  record ChangeLinkTypeRequest(
      @Schema(
              description =
                  "Code du nouveau type de lien, parmi ceux de GET /api/vocabulary/link-types ;"
                      + " il doit être autorisé pour le couple de types des deux entrées et"
                      + " différent du type actuel (sinon 409 LINK_EXISTS).",
              example = "worsens")
          @NotBlank
          String linkType) {}

  /**
   * Lien tel que le client l'affiche. {@code label} est le verbe vu depuis l'entrée de référence
   * (le verbe inverse quand elle est la cible d'un lien dirigé) et {@code sentence} la phrase
   * lisible « entrée verbe autre entrée », dans la langue de la requête.
   */
  @Schema(
      description =
          "Lien entre deux entrées d'un même patient, vu depuis une entrée de référence (l'entrée"
              + " de l'URL ; la source du lien pour un changement de type).")
  record LinkDto(
      @Schema(
              description = "Identifiant du lien.",
              example = "5d2f7a10-8c4b-4e6a-b1d3-7f9e0a2c4b68")
          UUID id,
      @Schema(
              description = "Identifiant de l'entrée source du lien.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID sourceId,
      @Schema(
              description = "Identifiant de l'entrée cible du lien.",
              example = "c1a7e9d4-2b3f-4a6c-8d5e-0f1a2b3c4d5e")
          UUID targetId,
      @Schema(
              description =
                  "Code du type de lien (triggers, caused_by, worsens, relieves, co_occurs,"
                      + " follows, part_of, treats). Toujours le code du sens source vers cible,"
                      + " jamais un type passif.",
              example = "triggers")
          String linkType,
      @Schema(
              description =
                  "Verbe du lien vu depuis l'entrée de référence, dans la langue de la requête :"
                      + " le libellé direct si elle est la source, le libellé inverse si elle est"
                      + " la cible d'un type dirigé (par exemple « déclenche » ou « déclenché"
                      + " par »).",
              example = "déclenche")
          String label,
      @Schema(
              description =
                  "Phrase lisible « entrée verbe autre entrée » vue depuis l'entrée de référence,"
                      + " dans la langue de la requête ; chaque entrée y est désignée par son"
                      + " titre, sinon par un extrait de 60 caractères de son texte, sinon par"
                      + " « une entrée ».",
              example = "Décès du père déclenche Peur au réveil")
          String sentence,
      @Schema(
              description =
                  "Origine du lien : manual (posé par le praticien), seule valeur produite en V1."
                      + " Valeurs réservées à l'IA (V2) : ai_suggested, ai_confirmed.",
              example = "manual")
          String provenance,
      @Schema(
              description =
                  "Statut du lien : accepted (lien actif), seule valeur rendue en V1. Valeurs"
                      + " réservées : proposed et rejected (suggestions de l'IA, V2), superseded"
                      + " (lien remplacé ou supprimé, jamais rendu par cette API).",
              example = "accepted")
          String status,
      @Schema(
              description = "Justification saisie à la pose du lien ; null si aucune.",
              example = "Depuis le décès, les réveils sont angoissés.")
          String rationale,
      @Schema(
              description = "Date de création du lien, ISO 8601 UTC.",
              example = "2026-10-12T14:30:00Z")
          Instant createdAt,
      @Schema(
              description =
                  "Identifiant du praticien qui a posé le lien ; null pour une suggestion de l'IA"
                      + " (V2, jamais en V1).",
              example = "7e0b3d92-4c1a-4f58-a6b7-9c8d7e6f5a4b")
          UUID authorId) {}
}
