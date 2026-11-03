package com.psymate.directory.controller;

import com.psymate.directory.dto.DirectoryVocabularyDto;
import com.psymate.directory.service.DirectoryVocabularyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
          paginées. Le même contenu est servi sans session par GET \
          /api/public/directory/vocabulary.""")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Listes fermées de l'annuaire.")})
  DirectoryVocabularyDto get() {
    return vocabulary.directoryVocabulary();
  }
}
