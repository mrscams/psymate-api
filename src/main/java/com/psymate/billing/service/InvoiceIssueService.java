package com.psymate.billing.service;

import com.psymate.billing.dto.InvoiceAlreadyPaidRequest;
import com.psymate.billing.dto.InvoiceDto;
import com.psymate.billing.dto.IssueInvoiceRequest;
import com.psymate.billing.model.BillingCounter;
import com.psymate.billing.model.BillingCounterId;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingIssuer;
import com.psymate.billing.model.BillingNumbering;
import com.psymate.billing.model.Invoice;
import com.psymate.billing.model.InvoiceEvent;
import com.psymate.billing.model.InvoiceLine;
import com.psymate.billing.model.InvoiceTotals;
import com.psymate.billing.model.Payment;
import com.psymate.billing.repository.BillingCounterRepository;
import com.psymate.billing.repository.BillingSettingsRepository;
import com.psymate.billing.repository.InvoiceEventRepository;
import com.psymate.billing.repository.InvoiceLineRepository;
import com.psymate.billing.repository.InvoiceRepository;
import com.psymate.billing.repository.PaymentRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Émission d'une facture (M15-09, L5.3 {@code issue} ; G Q88, Q89, Q90, Q92, Q93), dans une seule
 * transaction :
 *
 * <ol>
 *   <li>verrou de la facture ({@code select … for update} ; 409 INVOICE_ISSUED si elle n'est plus
 *       un brouillon), puis verrou consultatif des réglages ({@code lockSettings}) : ni préfixe, ni
 *       régime, ni adresse ne changent entre leur lecture et l'attribution du numéro, et deux
 *       émissions d'un même praticien se suivent ;
 *   <li>contrôles : réglages complets (422 BILLING_SETTINGS_INCOMPLETE, même calcul que {@code
 *       missing}), sous-total positif (422 EMPTY_INVOICE), ligne de séance à prix positif (422
 *       RATE_REQUIRED), puis les champs (422 VALIDATION_FAILED : date d'émission ni future ni
 *       antérieure à la dernière émise de la suite, destinataire, mode et date du paiement) ;
 *   <li>compteur {@code billing_counter} de la suite (praticien, facture, année de la date
 *       d'émission) créé au besoin puis verrouillé ({@code select … for update}) ; si {@code
 *       expectedNumber} n'est pas le suivant : 409 INVOICE_NUMBER_CHANGED avec {@code nextNumber},
 *       rien n'est émis ;
 *   <li>écriture : numéro, statut {@code issued}, échéance effective (L3.2), instantané de
 *       l'émetteur ({@link BillingIssuer#toSnapshot}), montants figés, événement {@code issued} ;
 *       {@code alreadyPaid} : paiement du total, {@code paid_cents}, événement {@code
 *       payment_recorded} ; les séances restent {@code invoiced} ;
 *   <li>PDF rendu puis rangé chiffré ({@link InvoicePdfService}) avant la validation : si le rendu
 *       ou le rangement échoue, la transaction est annulée et rien n'est émis.
 * </ol>
 *
 * <p>Date d'émission : celle du brouillon ; une date passée est permise (une facture établie après
 * la séance), une date future non, ni une date antérieure à la dernière facture émise de la même
 * suite (la suite doit rester chronologique). Ni nom, ni montant journalisé.
 */
@Service
public class InvoiceIssueService {

  private final InvoiceRepository invoices;
  private final InvoiceLineRepository lines;
  private final InvoiceEventRepository events;
  private final PaymentRepository payments;
  private final BillingSettingsRepository settingsRows;
  private final BillingCounterRepository counters;
  private final BillingSettingsService settings;
  private final InvoicePdfService pdfs;
  private final InvoiceService reader;
  private final Clock clock;

  public InvoiceIssueService(
      InvoiceRepository invoices,
      InvoiceLineRepository lines,
      InvoiceEventRepository events,
      PaymentRepository payments,
      BillingSettingsRepository settingsRows,
      BillingCounterRepository counters,
      BillingSettingsService settings,
      InvoicePdfService pdfs,
      InvoiceService reader,
      Clock clock) {
    this.invoices = invoices;
    this.lines = lines;
    this.events = events;
    this.payments = payments;
    this.settingsRows = settingsRows;
    this.counters = counters;
    this.settings = settings;
    this.pdfs = pdfs;
    this.reader = reader;
    this.clock = clock;
  }

