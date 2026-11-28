package com.psymate.billing.dto;

import com.psymate.billing.model.BillableSession;
import com.psymate.billing.model.BillingInvoiceRef;
import com.psymate.billing.model.BillingPricing;
import com.psymate.billing.model.BillingRate;
import com.psymate.billing.model.BillingService;
import com.psymate.patients.api.PatientBillingView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Séances à facturer vers DTO REST (L5.2), écrit à la main : regroupement par patient, tarif de
 * chaque séance et du groupe (G Q87). Le nom affiché vient de la façade des patients ; l'appelant
 * ne passe que les patients du praticien courant, et une séance dont le patient manque est écartée.
 * Aucun texte clinique.
 */
@Component
public class BillableSessionAssembler {

  /** Ordre des noms affichés : sans tenir compte de la casse ni des accents. */
  private static final Comparator<String> NAME_ORDER = nameOrder();

  private static Comparator<String> nameOrder() {
    Collator collator = Collator.getInstance(Locale.FRENCH);
    collator.setStrength(Collator.PRIMARY);
    return Comparator.<String, String>comparing(s -> s, collator::compare)
        .thenComparing(Comparator.naturalOrder());
  }

  /**
   * Groupes par patient, par nom affiché ; dans un groupe, les séances gardent l'ordre reçu (par
   * date).
   */
  public BillableSessionsDto toList(
      List<BillableSession> pending,
      Map<UUID, PatientBillingView> patients,
      BillingPricing pricing,
      Map<UUID, Integer> openDrafts,
      Map<UUID, BillingInvoiceRef> lastInvoices,
      long dismissedCount) {
    Map<UUID, List<BillableSession>> byPatient = new LinkedHashMap<>();
    for (BillableSession s : pending) {
      if (patients.containsKey(s.getPatientId())) {
        byPatient.computeIfAbsent(s.getPatientId(), k -> new ArrayList<>()).add(s);
      }
    }
    List<BillableSessionGroupDto> groups = new ArrayList<>();
    byPatient.forEach(
        (patientId, sessions) ->
            groups.add(
                group(
                    patients.get(patientId),
                    sessions,
                    pricing,
                    openDrafts.getOrDefault(patientId, 0),
                    lastInvoices.get(patientId))));
    groups.sort(
        Comparator.comparing((BillableSessionGroupDto g) -> g.patient().displayName(), NAME_ORDER)
            .thenComparing(g -> g.patient().id()));
    return new BillableSessionsDto(List.copyOf(groups), dismissedCount);
  }

  /** Séances écartées, chacune avec son patient, dans l'ordre reçu. */
  public DismissedSessionsDto toDismissed(
      List<BillableSession> dismissed,
      Map<UUID, PatientBillingView> patients,
      BillingPricing pricing) {
    List<DismissedSessionDto> items = new ArrayList<>();
    for (BillableSession s : dismissed) {
      PatientBillingView patient = patients.get(s.getPatientId());
      if (patient == null) {
        continue;
      }
      BillingService service = pricing.serviceOf(s);
      BillingRate rate = pricing.rateOf(s);
      items.add(
          new DismissedSessionDto(
              patientRef(patient),
              s.getSessionEntryId(),
              s.getOccurredOn(),
              s.getModality(),
              s.getDurationMin(),
              service == null ? null : service.getId(),
              service == null ? null : service.getLabel(),
              rate.amountCents(),
              rate.source()));
    }
    return new DismissedSessionsDto(List.copyOf(items));
  }

  /** Une séance, tarif et prestation retenus. */
  public BillableSessionDto toDto(BillableSession s, BillingPricing pricing) {
    BillingService service = pricing.serviceOf(s);
    BillingRate rate = pricing.rateOf(s);
    return new BillableSessionDto(
        s.getSessionEntryId(),
        s.getOccurredOn(),
        s.getModality(),
        s.getDurationMin(),
        service == null ? null : service.getId(),
        service == null ? null : service.getLabel(),
        rate.amountCents(),
        rate.source());
  }

  private BillableSessionGroupDto group(
      PatientBillingView patient,
      List<BillableSession> sessions,
      BillingPricing pricing,
      int openDraftCount,
      BillingInvoiceRef lastInvoice) {
    List<BillableSessionDto> dtos = sessions.stream().map(s -> toDto(s, pricing)).toList();
    BillingRate base = pricing.baseRateOf(patient.id());
    boolean fixedOnASession =
        dtos.stream().anyMatch(d -> BillingRate.SESSION.equals(d.rateSource()));
    return new BillableSessionGroupDto(
        patientRef(patient),
        base.amountCents(),
        fixedOnASession ? BillingRate.SESSION : base.source(),
        openDraftCount,
        lastInvoice == null
            ? null
            : new BillingLastInvoiceDto(lastInvoice.number(), lastInvoice.issueDate()),
        dtos);
  }

  private static BillingPatientRefDto patientRef(PatientBillingView patient) {
    return new BillingPatientRefDto(patient.id(), patient.displayName(), patient.archived());
  }
}
