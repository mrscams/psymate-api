package com.psymate.directory;

import com.psymate.directory.DirectoryVocabularyAdminRepository.Row;
import com.psymate.directory.VocabularyException.FieldError;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration de la liste fermée des spécialités et publics de l'annuaire (G Q15, fiche 21,
 * M1-14b). Aucune suppression : une entrée se désactive, et seulement si aucune fiche publiée ne
 * l'utilise. Les routes sont gardées (rôle admin + 2FA) par AdminTwoFactorFilter sur /api/admin/**.
 */
@Service
@Transactional
class DirectoryVocabularyAdminService {

  static final Set<String> KINDS = Set.of("specialty", "audience");
  static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,39}$");
  static final int MAX_LABEL = 120;
  static final int MAX_SORT = 32767;

  /** Création : type, code, libellés, rang (absent : après le dernier du type). */
  @Schema(description = "Nouvelle entrée du vocabulaire de l'annuaire (spécialité ou public).")
  record CreateRequest(
      @Schema(
              description = "Type de l'entrée : specialty (spécialité) ou audience (public).",
              allowableValues = {"specialty", "audience"},
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "specialty")
          String kind,
      @Schema(
              description =
                  "Code stable de l'entrée, unique pour le type : commence par une lettre"
                      + " minuscule, puis minuscules, chiffres et underscores, 2 à 40 caractères."
                      + " Ne change plus ensuite.",
              pattern = "^[a-z][a-z0-9_]{1,39}$",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "sleep_therapy")
          String code,
      @Schema(
              description = "Libellé français, 120 caractères au plus.",
              maxLength = 120,
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "Thérapie du sommeil")
          String labelFr,
      @Schema(
              description =
                  "Libellé anglais, 120 caractères au plus ; vide ou absent : pas de libellé"
                      + " anglais.",
              maxLength = 120,
              example = "Sleep therapy")
          String labelEn,
      @Schema(
              description =
                  "Rang d'affichage dans le type, de 0 à 32767 ; absent : après le dernier du"
                      + " type.",
              minimum = "0",
              maximum = "32767",
              example = "23")
          Integer sortOrder) {}

  /** Modification partielle : un champ absent reste inchangé ; labelEn vide efface le libellé. */
  @Schema(
      description =
          "Modification partielle d'une entrée : un champ absent reste inchangé. Le code et le"
              + " type ne se modifient pas.")
  record PatchRequest(
      @Schema(
              description = "Nouveau libellé français, non vide, 120 caractères au plus.",
              maxLength = 120)
          String labelFr,
      @Schema(
              description =
                  "Nouveau libellé anglais, 120 caractères au plus ; chaîne vide : efface le"
                      + " libellé anglais.",
              maxLength = 120)
          String labelEn,
      @Schema(
              description = "Nouveau rang d'affichage dans le type, de 0 à 32767.",
              minimum = "0",
              maximum = "32767")
          Integer sortOrder,
      @Schema(
              description =
                  "false pour désactiver l'entrée (409 VOCABULARY_IN_USE si une fiche publiée"
                      + " l'utilise), true pour la réactiver.")
          Boolean active) {}

  @Schema(description = "Entrée du vocabulaire de l'annuaire, active ou non.")
  record ItemDto(
      @Schema(
              description = "Type de l'entrée : specialty (spécialité) ou audience (public).",
              allowableValues = {"specialty", "audience"},
              example = "specialty")
          String kind,
      @Schema(description = "Code stable de l'entrée, unique pour le type.", example = "anxiety")
          String code,
      @Schema(description = "Libellé français.", example = "Anxiété") String labelFr,
      @Schema(description = "Libellé anglais ; null s'il n'y en a pas.", example = "Anxiety")
          String labelEn,
      @Schema(description = "Rang d'affichage dans le type, de 0 à 32767.", example = "1")
          int sortOrder,
      @Schema(
              description =
                  "true si l'entrée est proposée dans l'annuaire ; false si désactivée (invisible"
                      + " dans GET /api/vocabulary/directory et refusée dans les profils).")
          boolean active,
      @Schema(
              description =
                  "Nombre de fiches publiées qui utilisent ce code ; tant qu'il est supérieur à 0,"
                      + " l'entrée ne peut pas être désactivée.",
              example = "3")
          int usedByPublishedProfiles) {}

  @Schema(description = "Entrées du vocabulaire de l'annuaire.")
  record ListDto(
      @Schema(
              description =
                  "Entrées, actives ou non, par type puis rang puis libellé français quand les"
                      + " deux types sont listés.")
          List<ItemDto> items) {}

  private final DirectoryVocabularyAdminRepository repo;
  private final Clock clock;

  DirectoryVocabularyAdminService(DirectoryVocabularyAdminRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  ListDto list(String kind) {
    String k = kind == null || kind.isBlank() ? null : kind;
    if (k != null && !KINDS.contains(k)) {
      throw VocabularyException.invalid(
          List.of(new FieldError("kind", "INVALID_VALUE", "vocabulary.error.kind")));
    }
    return new ListDto(repo.list(k).stream().map(DirectoryVocabularyAdminService::dto).toList());
  }

  ItemDto create(CreateRequest r) {
    List<FieldError> errors = new ArrayList<>();
    if (r.kind() == null || !KINDS.contains(r.kind())) {
      errors.add(new FieldError("kind", "INVALID_VALUE", "vocabulary.error.kind"));
    }
    if (r.code() == null || r.code().isBlank()) {
      errors.add(new FieldError("code", "REQUIRED", "vocabulary.error.required"));
    } else if (!CODE.matcher(r.code()).matches()) {
      errors.add(new FieldError("code", "INVALID_FORMAT", "vocabulary.error.code"));
    }
    String labelFr = r.labelFr() == null ? "" : r.labelFr().trim();
    if (labelFr.isEmpty()) {
      errors.add(new FieldError("labelFr", "REQUIRED", "vocabulary.error.required"));
    } else if (labelFr.length() > MAX_LABEL) {
      errors.add(new FieldError("labelFr", "TOO_LONG", "vocabulary.error.label"));
    }
    String labelEn = blankToNull(r.labelEn());
    if (labelEn != null && labelEn.length() > MAX_LABEL) {
      errors.add(new FieldError("labelEn", "TOO_LONG", "vocabulary.error.label"));
    }
    if (r.sortOrder() != null && (r.sortOrder() < 0 || r.sortOrder() > MAX_SORT)) {
      errors.add(new FieldError("sortOrder", "OUT_OF_RANGE", "vocabulary.error.sort"));
    }
    if (!errors.isEmpty()) {
      throw VocabularyException.invalid(errors);
    }
    if (repo.exists(r.kind(), r.code())) {
      throw exists();
    }
    int sort =
        r.sortOrder() != null ? r.sortOrder() : Math.min(repo.nextSortOrder(r.kind()), MAX_SORT);
    try {
      repo.insert(r.kind(), r.code(), labelFr, labelEn, sort, clock.instant());
    } catch (DuplicateKeyException e) {
      throw exists();
    }
    return dto(repo.find(r.kind(), r.code()).orElseThrow());
  }

  ItemDto update(String kind, String code, PatchRequest r) {
    if (!KINDS.contains(kind) || !repo.lock(kind, code)) {
      throw VocabularyException.of(
          HttpStatus.NOT_FOUND, "VOCABULARY_NOT_FOUND", "vocabulary.error.notfound");
    }
    Row current = repo.find(kind, code).orElseThrow();
    List<FieldError> errors = new ArrayList<>();
    String labelFr = current.labelFr();
    if (r.labelFr() != null) {
      labelFr = r.labelFr().trim();
      if (labelFr.isEmpty()) {
        errors.add(new FieldError("labelFr", "REQUIRED", "vocabulary.error.required"));
      } else if (labelFr.length() > MAX_LABEL) {
        errors.add(new FieldError("labelFr", "TOO_LONG", "vocabulary.error.label"));
      }
    }
    String labelEn = current.labelEn();
    if (r.labelEn() != null) {
      labelEn = blankToNull(r.labelEn());
      if (labelEn != null && labelEn.length() > MAX_LABEL) {
        errors.add(new FieldError("labelEn", "TOO_LONG", "vocabulary.error.label"));
      }
    }
    int sort = current.sortOrder();
    if (r.sortOrder() != null) {
      sort = r.sortOrder();
      if (sort < 0 || sort > MAX_SORT) {
        errors.add(new FieldError("sortOrder", "OUT_OF_RANGE", "vocabulary.error.sort"));
      }
    }
    if (!errors.isEmpty()) {
      throw VocabularyException.invalid(errors);
    }
    boolean active = r.active() != null ? r.active() : current.active();
    if (!active && current.active() && current.usedByPublishedProfiles() > 0) {
      throw VocabularyException.with(
          HttpStatus.CONFLICT,
          "VOCABULARY_IN_USE",
          "vocabulary.error.inuse",
          Map.of("usedByPublishedProfiles", current.usedByPublishedProfiles()));
    }
    repo.update(kind, code, labelFr, labelEn, sort, active, clock.instant());
    return dto(repo.find(kind, code).orElseThrow());
  }

  private static VocabularyException exists() {
    return VocabularyException.of(
        HttpStatus.CONFLICT, "VOCABULARY_EXISTS", "vocabulary.error.exists");
  }

  private static ItemDto dto(Row r) {
    return new ItemDto(
        r.kind(),
        r.code(),
        r.labelFr(),
        r.labelEn(),
        r.sortOrder(),
        r.active(),
        r.usedByPublishedProfiles());
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
