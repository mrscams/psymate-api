package com.psymate.billing.service;

import com.psymate.billing.dto.BillingEmailTemplateRequest;
import com.psymate.billing.dto.BillingEmailTemplatesRequest;
import com.psymate.billing.dto.BillingNumberingRequest;
import com.psymate.billing.dto.BillingPaymentTermsRequest;
import com.psymate.billing.dto.BillingSettingsAssembler;
import com.psymate.billing.dto.BillingSettingsDto;
import com.psymate.billing.dto.BillingSettingsRequest;
import com.psymate.billing.dto.BillingVatRequest;
import com.psymate.billing.model.BillingCounter;
import com.psymate.billing.model.BillingCounterId;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingIssuer;
import com.psymate.billing.model.BillingNumbering;
import com.psymate.billing.model.BillingSettings;
import com.psymate.billing.model.Siret;
import com.psymate.billing.repository.BillingCounterRepository;
import com.psymate.billing.repository.BillingServiceRepository;
import com.psymate.billing.repository.BillingSettingsRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.IssuerView;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réglages de facturation du praticien (M15-06, L5.1) : lecture et remplacement. Le praticien et le
 * cabinet viennent de la session ; la clé de la ligne est le praticien, si bien qu'aucun praticien
 * ne lit ni n'écrit les réglages d'un autre.
 *
 * <p>Lecture : sans ligne, valeurs par défaut ; adresse et e-mail proposés depuis l'identité
 * d'émetteur (identity, M15-03) tant qu'ils ne sont pas enregistrés ; mention de TVA tirée de
 * messages_fr selon le régime (G Q92) ; prochains numéros calculés depuis {@code billing_counter}
 * et l'année en cours à Paris (G Q89) ; préfixes verrouillés dès qu'un numéro a été attribué ;
 * {@code missing} = ce qui empêche d'émettre.
 *
 * <p>Écriture : règles de champ (SIRET_INVALID, VAT_RATE_REQUIRED, VAT_NUMBER_REQUIRED,
 * INVALID_VALUE…) toutes rapportées ensemble en 422, puis 409 NUMBERING_LOCKED si un préfixe change
 * après une émission. Les écritures d'un même praticien passent par son verrou consultatif. Rien
 * n'est journalisé.
 */
@Service
public class BillingSettingsService {

  /** Codes de {@code missing}, dans l'ordre de la réponse. */
  static final String MISSING_SIRET = "siret";

  static final String MISSING_ADDRESS = "addressLines";
  static final String MISSING_SERVICE = "service";

  /** Natures de modèle d'e-mail et leurs parties. */
  static final List<String> TEMPLATE_KINDS = List.of("invoice", "reminder");

  static final List<String> TEMPLATE_PARTS = List.of("subject", "body");

