package com.psymate.directory.controller;

import com.psymate.directory.dto.DirectoryVocabularyDto;
import com.psymate.directory.dto.Item;
import com.psymate.directory.service.DirectoryVocabularyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listes fermées de l'annuaire (G Q15) : spécialités et publics en base, modalités et langues. */
@Tag(
    name = "Vocabulaire de l'annuaire",
    description =
        "Listes fermées proposées pour remplir le profil d'annuaire : spécialités, publics,"
            + " modalités et langues.")
@RestController
@RequestMapping("/api/vocabulary/directory")
class DirectoryVocabularyController {

  private static final List<Item> MODALITIES =
      List.of(
          new Item("in_person", "Au cabinet", "In person"),
          new Item("video", "En visio", "Video"),
          new Item("home", "À domicile", "At home"));

  /** Langues proposées, codes ISO 639-1 (tout code ISO 639-1 reste accepté en écriture). */
  private static final List<String> LANGUAGES =
      List.of(
          "fr", "en", "es", "de", "it", "pt", "ar", "nl", "ru", "pl", "tr", "zh", "ja", "ro", "hi");

  private final DirectoryVocabularyService vocabulary;

  DirectoryVocabularyController(DirectoryVocabularyService vocabulary) {
    this.vocabulary = vocabulary;
  }

  @GetMapping
  @Operation(
      summary = "Liste les valeurs admises pour remplir le profil d'annuaire",
      description =
          """
          Session praticien. Rend les spécialités et publics actifs (administrables), les \
          modalités et les langues proposées ; les codes sont ceux à envoyer dans le profil \
          (PUT /api/me/profile) et dans les filtres de l'annuaire public. Listes complètes, non \
          paginées.""")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Listes fermées de l'annuaire.")})
  DirectoryVocabularyDto get() {
    List<Item> languages =
        LANGUAGES.stream()
            .map(
                c -> {
                  Locale l = Locale.forLanguageTag(c);
                  return new Item(
                      c, l.getDisplayLanguage(Locale.FRENCH), l.getDisplayLanguage(Locale.ENGLISH));
                })
            .sorted(Comparator.comparing(Item::labelFr))
            .toList();
    return new DirectoryVocabularyDto(
        vocabulary.activeItems("specialty"),
        vocabulary.activeItems("audience"),
        MODALITIES,
        languages);
  }
}
