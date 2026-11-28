package com.psymate.billing.service;

import com.psymate.billing.dto.CreateInvoiceBatchRequest;
import com.psymate.billing.dto.CreateInvoiceRequest;
import com.psymate.billing.dto.InvoiceAssembler;
import com.psymate.billing.dto.InvoiceBatchDto;
import com.psymate.billing.dto.InvoiceBatchItemDto;
import com.psymate.billing.dto.InvoiceDto;
import com.psymate.billing.dto.InvoiceLineRequest;
import com.psymate.billing.dto.InvoiceRecipientRequest;
import com.psymate.billing.dto.UpdateInvoiceRequest;
import com.psymate.billing.model.BillableSession;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingIssuer;
import com.psymate.billing.model.BillingNumbering;
import com.psymate.billing.model.BillingPricing;
import com.psymate.billing.model.BillingRate;
import com.psymate.billing.model.BillingService;
import com.psymate.billing.model.Invoice;
import com.psymate.billing.model.InvoiceEvent;
import com.psymate.billing.model.InvoiceLine;
import com.psymate.billing.model.InvoiceRecipients;
import com.psymate.billing.model.InvoiceRecipients.Recipient;
import com.psymate.billing.model.InvoiceSummaryRow;
import com.psymate.billing.model.InvoiceTotals;
import com.psymate.billing.repository.BillableSessionRepository;
import com.psymate.billing.repository.BillingPatientRepository;
import com.psymate.billing.repository.BillingServiceRepository;
import com.psymate.billing.repository.InvoiceEventRepository;
import com.psymate.billing.repository.InvoiceLineRepository;
import com.psymate.billing.repository.InvoiceRepository;
import com.psymate.billing.repository.PaymentRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientBillingView;
import com.psymate.patients.api.PatientFacade;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brouillons de facture (M15-08, L5.3 jusqu'à DELETE ; G Q86, Q87, Q88) : création (une ou par
 * lot), lecture (par identifiant ou par numéro), duplication (M15-09), modification, suppression.
 *
 * <p>Une séance mise dans un brouillon passe à {@code invoiced} (avec {@code invoice_id}) dans la
 * même transaction ; une ligne de séance retirée, ou le brouillon supprimé, la rend à {@code
 * pending}. Les séances sont verrouillées ({@code select … for update}, par identifiant croissant)
 * avant leur contrôle : une même séance ne peut pas entrer dans deux brouillons à la fois (409
 * SESSION_ALREADY_INVOICED avec {@code sessionEntryId}). Une ligne par séance demandée :
 * désignation sans texte clinique (libellé de la prestation retenue et date, ou « Séance du … »),
 * prix = tarif de G Q87 (422 RATE_REQUIRED s'il n'y en a pas), quantité 1, date et prestation de la
 * séance.
 *
 * <p>Montants recalculés à chaque écriture selon L3.2 avec les réglages du moment ({@link
 * InvoiceTotals}). Seul un brouillon s'écrit ou se supprime (409 INVOICE_ISSUED sinon) ; la
 * suppression est physique (E9), ses lignes et son historique partent en cascade. Événement
 * d'historique {@code draft_created} à la création. Ni nom, ni montant journalisé.
 */
@Service
public class InvoiceService {

  /** Lignes de séance au plus par facture (taille du lot, L5.3). */
  static final int MAX_SESSION_LINES = 200;

  /** Lignes libres au plus par facture (L5.3). */
  static final int MAX_FREE_LINES = 100;

  /** Ligne à écrire, déjà validée. */
  record PlannedLine(
      UUID sessionEntryId,
      UUID serviceId,
      String label,
      LocalDate occurredOn,
      int quantity,
      int unitPrice) {}

  private final InvoiceRepository invoices;
  private final InvoiceLineRepository lines;
  private final InvoiceEventRepository events;
  private final PaymentRepository payments;
  private final BillableSessionRepository sessions;
  private final BillableSessionService billable;
  private final BillingPatientRepository profiles;
  private final BillingServiceRepository services;
  private final BillingSettingsService settings;
  private final PatientFacade patients;
  private final InvoiceAssembler assembler;
  private final BillingTexts texts;
  private final Clock clock;

