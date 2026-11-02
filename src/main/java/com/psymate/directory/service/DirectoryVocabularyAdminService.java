package com.psymate.directory.service;

import com.psymate.directory.dto.CreateRequest;
import com.psymate.directory.dto.ItemDto;
import com.psymate.directory.dto.ListDto;
import com.psymate.directory.dto.PatchRequest;
import com.psymate.directory.model.DirectoryVocabulary;
import com.psymate.directory.model.DirectoryVocabularyException;
import com.psymate.directory.model.DirectoryVocabularyException.FieldError;
import com.psymate.directory.model.DirectoryVocabularyId;
import com.psymate.directory.repository.DirectoryVocabularyRepository;
import com.psymate.shared.time.Timestamps;
import java.sql.SQLException;
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
public class DirectoryVocabularyAdminService {

  static final Set<String> KINDS = Set.of("specialty", "audience");
  static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,39}$");
  static final int MAX_LABEL = 120;
  static final int MAX_SORT = 32767;

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
  public ListDto list(String kind) {
    String k = kind == null || kind.isBlank() ? null : kind;
    if (k != null && !KINDS.contains(k)) {
      throw DirectoryVocabularyException.invalid(
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
  public ItemDto create(CreateRequest r) {
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
      throw DirectoryVocabularyException.invalid(errors);
    }
    try {
      return tx.execute(status -> doCreate(r, labelFr, labelEn));
    } catch (DataIntegrityViolationException race) {
      // Seule la course sur la clé (kind, code) devient un 409 ; toute autre violation
      // d'intégrité reste une erreur serveur, comme l'ancien catch de DuplicateKeyException.
      if (isUniqueViolation(race)) {
        throw exists();
      }
      throw race;
    }
  }

  /** Vrai si la chaîne de causes contient une violation d'unicité PostgreSQL (SQLSTATE 23505). */
  static boolean isUniqueViolation(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
      if (c.getCause() == c) {
        break;
      }
    }
    return false;
  }

  private ItemDto doCreate(CreateRequest r, String labelFr, String labelEn) {
    DirectoryVocabularyId id = new DirectoryVocabularyId(r.kind(), r.code());
    if (repo.existsById(id)) {
      throw exists();
    }
    int sort =
        r.sortOrder() != null ? r.sortOrder() : Math.min(repo.nextSortOrder(r.kind()), MAX_SORT);
    DirectoryVocabulary created =
        repo.save(new DirectoryVocabulary(id, labelFr, labelEn, sort, Timestamps.now(clock)));
    // Un code qui n'existait pas ne peut être dans aucune fiche : aucun usage.
    return dto(created, 0);
  }

  @Transactional
  public ItemDto update(String kind, String code, PatchRequest r) {
    DirectoryVocabulary current =
        KINDS.contains(kind) ? repo.findForUpdate(kind, code).orElse(null) : null;
    if (current == null) {
      throw DirectoryVocabularyException.of(
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
      throw DirectoryVocabularyException.invalid(errors);
    }
    int used = repo.usedByPublishedProfiles(kind, code);
    boolean active = r.active() != null ? r.active() : current.isActive();
    if (!active && current.isActive() && used > 0) {
      throw DirectoryVocabularyException.with(
          HttpStatus.CONFLICT,
          "VOCABULARY_IN_USE",
          "vocabulary.error.inuse",
          Map.of("usedByPublishedProfiles", used));
    }
    current.update(labelFr, labelEn, sort, active, Timestamps.now(clock));
    return dto(current, used);
  }

  private static DirectoryVocabularyException exists() {
    return DirectoryVocabularyException.of(
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
