package com.psymate.directory;

import com.psymate.directory.DirectoryVocabularyAdminService.CreateRequest;
import com.psymate.directory.DirectoryVocabularyAdminService.ItemDto;
import com.psymate.directory.DirectoryVocabularyAdminService.ListDto;
import com.psymate.directory.DirectoryVocabularyAdminService.PatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration du vocabulaire de l'annuaire (G Q15, fiche 21, M1-14b). Vit dans directory et non
 * dans identity.admin : identity ne peut pas dépendre de directory (cycle). Le rôle admin et la 2FA
 * sont contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**. Aucune route DELETE.
 */
@RestController
@RequestMapping("/api/admin/vocabulary/directory")
class DirectoryVocabularyAdminController {

  private final DirectoryVocabularyAdminService service;

  DirectoryVocabularyAdminController(DirectoryVocabularyAdminService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminDirectoryVocabulary",
      summary =
          "Spécialités et publics de l'annuaire, actifs ou non, avec le nombre de fiches publiées"
              + " qui les utilisent")
  ListDto list(@RequestParam(required = false) String kind) {
    return service.list(kind);
  }

  @PostMapping
  @Operation(
      operationId = "createAdminDirectoryVocabulary",
      summary = "Ajoute une spécialité ou un public (409 VOCABULARY_EXISTS si le code existe)")
  ResponseEntity<ItemDto> create(@RequestBody CreateRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @PatchMapping("/{kind}/{code}")
  @Operation(
      operationId = "updateAdminDirectoryVocabulary",
      summary =
          "Modifie libellés, rang ou activation (409 VOCABULARY_IN_USE pour désactiver un code"
              + " utilisé par une fiche publiée)")
  ItemDto update(
      @PathVariable String kind, @PathVariable String code, @RequestBody PatchRequest body) {
    return service.update(kind, code, body);
  }
}