  public InvoiceService(
      InvoiceRepository invoices,
      InvoiceLineRepository lines,
      InvoiceEventRepository events,
      PaymentRepository payments,
      BillableSessionRepository sessions,
      BillableSessionService billable,
      BillingPatientRepository profiles,
      BillingServiceRepository services,
      BillingSettingsService settings,
      PatientFacade patients,
      InvoiceAssembler assembler,
      BillingTexts texts,
      Clock clock) {
    this.invoices = invoices;
    this.lines = lines;
    this.events = events;
    this.payments = payments;
    this.sessions = sessions;
    this.billable = billable;
    this.profiles = profiles;
    this.services = services;
    this.settings = settings;
    this.patients = patients;
    this.assembler = assembler;
    this.texts = texts;
    this.clock = clock;
  }

  // ---- création

  /**
   * Nouveau brouillon d'un patient du praticien : une ligne par séance de {@code sessionEntryIds}
   * (par date), puis les lignes données. 404 PATIENT_NOT_FOUND, 409 PATIENT_ARCHIVED, 404
   * SESSION_NOT_BILLABLE, 409 SESSION_ALREADY_INVOICED, 422 RATE_REQUIRED, 422 par champ.
   */
  @Transactional
  public InvoiceDto create(CreateInvoiceRequest request) {
    CurrentPractitioner me = me();
    PatientBillingView patient = ownPatient(request.patientId(), me);
    if (patient.archived()) {
      throw BillingException.patientArchivedForInvoice(patient.id());
    }
    List<UUID> sessionIds =
        request.sessionEntryIds() == null ? List.of() : request.sessionEntryIds();
    List<InvoiceLineRequest> given = request.lines() == null ? List.of() : request.lines();
    List<FieldProblem> problems = new ArrayList<>();
    duplicates(sessionIds, "sessionEntryIds", problems);
    checkLines(given, new HashSet<>(sessionIds), Set.of(), me, problems);
    throwIfAny(problems);

    Set<UUID> all = new LinkedHashSet<>(sessionIds);
    all.addAll(lineSessionIds(given));
    Map<UUID, BillableSession> locked = lock(all, me);
    for (int i = 0; i < sessionIds.size(); i++) {
      if (!patient.id().equals(locked.get(sessionIds.get(i)).getPatientId())) {
        problems.add(new FieldProblem("sessionEntryIds[" + i + "]", "INVALID_VALUE"));
      }
    }
    samePatient(given, locked, patient.id(), problems);
    throwIfAny(problems);
    for (UUID id : new TreeSet<>(all)) {
      if (locked.get(id).isInvoiced()) {
        throw BillingException.sessionAlreadyInvoiced(id);
      }
    }

    List<BillableSession> auto = byDate(sessionIds.stream().map(locked::get).toList());
    List<PlannedLine> planned = new ArrayList<>(sessionLines(auto, me));
    given.forEach(l -> planned.add(planned(l, locked)));

    BillingIssuer issuer = settings.issuerOfTheMoment(me);
    OffsetDateTime now = now();
    Invoice invoice = newDraft(me, patient, issuer, now);
    InvoiceTotals totals = writeLines(invoice, planned, locked, issuer, now);
    recordCreated(invoice, planned, false, now);
    invoices.flush();
    return view(invoice, me, totals);
  }

