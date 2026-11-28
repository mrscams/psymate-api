package com.psymate.billing.service;

import com.psymate.billing.dto.BillingProfileDto;
import com.psymate.billing.dto.BillingProfileMapper;
import com.psymate.billing.dto.BillingProfileRequest;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingPatient;
import com.psymate.billing.repository.BillingPatientRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.patients.api.PatientRef;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil de facturation d'un patient (M15-05, L5.1) : tarif, adresse de facturation, tiers payeur.
 * Le patient doit être au praticien : garde des patients, et le patient d'un autre praticien répond
 * 404 PATIENT_NOT_FOUND comme un patient inexistant (L5 : un identifiant d'un autre praticien
 * répond 404). Un patient archivé se lit mais ne s'écrit plus (409 PATIENT_ARCHIVED). Le profil
 * n'est pas une donnée clinique : {@code patient.clinical_rev} n'est pas touché. Aucun nom, aucune
 * adresse ni aucun montant n'est journalisé.
 */
@Service
public class BillingProfileService {

  private final BillingPatientRepository profiles;
  private final BillingProfileMapper mapper;
  private final PatientOwnershipGuard guard;
  private final PatientFacade patients;
  private final Clock clock;

  public BillingProfileService(
      BillingPatientRepository profiles,
      BillingProfileMapper mapper,
      PatientOwnershipGuard guard,
      PatientFacade patients,
      Clock clock) {
    this.profiles = profiles;
    this.mapper = mapper;
    this.guard = guard;
    this.patients = patients;
    this.clock = clock;
  }

  /** Profil du patient ; vide (champs à null) tant que rien n'est enregistré. */
  @Transactional(readOnly = true)
  public BillingProfileDto get(UUID patientId) {
    requireOwned(patientId);
    return profiles.findById(patientId).map(mapper::toDto).orElseGet(BillingProfileDto::empty);
  }

  /** Remplace le profil (PUT) : un champ null, ou une chaîne vide, est effacé. */
  @Transactional
  public BillingProfileDto replace(UUID patientId, BillingProfileRequest request) {
    CurrentPractitioner me = me();
    requireOwned(patientId);
    if (archived(patientId, me)) {
      throw BillingException.patientArchived();
    }
    BillingPatient profile =
        profiles
            .findById(patientId)
            .orElseGet(() -> new BillingPatient(patientId, me.tenantId(), me.id()));
    profile.replace(
        me.tenantId(),
        me.id(),
        request.rate(),
        blankToNull(request.addressLines()),
        blankToNull(request.thirdPartyName()),
        blankToNull(request.thirdPartyAddress()),
        now());
    return mapper.toDto(profiles.save(profile));
  }

  /** Texte sans espaces de bord ; vide → null. */
  static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /**
   * Garde des patients ; le patient d'un autre praticien (403 PATIENT_FORBIDDEN de la garde)
   * devient 404 PATIENT_NOT_FOUND, comme toute ressource d'un autre praticien en facturation.
   */
  private void requireOwned(UUID patientId) {
    try {
      guard.requireOwned(patientId);
    } catch (PatientAccessException e) {
      if (e.reason() == PatientAccessException.Reason.FORBIDDEN) {
        throw PatientAccessException.notFound();
      }
      throw e;
    }
  }

  /**
   * Patient archivé, lu par la liste des patients du praticien (la façade n'expose l'indicateur que
   * là, comme pour l'agenda). Absent de la liste après la garde : course avec un transfert, 404.
   */
  private boolean archived(UUID patientId, CurrentPractitioner me) {
    return patients.ownedBy(me.id()).stream()
        .filter(ref -> ref.id().equals(patientId))
        .findFirst()
        .map(PatientRef::archived)
        .orElseThrow(PatientAccessException::notFound);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
