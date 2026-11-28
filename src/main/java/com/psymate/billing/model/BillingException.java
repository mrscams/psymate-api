package com.psymate.billing.model;

import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.Problems;
import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * Erreur métier de la facturation, rendue en RFC 9457 par {@code BillingExceptionHandler} : le
 * statut et le code viennent d'ici, le message localisé (clé {@code billing.error.<code>}) du
 * handler. Codes de M15-05 (L5.1) : SERVICE_NOT_FOUND (404, aussi pour la prestation d'un autre
 * praticien), SERVICE_IS_DEFAULT et PATIENT_ARCHIVED (409), SERVICE_LIMIT_REACHED (422), et
 * VALIDATION_FAILED (422) pour une règle de champ que Jakarta Validation n'exprime pas.
 */
public class BillingException extends ProblemException {

  /** Nombre maximal de prestations non archivées par praticien (L5.1). */
  public static final int MAX_SERVICES = 50;

  private final String field;
  private final String fieldCode;

  private BillingException(int status, String code, String field, String fieldCode, String detail) {
    super(
        HttpStatusCode.valueOf(status),
        code,
        Problems.titleOf(HttpStatusCode.valueOf(status)),
        detail,
        field == null ? List.of() : List.of(new FieldProblem(field, fieldCode, detail)));
    this.field = field;
    this.fieldCode = fieldCode;
  }

  /** Champ de {@code errors[]} d'une erreur de validation métier, {@code null} sinon. */
  public String field() {
    return field;
  }

  /** Code de l'erreur de champ (INVALID_VALUE…), {@code null} sans champ. */
  public String fieldCode() {
    return fieldCode;
  }

  /** Prestation inexistante, archivée, ou d'un autre praticien. */
  public static BillingException serviceNotFound() {
    return new BillingException(404, "SERVICE_NOT_FOUND", null, null, "Prestation introuvable.");
  }

  /** 50 prestations non archivées au plus (L5.1). */
  public static BillingException serviceLimitReached() {
    return new BillingException(
        422,
        "SERVICE_LIMIT_REACHED",
        null,
        null,
        "Au plus " + MAX_SERVICES + " prestations actives.");
  }

  /** La prestation par défaut ne s'archive pas tant qu'il en reste d'autres (L5.1). */
  public static BillingException serviceIsDefault() {
    return new BillingException(
        409,
        "SERVICE_IS_DEFAULT",
        null,
        null,
        "La prestation par défaut ne peut pas être retirée.");
  }

  /** Patient archivé : son profil de facturation se lit mais ne s'écrit plus (L5.1). */
  public static BillingException patientArchived() {
    return new BillingException(409, "PATIENT_ARCHIVED", null, null, "Le patient est archivé.");
  }

  /**
   * Règle de champ hors Jakarta Validation : 422 VALIDATION_FAILED, errors[0] = {@code field}
   * {@code fieldCode}.
   */
  public static BillingException invalidField(String field, String fieldCode) {
    return new BillingException(422, "VALIDATION_FAILED", field, fieldCode, fieldCode);
  }
}