  /**
   * Brouillons par lot : un brouillon par patient des séances, tout ou rien (une seule transaction
   * : la première erreur annule tout). Mêmes codes que la création.
   */
  @Transactional
  public InvoiceBatchDto batch(CreateInvoiceBatchRequest request) {
    CurrentPractitioner me = me();
    List<UUID> ids = request.sessionEntryIds();
    List<FieldProblem> problems = new ArrayList<>();
    duplicates(ids, "sessionEntryIds", problems);
    throwIfAny(problems);

    Map<UUID, BillableSession> locked = lock(new LinkedHashSet<>(ids), me);
    for (UUID id : new TreeSet<>(ids)) {
      if (locked.get(id).isInvoiced()) {
        throw BillingException.sessionAlreadyInvoiced(id);
      }
    }
    Map<UUID, List<BillableSession>> byPatient = new LinkedHashMap<>();
    for (UUID id : ids) {
      BillableSession s = locked.get(id);
      byPatient.computeIfAbsent(s.getPatientId(), k -> new ArrayList<>()).add(s);
    }
    Map<UUID, PatientBillingView> views = patients.billingIdentities(byPatient.keySet());
    for (UUID patientId : byPatient.keySet()) {
      PatientBillingView view = views.get(patientId);
      if (view == null || !me.id().equals(view.ownerId())) {
        throw PatientAccessException.notFound();
      }
      if (view.archived()) {
        throw BillingException.patientArchivedForInvoice(patientId);
      }
    }
    Map<UUID, List<PlannedLine>> plannedByPatient = new LinkedHashMap<>();
    byPatient.forEach(
        (patientId, rows) -> plannedByPatient.put(patientId, sessionLines(byDate(rows), me)));

    BillingIssuer issuer = settings.issuerOfTheMoment(me);
    OffsetDateTime now = now();
    List<InvoiceBatchItemDto> items = new ArrayList<>();
    for (Map.Entry<UUID, List<PlannedLine>> e : plannedByPatient.entrySet()) {
      PatientBillingView patient = views.get(e.getKey());
      Invoice invoice = newDraft(me, patient, issuer, now);
      InvoiceTotals totals = writeLines(invoice, e.getValue(), locked, issuer, now);
      recordCreated(invoice, e.getValue(), true, now);
      items.add(assembler.toBatchItem(invoice, patient, totals.total()));
    }
    invoices.flush();
    return new InvoiceBatchDto(List.copyOf(items));
  }

  // ---- lecture

  /** Facture du praticien (404 INVOICE_NOT_FOUND sinon), brouillon ou document émis. */
  @Transactional(readOnly = true)
  public InvoiceDto get(UUID id) {
    CurrentPractitioner me = me();
    Invoice invoice =
        invoices
            .findByIdAndPractitionerId(id, me.id())
            .orElseThrow(BillingException::invoiceNotFound);
    return view(invoice, me, null);
  }

  /**
   * Document du praticien par son numéro (M15-09 : même réponse que {@link #get}) ; 404
   * INVOICE_NOT_FOUND pour un numéro inconnu ou d'un autre praticien.
   */
  @Transactional(readOnly = true)
  public InvoiceDto getByNumber(String number) {
    CurrentPractitioner me = me();
    Invoice invoice =
        invoices
            .findByPractitionerIdAndNumber(me.id(), number)
            .orElseThrow(BillingException::invoiceNotFound);
    return view(invoice, me, null);
  }

  /** Facture relue après une écriture d'un autre service de la facturation (émission, M15-09). */
  InvoiceDto read(Invoice invoice, CurrentPractitioner me) {
    return view(invoice, me, null);
  }

  // ---- duplication

  /**
   * Nouveau brouillon copié d'une facture (M15-09, brouillon, émise ou annulée) : mêmes lignes
   * (désignation, date, prestation, quantité, prix) sans leurs séances ({@code session_entry_id}
   * null : aucune séance ne passe à {@code invoiced}), même patient (409 PATIENT_ARCHIVED s'il est
   * archivé), destinataire par défaut, date du jour, échéance et mention des réglages, historique
   * {@code draft_created} avec {@code duplicateOf}. Un avoir ne se duplique pas (409
   * CREDIT_NOTE_NOT_DUPLICABLE).
   */
  @Transactional
  public InvoiceDto duplicate(UUID id) {
    CurrentPractitioner me = me();
    Invoice source =
        invoices
            .findByIdAndPractitionerId(id, me.id())
            .orElseThrow(BillingException::invoiceNotFound);
    if (!Invoice.KIND_INVOICE.equals(source.getKind())) {
      throw BillingException.creditNoteNotDuplicable();
    }
    PatientBillingView patient = ownPatient(source.getPatientId(), me);
    if (patient.archived()) {
      throw BillingException.patientArchivedForInvoice(patient.id());
    }
    List<PlannedLine> planned =
        lines.findByInvoiceIdOrderByPosition(source.getId()).stream()
            .map(
                l ->
                    new PlannedLine(
                        null,
                        l.getServiceId(),
                        l.getLabel(),
                        l.getOccurredOn(),
                        l.getQuantity(),
                        l.getUnitPriceCents()))
            .toList();
    BillingIssuer issuer = settings.issuerOfTheMoment(me);
    OffsetDateTime now = now();
    Invoice invoice = newDraft(me, patient, issuer, now);
    InvoiceTotals totals = writeLines(invoice, planned, Map.of(), issuer, now);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("sessionCount", 0L);
    detail.put("batch", false);
    detail.put("duplicateOf", source.getId().toString());
    events.save(
        new InvoiceEvent(
            UUID.randomUUID(), invoice.getId(), InvoiceEvent.DRAFT_CREATED, now, detail));
    invoices.flush();
    return view(invoice, me, totals);
  }

