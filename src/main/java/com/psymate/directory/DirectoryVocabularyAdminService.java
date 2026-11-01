package com.psymate.directory;

import com.psymate.directory.VocabularyException.FieldError;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Administration de la liste fermée des spécialités et publics de l'annuaire (G Q15, fiche 21,
 * M1-14b). Aucune suppression : une entrée se désactive, et seulement si aucune fiche publiée ne
 * l'utilise. Les routes sont gardées (rôle admin + 2FA) par AdminTwoFactorFilter sur /api/admin/**.
 */
@Service
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

  private final DirectoryVocabularyRepository repo;
  private final TransactionTemplate tx;
  private final Clock clock;

  DirectoryVocabularyAdminService(
      DirectoryVocabularyRepository repo, TransactionTemplate tx, Clock clock) {
    this.repo = repo;
    this.tx = tx;
    this.clock = clock;
  }

  /** Une requête pour les entrées, une pour les comptes d'usage de toute la liste (pas de N+1). */
  @Transactional(readOnly = true)
  ListDto list(String kind) {
    String k = kind == null || kind.isBlank() ? null : kind;
    if (k != null && !KINDS.contains(k)) {
      throw VocabularyException.invalid(
          List.of(new FieldError("kind", "INVALID_VALUE", "vocabulary.error.kind")));
    }
    List<DirectoryVocabulary> entries =
        k == null
            ? repo.findAllByOrderByIdKindAscSortOrderAscLabelFrAsc()
            : repo.findByIdKindOrderBySortOrderAscLabelFrAsc(k);
    Map<DirectoryVocabularyId, Integer> used = repo.usedByPublishedProfiles(k);
    return new ListDto(entries.stream().map(e -> dto(e, used.getOrDefault(e.getId(), 0))).toList());
  }

  /**
   * Pas de {@code @Transactional} ici : l'unicité (kind, code) est contrôlée au flush / commit,
   * donc le 409 d'une course est attrapé APRÈS le commit (même schéma que LinkService).
   */
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
    try {
      return tx.execute(status -> doCreate(r, labelFr, labelEn));
    } catch (DataIntegrityViolationException race) {
      throw exists();
    }
  }

  private ItemDto doCreate(CreateRequest r, String labelFr, String labelEn) {
    DirectoryVocabularyId id = new DirectoryVocabularyId(r.kind(), r.code());
    if (repo.existsById(id)) {
      throw exists();
    }
    int sort =
        r.sortOrder() != null ? r.sortOrder() : Math.min(repo.nextSortOrder(r.kind()), MAX_SORT);
    DirectoryVocabulary created =
        repo.save(new DirectoryVocabulary(id, labelFr, labelEn, sort, DirectoryClock.now(clock)));
    // Un code qui n'existait pas ne peut être dans aucune fiche : aucun usage.
    return dto(created, 0);
  }

  @Transactional
  ItemDto update(String kind, String code, PatchRequest r) {
    DirectoryVocabulary current =
        KINDS.contains(kind) ? repo.findForUpdate(kind, code).orElse(null) : null;
    if (current == null) {
      throw VocabularyException.of(
          HttpStatus.NOT_FOUND, "VOCABULARY_NOT_FOUND", "vocabulary.error.notfound");
    }
    List<FieldError> errors = new ArrayList<>();
    String labelFr = current.getLabelFr();
    if (r.labelFr() != null) {
      labelFr = r.labelFr().trim();
      if (labelFr.isEmpty()) {
        errors.add(new FieldError("labelFr", "REQUIRED", "vocabulary.error.required"));
      } else if (labelFr.length() > MAX_LABEL) {
        errors.add(new FieldError("labelFr", "TOO_LONG", "vocabulary.error.label"));
      }
    }
    String labelEn = current.getLabelEn();
    if (r.labelEn() != null) {
      labelEn = blankToNull(r.labelEn());
      if (labelEn != null && labelEn.length() > MAX_LABEL) {
        errors.add(new FieldError("labelEn", "TOO_LONG", "vocabulary.error.label"));
      }
    }
    int sort = current.getSortOrder();
    if (r.sortOrder() != null) {
      sort = r.sortOrder();
      if (sort < 0 || sort > MAX_SORT) {
        errors.add(new FieldError("sortOrder", "OUT_OF_RANGE", "vocabulary.error.sort"));
      }
    }
    if (!errors.isEmpty()) {
      throw VocabularyException.invalid(errors);
    }
    int used = repo.usedByPublishedProfiles(kind, code);
    boolean active = r.active() != null ? r.active() : current.isActive();
    if (!active && current.isActive() && used > 0) {
      throw VocabularyException.with(
          HttpStatus.CONFLICT,
          "VOCABULARY_IN_USE",
          "vocabulary.error.inuse",
          Map.of("usedByPublishedProfiles", used));
    }
    current.update(labelFr, labelEn, sort, active, DirectoryClock.now(clock));
    return dto(current, used);
  }

  private static VocabularyException exists() {
    return VocabularyException.of(
        HttpStatus.CONFLICT, "VOCABULARY_EXISTS", "vocabulary.error.exists");
  }

  private static ItemDto dto(DirectoryVocabulary v, int usedByPublishedProfiles) {
    return new ItemDto(
        v.getKind(),
        v.getCode(),
        v.getLabelFr(),
        v.getLabelEn(),
        v.getSortOrder(),
        v.isActive(),
        usedByPublishedProfiles);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
