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
      @Schema(
              description =
                  "Texte libre restant après retrait des jetons (tag:, émotion:, avant:, après:) ;"
                      + " chaîne vide s'il n'en reste pas (recherche sans texte).",
              example = "licenciement")
          String text,
      @Schema(description = "Valeurs des jetons tag: de q (hors paramètre tags).")
          List<String> tags,
      @Schema(
              description =
                  "Valeurs des jetons émotion: / emotion: de q (hors paramètre emotions).")
          List<String> emotions,
      @Schema(
              description =
                  "Borne exclusive déduite de avant: / before: (début de la période, UTC ISO 8601) ;"
                      + " null sans jeton.",
              example = "2026-12-01T00:00:00Z")
          Instant before,
      @Schema(
              description =
                  "Borne inclusive déduite de après: / after: (premier instant suivant la période,"
                      + " UTC ISO 8601) ; null sans jeton.",
              example = "2026-07-01T00:00:00Z")
          Instant after) {}

  @Schema(
      description =
          "Une entrée trouvée. title et snippet sont des fragments HTML échappés ; seule la"
              + " balise mark y est ajoutée, autour des mots trouvés.")
  record EntrySearchItemDto(
      @Schema(
              description = "Identifiant de l'entrée.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description = "Code du type d'entrée.",
              allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
              example = "note")
          String type,
      @Schema(
              description =
                  "Titre de la version courante en HTML échappé, mots trouvés entourés de mark si"
                      + " la recherche a un texte ; null si l'entrée n'a pas de titre.",
              example = "Le <mark>licenciement</mark> de son frère")
          String title,
      @Schema(
              description =
                  "Extrait du corps en HTML échappé, mots trouvés entourés de mark (sans texte de"
                      + " recherche : début du corps, 200 caractères au plus, « … » si tronqué) ;"
                      + " null si l'entrée n'a pas de corps.",
              example = "…n'a pas supporté le <mark>licenciement</mark> de son frère…")
          String snippet,
      @Schema(
              description = "Début de l'événement concerné (occurred_from), UTC ISO 8601.",
              example = "2026-03-10T10:00:00Z")
          Instant occurredFrom,
      @Schema(
              description =
                  "Fin de l'événement concerné (occurred_to), UTC ISO 8601 ; null si ponctuel ou"
                      + " en cours.",
              example = "2026-03-10T11:00:00Z")
          Instant occurredTo,
      @Schema(
              description =
                  "Précision de la date : exact = date-heure connue, day = jour connu, month ="
                      + " mois connu, year = année connue, period = période, life_phase = phase de"
                      + " vie.",
              allowableValues = {"exact", "day", "month", "year", "period", "life_phase"},
              example = "exact")
          String occurredPrecision,
      @Schema(
              description = "Identifiant de la séance de rattachement.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID sessionId,
      @Schema(
              description =
                  "Visibilité : private = réservée à son auteur, clinical = dossier clinique.",
              allowableValues = {"private", "clinical"},
              example = "clinical")
          String visibility,
      @Schema(
              description =
                  "Date-heure d'archivage, UTC ISO 8601 ; null si l'entrée n'est pas archivée"
                      + " (toujours null sans includeArchived).",
              example = "2026-04-02T08:00:00Z")
          Instant archivedAt,
      @Schema(description = "Numéro de la version courante de l'entrée.", example = "1")
          int version,
      @Schema(description = "Libellés des tags de l'entrée, triés ; liste vide si aucun.")
          List<String> tags,
      @Schema(
              description = "Code de l'émotion pour un ressenti, null pour les autres types.",
              example = "anxiety")
          String emotionCode,
      @Schema(
              description =
                  "Pertinence (ts_rank_cd, multipliée par 1,5 pour les entrées de moins de 90"
                      + " jours) ; null pour un résultat approché ou une recherche sans texte.",
              example = "0.31")
          Double rank,
      @Schema(
              description =
                  "Similarité trigramme de 0 à 1 (seuil 0,3) ; null pour un résultat exact.",
              minimum = "0",
              maximum = "1",
              example = "0.45")
          Double similarity,
      @Schema(
              description =
                  "true pour un résultat approché (tolérance aux fautes de frappe), false pour un"
                      + " résultat exact.",
              example = "false")
          boolean approximate) {}

  @Schema(description = "Facette par type d'entrée.")
  record EntrySearchTypeFacetDto(
      @Schema(
              description = "Code du type d'entrée.",
              allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
              example = "note")
          String code,
      @Schema(description = "Nombre de résultats exacts de ce type.", example = "7") int count) {}

  @Schema(description = "Facette par émotion (ressentis).")
  record EntrySearchEmotionFacetDto(
      @Schema(description = "Code de l'émotion.", example = "anxiety") String code,
      @Schema(description = "Libellé français de l'émotion.", example = "Anxiété") String label,
      @Schema(description = "Nombre de résultats exacts portant cette émotion.", example = "3")
          int count) {}

  @Schema(description = "Facette par tag.")
  record EntrySearchTagFacetDto(
      @Schema(description = "Identifiant du tag.", example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(description = "Libellé du tag.", example = "travail") String label,
      @Schema(description = "Nombre de résultats exacts portant ce tag.", example = "5")
          int count) {}

  @Schema(
      description =
          "Compteurs sur les résultats exacts, triés par effectif décroissant ; chaque facette"
              + " ignore son propre filtre pour que les puces gardent leur compteur.")
  record EntrySearchFacetsDto(
      @Schema(description = "Répartition par type d'entrée.") List<EntrySearchTypeFacetDto> types,
      @Schema(description = "Répartition par émotion.") List<EntrySearchEmotionFacetDto> emotions,
      @Schema(description = "Répartition par tag.") List<EntrySearchTagFacetDto> tags) {}

  @Schema(
      description =
          "Page de résultats d'une recherche : entrées, facettes et requête interprétée. Pagination"
              + " par numéro de page (20 par page), non par curseur.")
  record EntrySearchResultDto(
      @Schema(description = "Requête telle que le serveur l'a interprétée.")
          EntrySearchQueryDto query,
      @Schema(description = "Numéro de la page renvoyée, à partir de 1.", example = "1") int page,
      @Schema(description = "Taille de page, fixe : 20.", example = "20") int pageSize,
      @Schema(
              description =
                  "Nombre total de résultats exacts sur toutes les pages, hors résultats"
                      + " approchés.",
              example = "25")
          long total,
      @Schema(description = "Nombre de résultats exacts dans items.", example = "20")
          int exactCount,
      @Schema(
              description =
                  "Nombre de résultats approchés dans items (page 1 seulement, et seulement s'il y"
                      + " a moins de 3 résultats exacts, avec un texte sans exclusion -mot ; 0 sinon).",
              example = "0")
          int approximateCount,
      @Schema(
              description =
                  "Résultats de la page : exacts d'abord (par pertinence, puis date décroissante),"
                      + " puis approchés (par similarité). Sans texte : par date décroissante.")
          List<EntrySearchItemDto> items,
      @Schema(description = "Facettes calculées sur les résultats exacts.")
          EntrySearchFacetsDto facets,
      @Schema(description = "Durée de traitement côté serveur, en millisecondes.", example = "12")
          long tookMs) {}
}
