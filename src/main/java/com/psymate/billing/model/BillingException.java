package com.psymate.billing.model;

import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.Problems;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * SESSION_NOT_BILLABLE (404) et SESSION_ALREADY_INVOICED (409). Codes de M15-08 (L5.3) :
 * INVOICE_NOT_FOUND (404), INVOICE_ISSUED (409), RATE_REQUIRED (422, {@code sessionEntryId} dans le
 * problème), SESSION_ALREADY_INVOICED avec {@code sessionEntryId} et PATIENT_ARCHIVED (409) à la
 * création d'un brouillon. Codes de M15-09 : BILLING_SETTINGS_INCOMPLETE (422, code racine avec
 * errors[]), EMPTY_INVOICE (422), INVOICE_NUMBER_CHANGED (409, {@code nextNumber}),
 * INVOICE_NOT_PAID (409), CREDIT_NOTE_NOT_DUPLICABLE (409), PDF_UNAVAILABLE (500). Une propriété de
 * problème ({@link #properties}) s'ajoute à la réponse ; une clé de message propre ({@link
 * #messageKey}) donne un texte adapté à la route.
 */
public class BillingException extends ProblemException {

  /** Nombre maximal de prestations non archivées par praticien (L5.1). */
  public static final int MAX_SERVICES = 50;

  private final String field;
  private final String fieldCode;
  private final List<FieldProblem> fieldProblems;
  private Long retryAfterSeconds;
  private final Map<String, Object> properties = new LinkedHashMap<>();
  private String messageKey;

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

  /** Propriétés ajoutées au problème RFC 9457 ({@code sessionEntryId}…), vide sinon. */
  public Map<String, Object> properties() {
    return Collections.unmodifiableMap(properties);
  }

  /**
   * Suffixe de la clé de message ({@code billing.error.<messageKey>}) : le code, ou une variante
   * propre à la route ({@code PATIENT_ARCHIVED.invoice}).
   */
  public String messageKey() {
    return messageKey == null ? code() : messageKey;
  }

  private BillingException with(String property, Object value) {
    properties.put(property, value);
    return this;
  }

  private BillingException message(String key) {
    this.messageKey = key;
    return this;
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

  /**
   * La séance est déjà dans une autre facture, brouillon compris (L3.3) : {@code sessionEntryId}
   * dans le problème (routes des factures, où la séance n'est pas dans l'URL).
   */
  public static BillingException sessionAlreadyInvoiced(UUID sessionEntryId) {
    return sessionAlreadyInvoiced()
        .with("sessionEntryId", sessionEntryId.toString())
        .message("SESSION_ALREADY_INVOICED.invoice");
  }

  /** Patient archivé : plus de nouveau brouillon (L3.3) ; {@code patientId} dans le problème. */
  public static BillingException patientArchivedForInvoice(UUID patientId) {
    return patientArchived()
        .with("patientId", patientId.toString())
        .message("PATIENT_ARCHIVED.invoice");
  }

  /** Facture inexistante ou d'un autre praticien (L5 : 404). */
  public static BillingException invoiceNotFound() {
    return new BillingException(404, "INVOICE_NOT_FOUND", null, null, "Facture introuvable.");
  }

  /** Facture émise, annulée, ou avoir : plus aucune modification ni suppression (G Q88). */
  public static BillingException invoiceIssued() {
    return new BillingException(
        409,
        "INVOICE_ISSUED",
        null,
        null,
        "Cette facture est émise : elle ne se modifie plus et ne se supprime pas.");
  }

  /**
   * Séance sans tarif (G Q87, {@code rateSource = none}) : elle ne peut pas entrer dans une facture
   * ; {@code sessionEntryId} dans le problème.
   */
  public static BillingException rateRequired(UUID sessionEntryId) {
    return new BillingException(422, "RATE_REQUIRED", null, null, "Cette séance n'a pas de tarif.")
        .with("sessionEntryId", sessionEntryId.toString());
  }

  /**
   * Réglages incomplets à l'émission (L3.3) : 422 BILLING_SETTINGS_INCOMPLETE (code racine, pas
   * VALIDATION_FAILED) avec errors[] = un champ manquant par élément ({@code siret}, {@code
   * addressLines}, {@code service}, comme {@code missing} des réglages), code REQUIRED. Le
   * brouillon reste intact.
   */
  public static BillingException settingsIncomplete(List<String> missing) {
    if (missing.isEmpty()) {
      throw new IllegalArgumentException("au moins un champ manquant");
    }
    return new BillingException(
        422,
        "BILLING_SETTINGS_INCOMPLETE",
        "Réglages de facturation incomplets.",
        missing.stream().map(m -> new FieldProblem(m, "REQUIRED")).toList());
  }

  /** Brouillon sans ligne, ou dont le sous-total n'est pas positif, à l'émission (L3.2). */
  public static BillingException emptyInvoice() {
    return new BillingException(
        422, "EMPTY_INVOICE", null, null, "La facture est vide ou son total n'est pas positif.");
  }

  /**
   * Le numéro attendu n'est plus le suivant (G Q89, L3.3) : rien n'est émis ; {@code nextNumber}
   * dans le problème.
   */
  public static BillingException invoiceNumberChanged(String nextNumber) {
    return new BillingException(
            409, "INVOICE_NUMBER_CHANGED", null, null, "Le numéro de la facture a changé.")
        .with("nextNumber", nextNumber);
  }

  /** Facture acquittée demandée alors que la facture n'est pas entièrement réglée (L5.3). */
  public static BillingException invoiceNotPaid() {
    return new BillingException(
        409, "INVOICE_NOT_PAID", null, null, "Cette facture n'est pas entièrement réglée.");
  }

  /** Un avoir ne se duplique pas : seule une facture donne un nouveau brouillon (M15-09). */
  public static BillingException creditNoteNotDuplicable() {
    return new BillingException(
        409, "CREDIT_NOTE_NOT_DUPLICABLE", null, null, "Un avoir ne se duplique pas.");
  }

  /**
   * PDF impossible à produire (rendu) ou à relire (fichier rangé) : 500 PDF_UNAVAILABLE, détail
   * générique ; à l'émission, rien n'est émis.
   */
  public static BillingException pdfUnavailable() {
    return new BillingException(
        500, "PDF_UNAVAILABLE", null, null, "Le PDF de la facture est indisponible.");
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
