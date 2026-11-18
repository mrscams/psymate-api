package com.psymate.patients.service;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.dto.CreatePatientProfileItemRequest;
import com.psymate.patients.dto.PatientProfileDto;
import com.psymate.patients.dto.PatientProfileItemDto;
import com.psymate.patients.dto.PatientProfileItemMapper;
import com.psymate.patients.dto.UpdatePatientProfileItemRequest;
import com.psymate.patients.model.Patient;
import com.psymate.patients.model.PatientException;
import com.psymate.patients.model.PatientFieldCode;
import com.psymate.patients.model.PatientProfileCategory;
import com.psymate.patients.model.PatientProfileItem;
import com.psymate.patients.repository.PatientJpaRepository;
import com.psymate.patients.repository.PatientOwnership;
import com.psymate.patients.repository.PatientProfileItemRepository;
import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil 360 d'un patient (G Q62 à Q70, J3) : lecture de tout le profil, ajout, correction sur
 * place et retrait d'une information. Toute route passe d'abord par {@link PatientOwnershipGuard}
 * (403 / 404 des patients). Une écriture de profil ne crée aucune version, ne publie aucun
 * événement et ne touche pas {@code clinical_rev} (G Q62) ; elle n'écrit ni {@code entry} ni {@code
 * link} (G Q63).
 *
 * <p>Ordre des contrôles d'une écriture : propriété du patient (403 / 404), patient archivé (409
 * PATIENT_ARCHIVED), information du patient (404 PROFILE_ITEM_NOT_FOUND), information retirée (409
 * PROFILE_ITEM_ARCHIVED, correction seulement), champs (422 VALIDATION_FAILED), plafond (422
 * PROFILE_ITEM_LIMIT_REACHED, ajout seulement).
 */
@Service
public class PatientProfileService {

  static final String PROFILE_ITEM_NOT_FOUND = "PROFILE_ITEM_NOT_FOUND";
  static final String PROFILE_ITEM_ARCHIVED = "PROFILE_ITEM_ARCHIVED";
  static final String PROFILE_ITEM_LIMIT_REACHED = "PROFILE_ITEM_LIMIT_REACHED";

  /** Ordre des champs dans errors[] ; une clé refusée (UNKNOWN_FIELD) vient après. */
  static final List<String> FIELDS =
      List.of("category", "title", "detail", "periodLabel", "periodYear");

