package com.psymate.vocabulary.controller;

import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.EntryTypeView;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import com.psymate.vocabulary.service.EntryTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lecture des vocabulaires. Sera protégé par la sécurité de M0-04 (pas encore présente). */
@Tag(
    name = "Vocabulaire",
    description =
        "Catalogues de référence en lecture seule : émotions, types d'entrées et types de liens.")
@RestController
@RequestMapping("/api/vocabulary")
class VocabularyController {

  private final EmotionCatalog emotions;
  private final LinkTypeCatalog linkTypes;
  private final EntryTypeService entryTypes;

  VocabularyController(
      EmotionCatalog emotions, LinkTypeCatalog linkTypes, EntryTypeService entryTypes) {
    this.emotions = emotions;
    this.linkTypes = linkTypes;
    this.entryTypes = entryTypes;
  }

  @GetMapping("/emotions")
  @Operation(
      summary = "Liste les émotions utilisables par le praticien",
      description =
          """
          Session praticien. Renvoie les 29 émotions globales du produit plus les émotions \
          personnalisées actives du praticien connecté (jamais celles d'un autre praticien) ; les \
          émotions fusionnées sont absentes. Tri : globales d'abord, puis par famille, niveau \
          d'intensité (non renseigné en dernier) et libellé. Liste complète, non paginée.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Émotions actives (globales et personnalisées).")
  })
  List<EmotionView> emotions() {
    return emotions.activeEmotions();
  }

  @GetMapping("/link-types")
  @Operation(
      summary = "Liste les types de liens et leur matrice source/cible",
      description =
          """
          Session praticien. Les huit types de liens actifs, triés par sortOrder, avec les types \
          d'entrée admis en source et en cible : un lien dont la source ou la cible sort de cette \
          matrice est refusé à la création. Catalogue identique pour tous les praticiens, liste \
          complète, non paginée.""")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Types de liens actifs.")})
  List<LinkTypeView> linkTypes() {
    return linkTypes.activeLinkTypes();
  }

  @GetMapping("/entry-types")
  @Operation(
      summary = "Liste les types d'entrées cliniques",
      description =
          """
          Session praticien. Les six types d'entrées actifs (note, event, situation, feeling, \
          session, medication), triés par sortOrder. Catalogue identique pour tous les \
          praticiens, liste complète, non paginée.""")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Types d'entrées actifs.")})
  List<EntryTypeView> entryTypes() {
    return entryTypes.activeEntryTypes();
  }
}
