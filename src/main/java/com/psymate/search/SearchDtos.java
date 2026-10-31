package com.psymate.search;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Corps de la réponse de recherche (noms préfixés EntrySearch pour le contrat OpenAPI). */
final class SearchDtos {

  private SearchDtos() {}

  @Schema(description = "Requête telle que le serveur l'a interprétée à partir du paramètre q.")
  record EntrySearchQueryDto(
      @Schema(description = "Texte libre restant après retrait des jetons (tag:, émotion:, ...)")
          String text,
      @Schema(description = "Valeurs des jetons tag:") List<String> tags,
      @Schema(description = "Valeurs des jetons émotion: / emotion:") List<String> emotions,
      @Schema(description = "Borne exclusive de avant: / before: (UTC), ou null") Instant before,
      @Schema(description = "Borne inclusive déduite de après: / after: (UTC), ou null")
          Instant after) {}

  @Schema(description = "Une entrée trouvée. title et snippet sont des fragments HTML échappés.")
  record EntrySearchItemDto(
      UUID entryId,
      @Schema(description = "Code du type d'entrée (note, event, situation, feeling, ...)")
          String type,
      @Schema(
              description =
                  "Titre en HTML échappé, mots trouvés entre mark si la recherche a un texte")
          String title,
      @Schema(
              description =
                  "Extrait du corps en HTML échappé avec mark ; null si l'entrée n'a pas de corps")
          String snippet,
      Instant occurredFrom,
      Instant occurredTo,
      String occurredPrecision,
      UUID sessionId,
      @Schema(description = "private ou clinical") String visibility,
      Instant archivedAt,
      @Schema(description = "Version courante de l'entrée") int version,
      @Schema(description = "Libellés des tags") List<String> tags,
      @Schema(description = "Code de l'émotion pour un ressenti, sinon null") String emotionCode,
      @Schema(
              description =
                  "Pertinence (ts_rank_cd, multipliée par 1,5 sous 90 jours) ; null pour un"
                      + " résultat approché ou sans texte")
          Double rank,
      @Schema(description = "Similarité trigramme (0 à 1) ; null pour un résultat exact")
          Double similarity,
      @Schema(description = "true pour un résultat approché (tolérance aux fautes)")
          boolean approximate) {}

  record EntrySearchTypeFacetDto(String code, int count) {}

  record EntrySearchEmotionFacetDto(String code, String label, int count) {}

  record EntrySearchTagFacetDto(UUID id, String label, int count) {}

  @Schema(
      description =
          "Compteurs sur les résultats exacts ; chaque facette ignore son propre filtre pour que"
              + " les puces gardent leur compteur.")
  record EntrySearchFacetsDto(
      List<EntrySearchTypeFacetDto> types,
      List<EntrySearchEmotionFacetDto> emotions,
      List<EntrySearchTagFacetDto> tags) {}

  record EntrySearchResultDto(
      EntrySearchQueryDto query,
      @Schema(description = "Numéro de page, à partir de 1") int page,
      @Schema(description = "Taille de page (20)") int pageSize,
      @Schema(description = "Nombre total de résultats exacts, hors approchés") long total,
      @Schema(description = "Résultats exacts dans items") int exactCount,
      @Schema(description = "Résultats approchés dans items (page 1 seulement)")
          int approximateCount,
      @Schema(description = "Exacts d'abord, puis approchés") List<EntrySearchItemDto> items,
      EntrySearchFacetsDto facets,
      @Schema(description = "Durée de traitement côté serveur, en millisecondes") long tookMs) {}
}