  // ---- modification

  /**
   * Fusion partielle d'un brouillon (L5.3) : patient (tant qu'aucune ligne de séance),
   * destinataire, dates, échéance, mention, lignes (remplacement complet). Répond la facture relue.
   */
  @Transactional
  public InvoiceDto update(UUID id, UpdateInvoiceRequest r) {
    CurrentPractitioner me = me();
    Invoice invoice =
        invoices.findForUpdate(id, me.id()).orElseThrow(BillingException::invoiceNotFound);
    invoice.requireDraft();
    List<FieldProblem> problems = new ArrayList<>();

    PatientBillingView newPatient = null;
    if (r.patientIdProvided()) {
      if (r.getPatientId() == null) {
        problems.add(new FieldProblem("patientId", "REQUIRED"));
      } else if (!r.getPatientId().equals(invoice.getPatientId())) {
        newPatient = ownPatient(r.getPatientId(), me);
        if (newPatient.archived()) {
          throw BillingException.patientArchivedForInvoice(newPatient.id());
        }
      }
    }
    UUID targetPatient = newPatient == null ? invoice.getPatientId() : newPatient.id();

    LocalDate issueDate = invoice.getIssueDate();
    if (r.hasIssueDate()) {
      if (r.getIssueDate() == null) {
        problems.add(new FieldProblem("issueDate", "REQUIRED"));
      } else {
        issueDate = r.getIssueDate();
      }
    }
    String dueTerm = invoice.getDueTerm();
    if (r.dueTermProvided()) {
      if (r.getDueTerm() == null) {
        problems.add(new FieldProblem("dueTerm", "REQUIRED"));
      } else if (!Invoice.DUE_TERMS.contains(r.getDueTerm())) {
        problems.add(new FieldProblem("dueTerm", "INVALID_VALUE"));
      } else {
        dueTerm = r.getDueTerm();
      }
    }
    LocalDate dueOn = r.dueOnProvided() ? r.getDueOn() : invoice.getDueOn();
    if (Invoice.DUE_DATE.equals(dueTerm)) {
      if (dueOn == null) {
        problems.add(new FieldProblem("dueOn", "REQUIRED"));
      } else if (dueOn.isBefore(issueDate)) {
        problems.add(new FieldProblem("dueOn", "OUT_OF_RANGE"));
      }
    } else {
      if (r.dueOnProvided() && r.getDueOn() != null) {
        problems.add(new FieldProblem("dueOn", "INVALID_VALUE"));
      }
      dueOn = null;
    }

    List<InvoiceLineRequest> given = null;
    if (r.linesProvided()) {
      if (r.getLines() == null) {
        problems.add(new FieldProblem("lines", "REQUIRED"));
      } else {
        given = r.getLines();
        // Une prestation archivée depuis reste admise si une ligne actuelle du brouillon la porte :
        // renvoyer les lignes telles quelles doit toujours passer.
        Set<UUID> currentServices =
            lines.findByInvoiceIdOrderByPosition(invoice.getId()).stream()
                .map(InvoiceLine::getServiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        checkLines(given, Set.of(), currentServices, me, problems);
      }
    }
    throwIfAny(problems);

    OffsetDateTime now = now();
    // Séances rattachées lues sans verrou : la facture est verrouillée, aucune autre écriture ne
    // les rattache ni ne les détache ; elles sont verrouillées plus bas avec les nouvelles, en une
    // seule passe et dans l'ordre de lock() (le même que la création et la suppression).
    Set<UUID> attachedIds =
        new LinkedHashSet<>(sessions.findIdsByInvoice(invoice.getId(), me.id()));
    boolean patientChanges = !targetPatient.equals(invoice.getPatientId());

    InvoiceTotals totals;
    BillingIssuer issuer = settings.issuerOfTheMoment(me);
    if (given != null) {
      Set<UUID> wanted = lineSessionIds(given);
      if (patientChanges && wanted.stream().anyMatch(attachedIds::contains)) {
        throw BillingException.invalidField("patientId", "INVALID_VALUE");
      }
      Set<UUID> all = new LinkedHashSet<>(attachedIds);
      all.addAll(wanted);
      Map<UUID, BillableSession> locked = lock(all, me);
      samePatient(given, locked, targetPatient, problems);
      throwIfAny(problems);
      for (UUID sessionId : new TreeSet<>(wanted)) {
        BillableSession s = locked.get(sessionId);
        if (s.isInvoiced() && !invoice.getId().equals(s.getInvoiceId())) {
          throw BillingException.sessionAlreadyInvoiced(sessionId);
        }
      }
      for (UUID sessionId : attachedIds) {
        if (!wanted.contains(sessionId)) {
          locked.get(sessionId).release(now);
        }
      }
      lines.deleteAllOfDraft(invoice.getId());
      List<PlannedLine> planned = given.stream().map(l -> planned(l, locked)).toList();
      totals = writeLines(invoice, planned, locked, issuer, now);
    } else {
      if (patientChanges && !attachedIds.isEmpty()) {
        throw BillingException.invalidField("patientId", "INVALID_VALUE");
      }
      List<Long> amounts =
          lines.findByInvoiceIdOrderByPosition(invoice.getId()).stream()
              .map(InvoiceLine::getAmountCents)
              .toList();
      totals = InvoiceTotals.of(amounts, issuer.vatRegime(), issuer.vatRateBp());
      invoice.changeTotals(totals, now);
    }

    if (newPatient != null) {
      invoice.changePatient(newPatient.id(), now);
      if (!r.recipientProvided()) {
        Recipient recipient = defaultRecipient(newPatient, me);
        invoice.changeRecipient(recipient.name(), recipient.addressLines(), now);
      }
    }
    if (r.recipientProvided()) {
      InvoiceRecipientRequest asked = r.getRecipient();
      if (asked == null) {
        PatientBillingView patient =
            newPatient != null ? newPatient : ownPatient(invoice.getPatientId(), me);
        Recipient recipient = defaultRecipient(patient, me);
        invoice.changeRecipient(recipient.name(), recipient.addressLines(), now);
      } else {
        invoice.changeRecipient(
            asked.name().strip(), BillingSettingsService.blankToNull(asked.addressLines()), now);
      }
    }
    if (r.hasIssueDate() || r.dueTermProvided() || r.dueOnProvided()) {
      invoice.changeDates(issueDate, dueTerm, dueOn, now);
    }
    if (r.footerNoteProvided()) {
      invoice.changeFooterNote(BillingSettingsService.blankToNull(r.getFooterNote()), now);
    }
    invoices.flush();
    return view(invoice, me, totals);
  }

  // ---- suppression

  /**
   * Supprime un brouillon (E9, 204) : ses séances reviennent à {@code pending}, puis la ligne de la
   * facture est effacée (ses lignes et son historique en cascade). 409 INVOICE_ISSUED pour une
   * facture émise, un avoir ou une facture annulée ; 404 INVOICE_NOT_FOUND sinon. La suppression
   * porte elle-même la condition « brouillon » en SQL : une facture émise ne part jamais.
   */
  @Transactional
  public void delete(UUID id) {
    CurrentPractitioner me = me();
    Invoice invoice =
        invoices.findForUpdate(id, me.id()).orElseThrow(BillingException::invoiceNotFound);
    invoice.requireDraft();
    OffsetDateTime now = now();
    Set<UUID> attachedIds =
        new LinkedHashSet<>(sessions.findIdsByInvoice(invoice.getId(), me.id()));
    lock(attachedIds, me).values().forEach(session -> session.release(now));
    // Les séances quittent la facture avant elle (écrites avant la suppression en bloc) : la
    // contrainte état/invoice_id tient.
    if (invoices.deleteDraft(invoice.getId(), me.id()) != 1) {
      throw BillingException.invoiceIssued();
    }
  }

  // ---- outils : lignes

  /**
   * Règles des lignes que Jakarta n'exprime pas : 100 lignes libres et 200 lignes de séance au
   * plus, une séance une seule fois (ni deux lignes, ni une ligne et {@code sessionEntryIds}),
   * prestation du praticien, non archivée (comme le PATCH d'une séance à facturer, M15-07) sauf si
   * elle figure déjà sur une ligne actuelle du brouillon ({@code keptServiceIds}).
   */
  private void checkLines(
      List<InvoiceLineRequest> given,
      Set<UUID> autoSessionIds,
      Set<UUID> keptServiceIds,
      CurrentPractitioner me,
      List<FieldProblem> problems) {
    long free = given.stream().filter(l -> l.sessionEntryId() == null).count();
    long sessionLines = autoSessionIds.size() + (given.size() - free);
    if (free > MAX_FREE_LINES || sessionLines > MAX_SESSION_LINES) {
      problems.add(new FieldProblem("lines", "OUT_OF_RANGE"));
    }
    Set<UUID> seen = new HashSet<>(autoSessionIds);
    Set<UUID> serviceIds = new HashSet<>();
    for (int i = 0; i < given.size(); i++) {
      InvoiceLineRequest l = given.get(i);
      if (l.sessionEntryId() != null && !seen.add(l.sessionEntryId())) {
        problems.add(new FieldProblem("lines[" + i + "].sessionEntryId", "INVALID_VALUE"));
      }
      if (l.serviceId() != null) {
        serviceIds.add(l.serviceId());
      }
    }
    if (serviceIds.isEmpty()) {
      return;
    }
    Set<UUID> known =
        services.findAllById(serviceIds).stream()
            .filter(s -> me.id().equals(s.getPractitionerId()))
            .filter(s -> !s.isArchived() || keptServiceIds.contains(s.getId()))
            .map(BillingService::getId)
            .collect(Collectors.toSet());
    for (int i = 0; i < given.size(); i++) {
      UUID serviceId = given.get(i).serviceId();
      if (serviceId != null && !known.contains(serviceId)) {
        problems.add(new FieldProblem("lines[" + i + "].serviceId", "INVALID_VALUE"));
      }
    }
  }

  /** Les séances des lignes données doivent être du patient de la facture. */
  private static void samePatient(
      List<InvoiceLineRequest> given,
      Map<UUID, BillableSession> locked,
      UUID patientId,
      List<FieldProblem> problems) {
    for (int i = 0; i < given.size(); i++) {
      UUID sessionId = given.get(i).sessionEntryId();
      if (sessionId != null && !patientId.equals(locked.get(sessionId).getPatientId())) {
        problems.add(new FieldProblem("lines[" + i + "].sessionEntryId", "INVALID_VALUE"));
      }
    }
  }

  /**
   * Lignes des séances demandées, par date : désignation sans texte clinique, tarif de G Q87 (422
   * RATE_REQUIRED sans tarif), quantité 1, date et prestation retenue de la séance.
   */
  private List<PlannedLine> sessionLines(List<BillableSession> rows, CurrentPractitioner me) {
    if (rows.isEmpty()) {
      return List.of();
    }
    BillingPricing pricing = billable.pricingOf(rows, me);
    List<PlannedLine> planned = new ArrayList<>();
    for (BillableSession s : rows) {
      BillingRate rate = pricing.rateOf(s);
      if (rate.amountCents() == null) {
        throw BillingException.rateRequired(s.getSessionEntryId());
      }
      BillingService service = pricing.serviceOf(s);
      planned.add(
          new PlannedLine(
              s.getSessionEntryId(),
              service == null ? null : service.getId(),
              texts.sessionLineLabel(
                  service == null ? null : service.getLabel(), s.getOccurredOn()),
              s.getOccurredOn(),
              1,
              rate.amountCents()));
    }
    return planned;
  }

  /** Ligne donnée : une ligne de séance prend toujours le jour de la séance. */
  private static PlannedLine planned(InvoiceLineRequest l, Map<UUID, BillableSession> locked) {
    BillableSession session = l.sessionEntryId() == null ? null : locked.get(l.sessionEntryId());
    return new PlannedLine(
        l.sessionEntryId(),
        l.serviceId(),
        l.label().strip(),
        session != null ? session.getOccurredOn() : l.occurredOn(),
        l.quantity() == null ? 1 : l.quantity(),
        l.unitPrice());
  }

  /**
   * Écrit les lignes (positions 1, 2…), rattache leurs séances au brouillon et recalcule ses
   * montants avec les réglages du moment.
   */
  private InvoiceTotals writeLines(
      Invoice invoice,
      List<PlannedLine> planned,
      Map<UUID, BillableSession> locked,
      BillingIssuer issuer,
      OffsetDateTime now) {
    List<InvoiceLine> rows = new ArrayList<>(planned.size());
    int position = 1;
    for (PlannedLine p : planned) {
      rows.add(
          new InvoiceLine(
              UUID.randomUUID(),
              invoice.getId(),
              position++,
              p.serviceId(),
              p.label(),
              p.occurredOn(),
              p.quantity(),
              p.unitPrice(),
              p.sessionEntryId()));
      if (p.sessionEntryId() != null) {
        locked.get(p.sessionEntryId()).attachTo(invoice.getId(), now);
      }
    }
    lines.saveAll(rows);
    InvoiceTotals totals =
        InvoiceTotals.of(
            rows.stream().map(InvoiceLine::getAmountCents).toList(),
            issuer.vatRegime(),
            issuer.vatRateBp());
    invoice.changeTotals(totals, now);
    return totals;
  }

  // ---- outils : brouillon

  /** Brouillon vide du patient : destinataire par défaut, date du jour, échéance et mention. */
  private Invoice newDraft(
      CurrentPractitioner me,
      PatientBillingView patient,
      BillingIssuer issuer,
      OffsetDateTime now) {
    Recipient recipient = defaultRecipient(patient, me);
    Invoice invoice =
        Invoice.draft(
            UUID.randomUUID(),
            me.tenantId(),
            me.id(),
            patient.id(),
            recipient.name(),
            recipient.addressLines(),
            today(),
            issuer.dueTerm(),
            issuer.footerNote(),
            now);
    // Écrit tout de suite : les séances y sont rattachées par clé étrangère.
    return invoices.saveAndFlush(invoice);
  }

  private Recipient defaultRecipient(PatientBillingView patient, CurrentPractitioner me) {
    return InvoiceRecipients.defaultFor(
        patient, profiles.findByPatientIdAndPractitionerId(patient.id(), me.id()).orElse(null));
  }

  /** Événement d'historique {@code draft_created} : nombre de lignes de séance, lot ou non. */
  private void recordCreated(
      Invoice invoice, List<PlannedLine> planned, boolean batch, OffsetDateTime now) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("sessionCount", planned.stream().filter(p -> p.sessionEntryId() != null).count());
    detail.put("batch", batch);
    events.save(
        new InvoiceEvent(
            UUID.randomUUID(), invoice.getId(), InvoiceEvent.DRAFT_CREATED, now, detail));
  }