  private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)*");
  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
  private static final Pattern PHONE = Pattern.compile("[0-9+().\\s-]+");
  private static final Pattern VAT_NUMBER = Pattern.compile("[A-Z]{2}[A-Z0-9]{2,18}");
  private static final int VAT_NUMBER_MAX = 20;

  private final BillingSettingsRepository settings;
  private final BillingCounterRepository counters;
  private final BillingServiceRepository services;
  private final PractitionerFacade practitioners;
  private final BillingTexts texts;
  private final BillingSettingsAssembler assembler;
  private final Clock clock;

  public BillingSettingsService(
      BillingSettingsRepository settings,
      BillingCounterRepository counters,
      BillingServiceRepository services,
      PractitionerFacade practitioners,
      BillingTexts texts,
      BillingSettingsAssembler assembler,
      Clock clock) {
    this.settings = settings;
    this.counters = counters;
    this.services = services;
    this.practitioners = practitioners;
    this.texts = texts;
    this.assembler = assembler;
    this.clock = clock;
  }

  // ---- règles pures (testées unitairement)

  /**
   * Ce qui empêche d'émettre, dans l'ordre : SIRET absent, aucune adresse (ni enregistrée ni
   * proposée), aucune prestation active.
   */
  static List<String> missing(String siret, String effectiveAddress, boolean hasActiveService) {
    List<String> missing = new ArrayList<>();
    if (siret == null || siret.isBlank()) {
      missing.add(MISSING_SIRET);
    }
    if (effectiveAddress == null || effectiveAddress.isBlank()) {
      missing.add(MISSING_ADDRESS);
    }
    if (!hasActiveService) {
      missing.add(MISSING_SERVICE);
    }
    return List.copyOf(missing);
  }

  /**
   * Adresse proposée depuis l'identité d'émetteur : rue, puis code postal et ville, sur deux lignes
   * ; null si rien n'est renseigné.
   */
  static String proposedAddress(IssuerView issuer) {
    if (issuer == null) {
      return null;
    }
    String cityLine =
        Stream.of(issuer.addressPostalCode(), issuer.addressCity())
            .map(BillingSettingsService::blankToNull)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" "));
    String address =
        Stream.of(blankToNull(issuer.addressStreet()), blankToNull(cityLine))
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    return address.isEmpty() ? null : address;
  }

  /** Texte sans espaces de bord ; vide → null. */
  static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /** Prochain numéro d'une suite : dernier numéro attribué plus un. */
  static int next(int lastNumber) {
    return lastNumber + 1;
  }

  /** Vrai si l'un des préfixes demandés diffère du préfixe en vigueur. */
  static boolean prefixesChange(
      String currentInvoice,
      String currentCredit,
      String requestedInvoice,
      String requestedCredit) {
    return !currentInvoice.equals(requestedInvoice) || !currentCredit.equals(requestedCredit);
  }

  // ---- lecture

  @Transactional(readOnly = true)
  public BillingSettingsDto get() {
    CurrentPractitioner me = me();
    BillingSettings current =
        settings
            .findById(me.id())
            .orElseGet(() -> BillingSettings.defaults(me.id(), me.tenantId(), now()));
    return view(me, current);
  }

  /**
   * Émetteur d'une facture selon les réglages du moment (bloc {@code issuer} d'un brouillon,
   * M15-08, et instantané de l'émission, M15-09) : identité lue dans identity, adresse et e-mail
   * effectifs (enregistrés, sinon proposés), mention de TVA du régime, taux en régime assujetti,
   * modalités ; préfixe, échéance et mention de bas de page par défaut d'un nouveau brouillon.
   */
  @Transactional(readOnly = true)
  public BillingIssuer issuerOfTheMoment(CurrentPractitioner me) {
    BillingSettings current =
        settings
            .findById(me.id())
            .orElseGet(() -> BillingSettings.defaults(me.id(), me.tenantId(), now()));
    IssuerView issuer = practitioners.issuerOf(me.id()).orElse(null);
    String profession = issuer != null ? issuer.profession() : me.profession();
    return new BillingIssuer(
        issuer == null ? me.displayName() : issuer.displayName(),
        texts.title(profession),
        issuer == null ? null : issuer.professionalNumber(),
        current.getSiret(),
        effectiveAddress(current, issuer),
        effectiveEmail(current, issuer, me),
        current.getDisplayPhone(),
        current.getVatRegime(),
        texts.vatMention(current.getVatRegime()),
        current.getVatRateBp(),
        current.getVatNumber(),
        current.getChequePayee(),
        current.getAcceptedModes(),
        current.isShowBankDetails() && current.getIbanLast4() != null,
        current.getInvoicePrefix(),
        current.getDueTerm(),
        current.getFooterNote());
  }

  /**
   * Ce qui empêche d'émettre (M15-09) : même calcul que {@code missing} des réglages, sur
   * l'émetteur du moment ({@link #issuerOfTheMoment}), dont l'adresse est déjà l'adresse effective
   * (enregistrée, sinon proposée par identity).
   */
  @Transactional(readOnly = true)
  public List<String> missingToIssue(UUID practitionerId, BillingIssuer issuer) {
    return missing(
        issuer.siret(),
        issuer.addressLines(),
        services.existsByPractitionerIdAndArchivedAtIsNull(practitionerId));
  }

  /**
   * Taux de TVA des réglages du moment en régime assujetti, null sinon : total des brouillons dans
   * la liste des factures (M15-08), sans lire l'identité du praticien.
   */
  @Transactional(readOnly = true)
  public Integer draftVatRateBp(UUID practitionerId) {
    return settings
        .findById(practitionerId)
        .filter(s -> BillingSettings.LIABLE.equals(s.getVatRegime()))
        .map(BillingSettings::getVatRateBp)
        .orElse(null);
  }

  /**
   * Prochain numéro de facture de la suite d'une année ({@code nextNumberPreview} d'un brouillon,
   * M15-08) : dernier numéro attribué plus un, formaté avec le préfixe donné. Simple aperçu : le
   * numéro n'est attribué qu'à l'émission, sous verrou (M15-09).
   */
  @Transactional(readOnly = true)
  public String nextInvoiceNumber(UUID practitionerId, String prefix, int year) {
    return BillingNumbering.format(
        prefix, year, next(last(practitionerId, BillingNumbering.INVOICE, year)));
  }

  // ---- écriture

  @Transactional
  public BillingSettingsDto replace(BillingSettingsRequest request) {
    CurrentPractitioner me = me();
    settings.lockSettings(me.id());
    OffsetDateTime now = now();
    BillingSettings current =
        settings
            .findById(me.id())
            .orElseGet(() -> BillingSettings.defaults(me.id(), me.tenantId(), now));
    Resolved r = resolve(request);
    if (!r.problems().isEmpty()) {
      throw BillingException.invalidFields(r.problems());
    }
    if (prefixesChange(
            current.getInvoicePrefix(),
            current.getCreditNotePrefix(),
            r.invoicePrefix(),
            r.creditNotePrefix())
        && counters.anyNumberIssued(me.id())) {
      throw BillingException.numberingLocked();
    }
    current.replaceContact(r.siret(), r.addressLines(), r.displayEmail(), r.displayPhone(), now);
    current.replaceVat(r.regime(), r.rateBp(), r.vatNumber(), now);
    current.replacePrefixes(r.invoicePrefix(), r.creditNotePrefix(), now);
    current.replaceTerms(
        r.dueTerm(),
        r.chequePayee(),
        r.acceptedModes(),
        r.showBankDetails(),
        r.footerNote(),
        storedTemplates(request.emailTemplates()),
        now);
    return view(me, settings.saveAndFlush(current));
  }

  /** Valeurs normalisées d'une requête et problèmes de champ, dans l'ordre des champs. */
  record Resolved(
      String siret,
      String addressLines,
      String displayEmail,
      String displayPhone,
      String regime,
      Integer rateBp,
      String vatNumber,
      String invoicePrefix,
      String creditNotePrefix,
      String dueTerm,
      String chequePayee,
      List<String> acceptedModes,
      boolean showBankDetails,
      String footerNote,
      List<FieldProblem> problems) {}

  /**
   * Normalise la requête et applique les règles que Jakarta Validation n'exprime pas (les tailles
   * et le taux sont déjà contrôlés). Pure : testée unitairement.
   */
  static Resolved resolve(BillingSettingsRequest request) {
    List<FieldProblem> problems = new ArrayList<>();

    String siret = Siret.normalize(blankToNull(request.siret()));
    if (siret != null && !Siret.isValid(siret)) {
      problems.add(new FieldProblem("siret", "SIRET_INVALID"));
    }
    String addressLines = blankToNull(request.addressLines());
    String displayEmail = blankToNull(request.displayEmail());
    if (displayEmail != null && !EMAIL.matcher(displayEmail).matches()) {
      problems.add(new FieldProblem("displayEmail", "INVALID_VALUE"));
    }
    String displayPhone = blankToNull(request.displayPhone());
    if (displayPhone != null && !PHONE.matcher(displayPhone).matches()) {
      problems.add(new FieldProblem("displayPhone", "INVALID_VALUE"));
    }

    BillingVatRequest vat = request.vat();
    String regime = vat == null ? null : blankToNull(vat.regime());
    regime = regime == null ? "health_exempt" : regime;
    Integer rateBp = vat == null ? null : vat.rateBp();
    String vatNumber = vat == null ? null : normalizeVatNumber(vat.vatNumber());
    if (!BillingSettings.VAT_REGIMES.contains(regime)) {
      problems.add(new FieldProblem("vat.regime", "INVALID_VALUE"));
    } else if (BillingSettings.LIABLE.equals(regime)) {
      if (rateBp == null) {
        problems.add(new FieldProblem("vat.rateBp", "VAT_RATE_REQUIRED"));
      }
      if (vatNumber == null) {
        problems.add(new FieldProblem("vat.vatNumber", "VAT_NUMBER_REQUIRED"));
      } else if (vatNumber.length() > VAT_NUMBER_MAX) {
        problems.add(new FieldProblem("vat.vatNumber", "OUT_OF_RANGE"));
      } else if (!VAT_NUMBER.matcher(vatNumber).matches()) {
        problems.add(new FieldProblem("vat.vatNumber", "INVALID_VALUE"));
      }
    }

    BillingNumberingRequest numbering = request.numbering();
    String invoicePrefix = numbering == null ? null : blankToNull(numbering.invoicePrefix());
    String creditNotePrefix = numbering == null ? null : blankToNull(numbering.creditNotePrefix());
    invoicePrefix = invoicePrefix == null ? BillingNumbering.DEFAULT_INVOICE_PREFIX : invoicePrefix;
    creditNotePrefix =
        creditNotePrefix == null ? BillingNumbering.DEFAULT_CREDIT_NOTE_PREFIX : creditNotePrefix;
    boolean invoicePrefixValid = PREFIX.matcher(invoicePrefix).matches();
    if (!invoicePrefixValid) {
      problems.add(new FieldProblem("numbering.invoicePrefix", "INVALID_VALUE"));
    }
    if (!PREFIX.matcher(creditNotePrefix).matches()
        || (invoicePrefixValid && invoicePrefix.equalsIgnoreCase(creditNotePrefix))) {
      // Deux préfixes égaux donneraient le même numéro à une facture et à un avoir.
      problems.add(new FieldProblem("numbering.creditNotePrefix", "INVALID_VALUE"));
    }

    BillingPaymentTermsRequest payment = request.payment();
    String dueTerm = payment == null ? null : blankToNull(payment.dueTerm());
    dueTerm = dueTerm == null ? "on_receipt" : dueTerm;
    if (!BillingSettings.DUE_TERMS.contains(dueTerm)) {
      problems.add(new FieldProblem("payment.dueTerm", "INVALID_VALUE"));
    }
    String chequePayee = payment == null ? null : blankToNull(payment.chequePayee());
    List<String> acceptedModes =
        payment == null || payment.acceptedModes() == null
            ? BillingSettings.DEFAULT_ACCEPTED_MODES
            : payment.acceptedModes().stream().filter(Objects::nonNull).distinct().toList();
    if (acceptedModes.isEmpty() || !BillingSettings.PAYMENT_MODES.containsAll(acceptedModes)) {
      problems.add(new FieldProblem("payment.acceptedModes", "INVALID_VALUE"));
    }
    boolean showBankDetails = payment != null && Boolean.TRUE.equals(payment.showBankDetails());
    String footerNote = blankToNull(request.footerNote());

    BillingEmailTemplatesRequest templates = request.emailTemplates();
    if (templates != null) {
      subjectOnOneLine(templates.invoice(), "emailTemplates.invoice.subject", problems);
      subjectOnOneLine(templates.reminder(), "emailTemplates.reminder.subject", problems);
    }

    return new Resolved(
        siret,
        addressLines,
        displayEmail,
        displayPhone,
        regime,
        rateBp,
        vatNumber,
        invoicePrefix,
        creditNotePrefix,
        dueTerm,
        chequePayee,
        acceptedModes,
        showBankDetails,
        footerNote,
        List.copyOf(problems));
  }

  /** Un objet d'e-mail tient sur une ligne : un saut de ligne ouvrirait un en-tête. */
  private static void subjectOnOneLine(
      BillingEmailTemplateRequest template, String field, List<FieldProblem> problems) {
    if (template != null
        && template.subject() != null
        && (template.subject().indexOf('\n') >= 0 || template.subject().indexOf('\r') >= 0)) {
      problems.add(new FieldProblem(field, "INVALID_VALUE"));
    }
  }

  /** Numéro de TVA sans espaces ni points, en majuscules ; vide → null. */
  static String normalizeVatNumber(String raw) {
    String value = blankToNull(raw);
    return value == null ? null : value.replaceAll("[\\s.]+", "").toUpperCase(Locale.ROOT);
  }

  /**
   * Modèles à ranger : seuls les textes non vides qui diffèrent du texte par défaut ; {@code {}}
   * quand tout est par défaut, si bien qu'un texte par défaut corrigé plus tard s'applique.
   */
  Map<String, Object> storedTemplates(BillingEmailTemplatesRequest request) {
    Map<String, Object> stored = new LinkedHashMap<>();
    if (request == null) {
      return stored;
    }
    for (String kind : TEMPLATE_KINDS) {
      BillingEmailTemplateRequest template =
          "invoice".equals(kind) ? request.invoice() : request.reminder();
      if (template == null) {
        continue;
      }
      Map<String, Object> parts = new LinkedHashMap<>();
      for (String part : TEMPLATE_PARTS) {
        String value = "subject".equals(part) ? template.subject() : template.body();
        String text = value == null ? null : value.strip();
        if (text != null && !text.isEmpty() && !text.equals(texts.template(kind, part))) {
          parts.put(part, text);
        }
      }
      if (!parts.isEmpty()) {
        stored.put(kind, parts);
      }
    }
    return stored;
  }

  /** Modèle effectif : texte enregistré, à défaut texte par défaut (français). */
  private Map<String, Map<String, String>> effectiveTemplates(Map<String, Object> stored) {
    Map<String, Map<String, String>> effective = new LinkedHashMap<>();
    for (String kind : TEMPLATE_KINDS) {
      Map<String, String> parts = new LinkedHashMap<>();
      Object kindValue = stored.get(kind);
      for (String part : TEMPLATE_PARTS) {
        Object value = kindValue instanceof Map<?, ?> map ? map.get(part) : null;
        parts.put(part, value instanceof String s && !s.isBlank() ? s : texts.template(kind, part));
      }
      effective.put(kind, parts);
    }
    return effective;
  }

  // ---- vue

  private BillingSettingsDto view(CurrentPractitioner me, BillingSettings current) {
    IssuerView issuer = practitioners.issuerOf(me.id()).orElse(null);
    String address = effectiveAddress(current, issuer);
    String email = effectiveEmail(current, issuer, me);
    int year = BillingNumbering.currentYear(clock);
    String nextInvoice =
        BillingNumbering.format(
            current.getInvoicePrefix(), year, next(last(me.id(), BillingNumbering.INVOICE, year)));
    String nextCredit =
        BillingNumbering.format(
            current.getCreditNotePrefix(),
            year,
            next(last(me.id(), BillingNumbering.CREDIT_NOTE, year)));
    String profession = issuer != null ? issuer.profession() : me.profession();
    return assembler.toDto(
        current,
        issuer == null ? me.displayName() : issuer.displayName(),
        texts.title(profession),
        issuer == null ? null : issuer.professionalNumber(),
        address,
        email,
        texts.vatMention(current.getVatRegime()),
        nextInvoice,
        nextCredit,
        counters.anyNumberIssued(me.id()),
        effectiveTemplates(current.getEmailTemplates()),
        missing(
            current.getSiret(),
            address,
            services.existsByPractitionerIdAndArchivedAtIsNull(me.id())));
  }

  /** Adresse effective : enregistrée, sinon proposée depuis identity. */
  private static String effectiveAddress(BillingSettings current, IssuerView issuer) {
    return current.getAddressLines() != null ? current.getAddressLines() : proposedAddress(issuer);
  }

  /** E-mail affiché effectif : enregistré, sinon celui du compte. */
  private static String effectiveEmail(
      BillingSettings current, IssuerView issuer, CurrentPractitioner me) {
    if (current.getDisplayEmail() != null) {
      return current.getDisplayEmail();
    }
    return issuer != null ? issuer.email() : me.email();
  }

  private int last(UUID practitionerId, String kind, int year) {
    return counters
        .findById(new BillingCounterId(practitionerId, kind, year))
        .map(BillingCounter::getLastNumber)
        .orElse(0);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