  /** Émet le brouillon et répond la facture émise (forme {@code Invoice}). */
  @Transactional
  public InvoiceDto issue(UUID id, IssueInvoiceRequest request) {
    CurrentPractitioner me = me();
    Invoice invoice =
        invoices.findForUpdate(id, me.id()).orElseThrow(BillingException::invoiceNotFound);
    invoice.requireDraft();
    settingsRows.lockSettings(me.id());

    BillingIssuer issuer = settings.issuerOfTheMoment(me);
    List<String> missing = settings.missingToIssue(me.id(), issuer);
    if (!missing.isEmpty()) {
      throw BillingException.settingsIncomplete(missing);
    }
    List<InvoiceLine> rows = lines.findByInvoiceIdOrderByPosition(invoice.getId());
    InvoiceTotals totals =
        InvoiceTotals.of(
            rows.stream().map(InvoiceLine::getAmountCents).toList(),
            issuer.vatRegime(),
            issuer.vatRateBp());
    if (totals.subtotal() <= 0L) {
      throw BillingException.emptyInvoice();
    }
    for (InvoiceLine row : rows) {
      if (row.getSessionEntryId() != null && row.getUnitPriceCents() <= 0) {
        throw BillingException.rateRequired(row.getSessionEntryId());
      }
    }

    LocalDate today = today();
    LocalDate issueDate = invoice.getIssueDate();
    int year = BillingNumbering.yearOf(issueDate);
    LocalDate lastIssued =
        invoices.lastIssueDate(
            me.id(), Invoice.KIND_INVOICE, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    List<FieldProblem> problems = new ArrayList<>();
    if (invoice.getRecipientName() == null || invoice.getRecipientName().isBlank()) {
      problems.add(new FieldProblem("recipient.name", "REQUIRED"));
    }
    problems.addAll(issueDateProblems(issueDate, today, lastIssued));
    problems.addAll(alreadyPaidProblems(request.alreadyPaid(), issuer.acceptedModes(), today));
    if (!problems.isEmpty()) {
      throw BillingException.invalidFields(problems);
    }

    counters.ensureExists(me.id(), BillingNumbering.INVOICE, year);
    BillingCounter counter =
        counters
            .findForUpdate(new BillingCounterId(me.id(), BillingNumbering.INVOICE, year))
            .orElseThrow(() -> new IllegalStateException("compteur absent après sa création"));
    String number =
        BillingNumbering.next(issuer.invoicePrefix(), issueDate, counter.getLastNumber());
    if (!number.equals(request.expectedNumber().strip())) {
      throw BillingException.invoiceNumberChanged(number);
    }
    counter.assignNext();

    OffsetDateTime now = now();
    LocalDate dueOn = InvoiceTotals.dueOn(invoice.getDueTerm(), issueDate, invoice.getDueOn());
    String key = InvoicePdfService.storageKey(invoice.getId());
    invoice.issue(number, issuer.toSnapshot(), dueOn, totals, key, now);
    Map<String, Object> issued = new LinkedHashMap<>();
    issued.put("number", number);
    events.save(
        new InvoiceEvent(UUID.randomUUID(), invoice.getId(), InvoiceEvent.ISSUED, now, issued));

    List<Payment> paid = new ArrayList<>();
    InvoiceAlreadyPaidRequest asked = request.alreadyPaid();
    if (asked != null) {
      Payment payment =
          payments.save(
              Payment.received(
                  UUID.randomUUID(),
                  me.tenantId(),
                  me.id(),
                  invoice.getId(),
                  asked.mode(),
                  totals.total(),
                  asked.receivedOn(),
                  BillingSettingsService.blankToNull(asked.reference()),
                  now));
      invoice.recordPayment(payment.getAmountCents(), now);
      Map<String, Object> recorded = new LinkedHashMap<>();
      recorded.put("paymentId", payment.getId().toString());
      recorded.put("mode", payment.getMode());
      // Une microseconde après l'émission : l'historique se lit dans l'ordre des faits.
      events.save(
          new InvoiceEvent(
              UUID.randomUUID(),
              invoice.getId(),
              InvoiceEvent.PAYMENT_RECORDED,
              now.plus(1, ChronoUnit.MICROS),
              recorded));
      paid.add(payment);
    }
    invoices.flush();

    // Rendu une fois, à l'émission, avec l'instantané : un échec annule toute l'émission.
    byte[] pdf =
        pdfs.renderIssued(
            invoice, rows, BillingIssuer.fromSnapshot(invoice.getIssuerSnapshot()), paid);
    pdfs.store(key, pdf);
    return reader.read(invoice, me);
  }

  // ---- règles pures (testées unitairement)

  /**
   * Date d'émission : pas dans le futur (OUT_OF_RANGE), pas avant la dernière date émise de la même
   * suite (INVALID_VALUE) : la numérotation continue suit l'ordre des dates.
   */
  static List<FieldProblem> issueDateProblems(
      LocalDate issueDate, LocalDate today, LocalDate lastIssuedOfSeries) {
    if (issueDate.isAfter(today)) {
      return List.of(new FieldProblem("issueDate", "OUT_OF_RANGE"));
    }
    if (lastIssuedOfSeries != null && issueDate.isBefore(lastIssuedOfSeries)) {
      return List.of(new FieldProblem("issueDate", "INVALID_VALUE"));
    }
    return List.of();
  }

  /**
   * Paiement à l'émission : mode accepté par les réglages ou {@code other} (MODE_NOT_ACCEPTED),
   * date de réception pas dans le futur (OUT_OF_RANGE). La forme (mode connu, date présente) est
   * déjà contrôlée par Jakarta Validation.
   */
  static List<FieldProblem> alreadyPaidProblems(
      InvoiceAlreadyPaidRequest paid, List<String> acceptedModes, LocalDate today) {
    if (paid == null) {
      return List.of();
    }
    List<FieldProblem> problems = new ArrayList<>();
    if (!Payment.MODE_OTHER.equals(paid.mode()) && !acceptedModes.contains(paid.mode())) {
      problems.add(new FieldProblem("alreadyPaid.mode", "MODE_NOT_ACCEPTED"));
    }
    if (paid.receivedOn().isAfter(today)) {
      problems.add(new FieldProblem("alreadyPaid.receivedOn", "OUT_OF_RANGE"));
    }
    return problems;
  }

  /** Aujourd'hui au jour de Europe/Paris, selon l'horloge injectée. */
  private LocalDate today() {
    return LocalDate.now(clock.withZone(BillingNumbering.ZONE));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
