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
 * VALIDATION_FAILED (422) pour une règle de champ que Jakarta Validation n'exprime pas. Codes de
 * M15-06 : NUMBERING_LOCKED (409), PASSWORD_INVALID (403), TOO_MANY_ATTEMPTS (429), et en
 * VALIDATION_FAILED les codes de champ SIRET_INVALID, VAT_RATE_REQUIRED, VAT_NUMBER_REQUIRED,
 * IBAN_INVALID, OUT_OF_RANGE, INVALID_VALUE ({@link #invalidFields}). Codes de M15-07 (L5.2) :
 * SESSION_NOT_BILLABLE (404) et SESSION_ALREADY_INVOICED (409).
 */
public class BillingException extends ProblemException {

  /** Nombre maximal de prestations non archivées par praticien (L5.1). */
  public static final int MAX_SERVICES = 50;

  private final String field;
  private final String fieldCode;
  private final List<FieldProblem> fieldProblems;
  private Long retryAfterSeconds;

  private BillingException(int status, String code, String field, String fieldCode, String detail) {
    this(
        status,
        code,
        detail,
        field == null ? List.of() : List.of(new FieldProblem(field, fieldCode)));
  }

  private BillingException(int status, String code, String detail, List<FieldProblem> problems) {
    super(
        HttpStatusCode.valueOf(status),
        code,
        Problems.titleOf(HttpStatusCode.valueOf(status)),
        detail,
        problems.stream().map(p -> new FieldProblem(p.field(), p.code(), p.code())).toList());
    this.fieldProblems = List.copyOf(problems);
    this.field = problems.isEmpty() ? null : problems.getFirst().field();
    this.fieldCode = problems.isEmpty() ? null : problems.getFirst().code();
  }

  /** Attente conseillée en secondes (en-tête Retry-After d'un 429), null sinon. */
  public Long retryAfterSeconds() {
    return retryAfterSeconds;
  }

  /** Erreurs de champ (sans message : le handler les localise), vide hors validation. */
  public List<FieldProblem> fieldProblems() {
    return fieldProblems;
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

  /**
   * Règles de champ hors Jakarta Validation, toutes à la fois : 422 VALIDATION_FAILED, errors[] =
   * {@code problems} dans l'ordre (codes de champ SIRET_INVALID, VAT_RATE_REQUIRED…).
   */
  public static BillingException invalidFields(List<FieldProblem> problems) {
    if (problems.isEmpty()) {
      throw new IllegalArgumentException("au moins une erreur de champ");
    }
    return new BillingException(422, "VALIDATION_FAILED", "VALIDATION_FAILED", problems);
  }

  /**
   * Séance inconnue de la facturation et qu'elle ne peut pas créer à la demande (G Q86) : séance
   * inexistante, ouverte, archivée, d'accueil, de contact, ou d'un autre praticien.
   */
  public static BillingException sessionNotBillable() {
    return new BillingException(
        404, "SESSION_NOT_BILLABLE", null, null, "Cette séance ne peut pas être facturée.");
  }

  /** La séance est déjà dans une facture, brouillon compris (L3.1). */
  public static BillingException sessionAlreadyInvoiced() {
    return new BillingException(
        409, "SESSION_ALREADY_INVOICED", null, null, "Cette séance est déjà facturée.");
  }

  /** Un préfixe de numérotation change alors qu'un numéro a déjà été attribué (G Q89). */
  public static BillingException numberingLocked() {
    return new BillingException(
        409,
        "NUMBERING_LOCKED",
        null,
        null,
        "Les préfixes de numérotation ne changent plus après la première émission.");
  }

  /** Le mot de passe de confirmation ne correspond pas au compte (G Q95). */
  public static BillingException passwordInvalid() {
    return new BillingException(403, "PASSWORD_INVALID", null, null, "Mot de passe incorrect.");
  }

  /**
   * Trop d'essais de mot de passe (5 échecs en 15 minutes, M15-06) : 429 TOO_MANY_ATTEMPTS, le mot
   * de passe n'est pas vérifié ; {@code retryAfterSeconds} jusqu'à la sortie de la fenêtre.
   */
  public static BillingException tooManyAttempts(long retryAfterSeconds) {
    BillingException e =
        new BillingException(
            429, "TOO_MANY_ATTEMPTS", null, null, "Trop d'essais : réessayez plus tard.");
    e.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    return e;
  }
}