  // ---- outils : vue

  /**
   * Facture relue : résumé calculé en SQL, émetteur (réglages du moment pour un brouillon,
   * instantané sinon), montants (recalculés pour un brouillon), lignes, avoirs, historique, aperçu
   * du prochain numéro.
   */
  private InvoiceDto view(Invoice invoice, CurrentPractitioner me, InvoiceTotals known) {
    boolean draft = invoice.isDraft();
    BillingIssuer issuer =
        draft
            ? settings.issuerOfTheMoment(me)
            : BillingIssuer.fromSnapshot(invoice.getIssuerSnapshot());
    List<InvoiceLine> rows = lines.findByInvoiceIdOrderByPosition(invoice.getId());
    InvoiceTotals totals;
    if (!draft) {
      totals =
          new InvoiceTotals(
              invoice.getSubtotalCents(), invoice.getVatCents(), invoice.getTotalCents());
    } else if (known != null) {
      totals = known;
    } else {
      totals =
          InvoiceTotals.of(
              rows.stream().map(InvoiceLine::getAmountCents).toList(),
              issuer.vatRegime(),
              issuer.vatRateBp());
    }
    Integer draftRate =
        draft && InvoiceTotals.LIABLE.equals(issuer.vatRegime()) ? issuer.vatRateBp() : null;
    InvoiceSummaryRow row =
        invoices.summariesOf(me.id(), List.of(invoice.getId()), today(), draftRate).getFirst();
    Set<UUID> credited =
        rows.isEmpty()
            ? Set.of()
            : new HashSet<>(lines.creditedAmong(rows.stream().map(InvoiceLine::getId).toList()));
    Invoice cancels =
        invoice.getCancelsInvoiceId() == null
            ? null
            : invoices
                .findByIdAndPractitionerId(invoice.getCancelsInvoiceId(), me.id())
                .orElse(null);
    PatientBillingView patient =
        patients
            .billingIdentity(invoice.getPatientId())
            .filter(v -> me.id().equals(v.ownerId()))
            .orElse(null);
    String preview =
        draft
            ? settings.nextInvoiceNumber(
                me.id(), issuer.invoicePrefix(), invoice.getIssueDate().getYear())
            : null;
    return assembler.toDto(
        invoice,
        row,
        patient,
        issuer,
        totals,
        rows,
        credited,
        invoices.findCreditNotes(invoice.getId(), me.id()),
        cancels,
        events.findByInvoiceIdOrderByAtAscIdAsc(invoice.getId()),
        preview,
        payments.findByInvoiceIdOrderByReceivedOnAscCreatedAtAscIdAsc(invoice.getId()));
  }