  /**
   * Tri de l'onglet (J3) : rubrique dans l'ordre de l'énumération ; dans history, l'année
   * croissante, les informations sans année en dernier ; puis la création, puis l'identifiant.
   */
  static final Comparator<PatientProfileItem> DISPLAY_ORDER =
      Comparator.comparing(PatientProfileItem::getCategory)
          .thenComparing(
              PatientProfileItem::getPeriodYear, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(PatientProfileItem::getCreatedAt)
          .thenComparing(PatientProfileItem::getId);

  /** Les cinq champs d'une information, avant ou après une correction. */
  record Fields(
      PatientProfileCategory category,
      String title,
      String detail,
      String periodLabel,
      Integer periodYear) {

    static Fields of(PatientProfileItem item) {
      return new Fields(
          item.getCategory(),
          item.getTitle(),
          item.getDetail(),
          item.getPeriodLabel(),
          item.getPeriodYear());
    }
  }

  private final PatientProfileItemRepository items;
  private final PatientJpaRepository patients;
  private final PatientOwnershipGuard guard;
  private final PatientProfileItemMapper mapper;
  private final PatientProfileMessages messages;
  private final Validator validator;
  private final int maxActiveItems;

  PatientProfileService(
      PatientProfileItemRepository items,
      PatientJpaRepository patients,
      PatientOwnershipGuard guard,
      PatientProfileItemMapper mapper,
      PatientProfileMessages messages,
      Validator validator,
      @Value("${app.patients.profile.max-active-items:500}") int maxActiveItems) {
    this.items = items;
    this.patients = patients;
    this.guard = guard;
    this.mapper = mapper;
    this.messages = messages;
    this.validator = validator;
    this.maxActiveItems = maxActiveItems;
  }

  // ---- lecture

  /** Tout le profil actif, trié pour l'onglet ; permis sur un patient archivé (G Q69). */
  @Transactional(readOnly = true)
  public PatientProfileDto profile(UUID patientId) {
    guard.requireOwned(patientId);
    List<PatientProfileItemDto> list =
        items.findByPatientIdAndArchivedAtIsNull(patientId).stream()
            .sorted(DISPLAY_ORDER)
            .map(mapper::toDto)
            .toList();
    return new PatientProfileDto(patientId, list);
  }

  // ---- écriture

  @Transactional
  public PatientProfileItemDto create(UUID patientId, CreatePatientProfileItemRequest r) {
    guard.requireOwned(patientId);
    CurrentPractitioner me = me();
    // Verrou de la fiche (for no key update) : deux ajouts concurrents se sérialisent et ne
    // dépassent pas le plafond. La fiche n'est que lue.
    Patient patient =
        patients.findForUpdate(patientId).orElseThrow(PatientAccessException::notFound);
    if (patient.getArchivedAt() != null) {
      throw patientArchived();
    }
    Map<String, FieldProblem> problems = violations(r);
    PatientProfileCategory category = PatientProfileCategory.parse(r.category());
    if (category != null) {
      checkPeriod(category, r.periodLabel(), r.periodYear(), problems);
    }
    throwIfAny(problems);
    if (items.countByPatientIdAndArchivedAtIsNull(patientId) >= maxActiveItems) {
      throw PatientException.of(
          HttpStatus.UNPROCESSABLE_ENTITY,
          PROFILE_ITEM_LIMIT_REACHED,
          messages.domain(PROFILE_ITEM_LIMIT_REACHED, maxActiveItems));
    }
    PatientProfileItem item =
        items.save(
            new PatientProfileItem(
                UUID.randomUUID(),
                patient.getTenantId(),
                patientId,
                category,
                r.title(),
                r.detail(),
                r.periodLabel(),
                r.periodYear(),
                me.id(),
                PatientProfileItem.now()));
    return mapper.toDto(item);
  }

  /** Correction partielle, sur place (G Q67) : une seule ligne, updated_at avancé. */
  @Transactional
  public PatientProfileItemDto correct(
      UUID patientId, UUID itemId, UpdatePatientProfileItemRequest r) {
    requireWritable(patientId);
    PatientProfileItem item = find(patientId, itemId);
    if (item.isArchived()) {
      throw PatientException.of(
          HttpStatus.CONFLICT, PROFILE_ITEM_ARCHIVED, messages.domain(PROFILE_ITEM_ARCHIVED));
    }
    Map<String, FieldProblem> problems = violations(r);
    Fields f = corrected(Fields.of(item), r, problems);
    throwIfAny(problems);
    item.correct(
        f.category(),
        f.title(),
        f.detail(),
        f.periodLabel(),
        f.periodYear(),
        PatientProfileItem.now());
    return mapper.toDto(item);
  }

  /** Retrait de la vue (G Q68) : archived_at, rien n'est supprimé ; idempotent. */
  @Transactional
  public void archive(UUID patientId, UUID itemId) {
    requireWritable(patientId);
    find(patientId, itemId).archive(PatientProfileItem.now());
  }

  // ---- règles (statiques : testées sans base)

  /**
   * Période hors history (G Q66) : 422 INVALID_VALUE sur chaque champ de période renseigné, sauf
   * s'il porte déjà une erreur (longueur, bornes).
   */
  static void checkPeriod(
      PatientProfileCategory category,
      String periodLabel,
      Integer periodYear,
      Map<String, FieldProblem> problems) {
    if (category.hasPeriod()) {
      return;
    }
    if (periodLabel != null) {
      problems.putIfAbsent(
          "periodLabel", new FieldProblem("periodLabel", PatientFieldCode.INVALID_VALUE));
    }
    if (periodYear != null) {
      problems.putIfAbsent(
          "periodYear", new FieldProblem("periodYear", PatientFieldCode.INVALID_VALUE));
    }
  }

  /**
   * Fusion d'une correction partielle (J3) : une clé absente garde la valeur courante ; null efface
   * detail, periodLabel ou periodYear ; category et title ne s'effacent pas (422 REQUIRED). Si la
   * rubrique finale n'est pas history, la période envoyée non nulle est refusée (422 INVALID_VALUE)
   * et la période courante est effacée (on quitte history).
   */
  static Fields corrected(
      Fields current, UpdatePatientProfileItemRequest r, Map<String, FieldProblem> problems) {
    PatientProfileCategory category = current.category();
    if (r.categoryProvided()) {
      PatientProfileCategory sent = PatientProfileCategory.parse(r.getCategory());
      if (r.getCategory() == null) {
        problems.putIfAbsent("category", new FieldProblem("category", PatientFieldCode.REQUIRED));
      } else if (sent == null) {
        problems.putIfAbsent(
            "category", new FieldProblem("category", PatientFieldCode.INVALID_VALUE));
      } else {
        category = sent;
      }
    }
    String title = current.title();
    if (r.titleProvided()) {
      if (r.getTitle() == null || r.getTitle().isBlank()) {
        problems.putIfAbsent("title", new FieldProblem("title", PatientFieldCode.REQUIRED));
      } else {
        title = r.getTitle();
      }
    }
    String detail = r.detailProvided() ? r.getDetail() : current.detail();
    String periodLabel = r.periodLabelProvided() ? r.getPeriodLabel() : current.periodLabel();
    Integer periodYear = r.periodYearProvided() ? r.getPeriodYear() : current.periodYear();
    if (!category.hasPeriod()) {
      checkPeriod(
          category,
          r.periodLabelProvided() ? r.getPeriodLabel() : null,
          r.periodYearProvided() ? r.getPeriodYear() : null,
          problems);
      periodLabel = null;
      periodYear = null;
    }
    return new Fields(category, title, detail, periodLabel, periodYear);
  }

  /**
   * Code de champ d'une contrainte Jakarta (J3) : obligatoire → REQUIRED, longueur ou borne →
   * OUT_OF_RANGE, valeur hors liste → INVALID_VALUE.
   */
  static String codeOf(String constraint) {
    return switch (constraint) {
      case "NotBlank", "NotNull", "NotEmpty" -> PatientFieldCode.REQUIRED;
      case "Size", "Min", "Max" -> PatientFieldCode.OUT_OF_RANGE;
      default -> PatientFieldCode.INVALID_VALUE;
    };
  }

  // ---- outils

  /** Contraintes Jakarta du corps, une erreur par champ (la plus prioritaire : REQUIRED). */
  private <T> Map<String, FieldProblem> violations(T request) {
    Map<String, FieldProblem> problems = new LinkedHashMap<>();
    validator.validate(request).stream()
        .map(PatientProfileService::problem)
        .sorted(
            Comparator.comparingInt((FieldProblem p) -> fieldRank(p.field()))
                .thenComparing(p -> PatientFieldCode.REQUIRED.equals(p.code()) ? 0 : 1))
        .forEach(p -> problems.putIfAbsent(p.field(), p));
    return problems;
  }

  private static FieldProblem problem(ConstraintViolation<?> v) {
    String constraint =
        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    return new FieldProblem(v.getPropertyPath().toString(), codeOf(constraint));
  }

  private static int fieldRank(String field) {
    int rank = FIELDS.indexOf(field);
    return rank < 0 ? FIELDS.size() : rank;
  }

  /** 422 VALIDATION_FAILED, errors[] dans l'ordre des champs, messages localisés. */
  private void throwIfAny(Map<String, FieldProblem> problems) {
    if (problems.isEmpty()) {
      return;
    }
    List<FieldProblem> errors =
        problems.values().stream()
            .sorted(Comparator.comparingInt(p -> fieldRank(p.field())))
            .map(messages::field)
            .toList();
    throw new PatientException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ErrorCode.VALIDATION_FAILED,
        errors.get(0).message(),
        errors);
  }

  /** Propriété (403 / 404) puis patient actif (409 PATIENT_ARCHIVED), comme PatientService. */
  private void requireWritable(UUID patientId) {
    guard.requireOwned(patientId);
    PatientOwnership o =
        patients.findOwnership(patientId).orElseThrow(PatientAccessException::notFound);
    if (o.archivedAt() != null) {
      throw patientArchived();
    }
  }

  /** Même refus que les autres écritures du patient (PatientService), G Q69. */
  private static PatientException patientArchived() {
    return PatientException.of(
        HttpStatus.CONFLICT, "PATIENT_ARCHIVED", "Patient archivé : restaurez-le d'abord.");
  }

  private PatientProfileItem find(UUID patientId, UUID itemId) {
    return items
        .findByIdAndPatientId(itemId, patientId)
        .orElseThrow(
            () ->
                PatientException.of(
                    HttpStatus.NOT_FOUND,
                    PROFILE_ITEM_NOT_FOUND,
                    messages.domain(PROFILE_ITEM_NOT_FOUND)));
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(
            () -> new ProblemException(401, ErrorCode.UNAUTHENTICATED, "Session requise."));
  }
}
