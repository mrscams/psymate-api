package com.psymate.directory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
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

  @Schema(description = "Entrée d'une liste fermée de l'annuaire, avec ses libellés fr et en.")
  record Item(
      @Schema(
              description =
                  "Code stable à envoyer dans le profil ou dans les filtres de recherche"
                      + " (minuscules et underscores, ou code de langue ISO 639-1).",
              example = "anxiety")
          String code,
      @Schema(description = "Libellé en français.", example = "Anxiété") String labelFr,
      @Schema(
              description = "Libellé en anglais ; peut être null pour une entrée ajoutée sans.",
              example = "Anxiety")
          String labelEn) {}

  @Schema(
      description =
          "Listes fermées de l'annuaire. Seules les entrées actives sont rendues, dans l'ordre"
              + " d'affichage.")
  record DirectoryVocabularyDto(
      @Schema(
              description =
                  "Spécialités actives, par rang puis libellé français (liste administrable).")
          List<Item> specialties,
      @Schema(
              description =
                  "Publics accueillis actifs, par rang puis libellé français (liste"
                      + " administrable) : par exemple adults, young_adults, adolescents,"
                      + " children, couples_families.")
          List<Item> audiences,
      @Schema(
              description =
                  "Modalités de consultation, liste fixe : in_person (au cabinet), video (en"
                      + " visio), home (à domicile).")
          List<Item> modalities,
      @Schema(
              description =
                  "Langues proposées par défaut (15 codes ISO 639-1, triées par libellé"
                      + " français) ; tout autre code ISO 639-1 reste accepté dans le profil.")
          List<Item> languages) {}

  private static final List<Item> MODALITIES =
      List.of(
          new Item("in_person", "Au cabinet", "In person"),
          new Item("video", "En visio", "Video"),
          new Item("home", "À domicile", "At home"));

  /** Langues proposées, codes ISO 639-1 (tout code ISO 639-1 reste accepté en écriture). */
  private static final List<String> LANGUAGES =
      List.of(
          "fr", "en", "es", "de", "it", "pt", "ar", "nl", "ru", "pl", "tr", "zh", "ja", "ro", "hi");

  private final DirectoryVocabularyRepository repo;

  DirectoryVocabularyController(DirectoryVocabularyRepository repo) {
    this.repo = repo;
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
    return new DirectoryVocabularyDto(items("specialty"), items("audience"), MODALITIES, languages);
  }

  private List<Item> items(String kind) {
    return repo.activeItems(kind).stream()
        .map(r -> new Item(r.code(), r.labelFr(), r.labelEn()))
        .toList();
  }
}
