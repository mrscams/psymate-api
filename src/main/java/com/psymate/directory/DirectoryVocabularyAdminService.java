package com.psymate.directory;

import com.psymate.directory.DirectoryVocabularyAdminRepository.Row;
import com.psymate.directory.VocabularyException.FieldError;
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
  record CreateRequest(
      String kind, String code, String labelFr, String labelEn, Integer sortOrder) {}

  /** Modification partielle : un champ absent reste inchangé ; labelEn vide efface le libellé. */
  record PatchRequest(String labelFr, String labelEn, Integer sortOrder, Boolean active) {}

  record ItemDto(
      String kind,
      String code,
      String labelFr,
      String labelEn,
      int sortOrder,
      boolean active,
      int usedByPublishedProfiles) {}

  record ListDto(List<ItemDto> items) {}

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