  // ---- outils : séances, patients, erreurs

  /**
   * Verrouille les séances du praticien par identifiant croissant (ordre de {@link UUID#compareTo},
   * le même pour la création, le lot, la modification et la suppression : pas d'étreinte fatale
   * entre deux demandes), en les créant à la demande si besoin (G Q86).
   */
  private Map<UUID, BillableSession> lock(Set<UUID> ids, CurrentPractitioner me) {
    Map<UUID, BillableSession> locked = new HashMap<>();
    for (UUID id : new TreeSet<>(ids)) {
      locked.put(id, billable.lockForInvoice(id, me));
    }
    return locked;
  }

  private static Set<UUID> lineSessionIds(List<InvoiceLineRequest> given) {
    return given.stream()
        .map(InvoiceLineRequest::sessionEntryId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static List<BillableSession> byDate(List<BillableSession> rows) {
    return rows.stream()
        .sorted(
            Comparator.comparing(BillableSession::getOccurredOn)
                .thenComparing(BillableSession::getSessionEntryId))
        .toList();
  }

  /** Un identifiant présent deux fois : {@code field[i]} INVALID_VALUE sur la répétition. */
  private static void duplicates(List<UUID> ids, String field, List<FieldProblem> problems) {
    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < ids.size(); i++) {
      if (!seen.add(ids.get(i))) {
        problems.add(new FieldProblem(field + "[" + i + "]", "INVALID_VALUE"));
      }
    }
  }

  private static void throwIfAny(List<FieldProblem> problems) {
    if (!problems.isEmpty()) {
      throw BillingException.invalidFields(List.copyOf(problems));
    }
  }

  /** Patient du praticien courant (404 PATIENT_NOT_FOUND sinon, comme un patient inexistant). */
  private PatientBillingView ownPatient(UUID patientId, CurrentPractitioner me) {
    return patients
        .billingIdentity(patientId)
        .filter(v -> me.id().equals(v.ownerId()))
        .orElseThrow(PatientAccessException::notFound);
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
