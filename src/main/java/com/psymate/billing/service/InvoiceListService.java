package com.psymate.billing.service;

import com.psymate.billing.dto.InvoiceAssembler;
import com.psymate.billing.dto.InvoicePageDto;
import com.psymate.billing.dto.InvoiceSummaryDto;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingNumbering;
import com.psymate.billing.model.InvoiceCursor;
import com.psymate.billing.model.InvoiceListCriteria;
import com.psymate.billing.model.InvoiceSummaryRow;
import com.psymate.billing.repository.InvoiceRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientBillingView;
import com.psymate.patients.api.PatientFacade;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liste des factures du praticien (GET /api/invoices, M15-08, L5.3) : filtres, curseur, statut
 * calculé en SQL ({@code InvoiceNativeQueries}). Ordre : brouillons d'abord, puis date d'émission
 * décroissante, puis identifiant.
 *
 * <p>Recherche {@code q} (2 caractères au moins, sans tenir compte de la casse ni des accents) :
 * dans le numéro et le nom du destinataire de la facture (colonnes de billing, en SQL), et dans le
 * nom affiché du patient. Ce nom n'est pas dans billing, qui ne lit pas les tables de patients (G
 * Q34, Q85) : les patients du praticien qui ont une facture sont lus par la façade des patients
 * ({@code billingIdentities}), filtrés en mémoire sur leur nom affiché, et leurs identifiants
 * passent à la requête. Le nom affiché montré est celui du moment.
 */
@Service
public class InvoiceListService {

  /** Statuts exposés filtrables (L5.3). */
  static final List<String> STATUSES = List.of("draft", "due", "partial", "paid", "void");

  /** Natures filtrables. */
  static final List<String> KINDS = List.of("invoice", "credit_note");

  /** Taille de page par défaut et maximale. */
  static final int DEFAULT_LIMIT = 50;

  static final int MAX_LIMIT = 100;

  /** Longueur de la recherche, en caractères, une fois les espaces de bord retirés. */
  static final int Q_MIN = 2;

  static final int Q_MAX = 100;

  private final InvoiceRepository invoices;
  private final BillingSettingsService settings;
  private final PatientFacade patients;
  private final InvoiceAssembler assembler;
  private final Clock clock;

  public InvoiceListService(
      InvoiceRepository invoices,
      BillingSettingsService settings,
      PatientFacade patients,
      InvoiceAssembler assembler,
      Clock clock) {
    this.invoices = invoices;
    this.settings = settings;
    this.patients = patients;
    this.assembler = assembler;
    this.clock = clock;
  }

  // ---- règles pures (testées unitairement)

  /** Texte de recherche normalisé : minuscules, sans accents (décomposition NFD). */
  static String normalize(String text) {
    String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
  }

  /** Motif {@code like} « contient », jokers {@code %} et {@code _} échappés par {@code !}. */
  static String containsPattern(String normalized) {
    String escaped = normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }

  // ---- lecture

  /**
   * Une page de la liste. 422 VALIDATION_FAILED : status ou kind INVALID_VALUE, q OUT_OF_RANGE,
   * limit OUT_OF_RANGE, cursor INVALID_FORMAT ; 404 PATIENT_NOT_FOUND pour le patient d'un autre
   * praticien.
   */
  @Transactional(readOnly = true)
  public InvoicePageDto list(
      String status,
      Boolean overdue,
      String kind,
      UUID patientId,
      String q,
      LocalDate from,
      String cursor,
      Integer limit) {
    CurrentPractitioner me = me();
    List<FieldProblem> problems = new ArrayList<>();
    String wantedStatus = BillingSettingsService.blankToNull(status);
    if (wantedStatus != null && !STATUSES.contains(wantedStatus)) {
      problems.add(new FieldProblem("status", "INVALID_VALUE"));
    }
    String wantedKind = BillingSettingsService.blankToNull(kind);
    if (wantedKind != null && !KINDS.contains(wantedKind)) {
      problems.add(new FieldProblem("kind", "INVALID_VALUE"));
    }
    String search = BillingSettingsService.blankToNull(q);
    if (search != null && (search.length() < Q_MIN || search.length() > Q_MAX)) {
      problems.add(new FieldProblem("q", "OUT_OF_RANGE"));
    }
    int size = limit == null ? DEFAULT_LIMIT : limit;
    if (size < 1 || size > MAX_LIMIT) {
      problems.add(new FieldProblem("limit", "OUT_OF_RANGE"));
    }
    InvoiceCursor after = null;
    if (cursor != null && !cursor.isBlank()) {
      try {
        after = InvoiceCursor.decode(cursor.strip());
      } catch (IllegalArgumentException e) {
        problems.add(new FieldProblem("cursor", "INVALID_FORMAT"));
      }
    }
    if (!problems.isEmpty()) {
      throw BillingException.invalidFields(problems);
    }
    if (patientId != null) {
      patients
          .billingIdentity(patientId)
          .filter(v -> me.id().equals(v.ownerId()))
          .orElseThrow(PatientAccessException::notFound);
    }
    String pattern = null;
    Set<UUID> matching = Set.of();
    if (search != null) {
      String normalized = normalize(search);
      pattern = containsPattern(normalized);
      matching = patientsNamed(normalized, me);
    }
    InvoiceListCriteria criteria =
        new InvoiceListCriteria(
            wantedStatus,
            overdue,
            wantedKind,
            patientId,
            pattern,
            matching,
            from,
            after,
            size,
            LocalDate.now(clock.withZone(BillingNumbering.ZONE)),
            settings.draftVatRateBp(me.id()));
    List<InvoiceSummaryRow> rows = invoices.summaries(me.id(), criteria);
    boolean more = rows.size() > size;
    List<InvoiceSummaryRow> page = more ? rows.subList(0, size) : rows;
    Map<UUID, PatientBillingView> views =
        ownPatients(
            page.stream()
                .map(InvoiceSummaryRow::patientId)
                .collect(Collectors.toCollection(LinkedHashSet::new)),
            me);
    List<InvoiceSummaryDto> items =
        page.stream().map(r -> assembler.toSummary(r, views.get(r.patientId()))).toList();
    String next = null;
    if (more) {
      InvoiceSummaryRow last = page.getLast();
      next = new InvoiceCursor(last.rank(), last.issueDate(), last.id()).encode();
    }
    return new InvoicePageDto(items, next);
  }

  /**
   * Patients du praticien, avec au moins une facture, dont le nom affiché contient la recherche.
   */
  private Set<UUID> patientsNamed(String normalized, CurrentPractitioner me) {
    Set<UUID> invoiced = invoices.invoicedPatientIds(me.id());
    return ownPatients(invoiced, me).values().stream()
        .filter(v -> v.displayName() != null && normalize(v.displayName()).contains(normalized))
        .map(PatientBillingView::id)
        .collect(Collectors.toSet());
  }

  private Map<UUID, PatientBillingView> ownPatients(Set<UUID> ids, CurrentPractitioner me) {
    return patients.billingIdentities(ids).entrySet().stream()
        .filter(e -> me.id().equals(e.getValue().ownerId()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
