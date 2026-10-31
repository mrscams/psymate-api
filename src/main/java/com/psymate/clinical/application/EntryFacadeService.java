package com.psymate.clinical.application;

import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.api.EntryVersionView;
import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.persistence.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la façade de lecture exposée aux autres modules. Aucune réponse ne révèle
 * l'existence d'une entrée dont le patient n'appartient pas au praticien courant (ADR-09).
 */
@Service
@Transactional(readOnly = true)
class EntryFacadeService implements EntryFacade {

  private final EntryRepository repo;
  private final PatientFacade patients;

  EntryFacadeService(EntryRepository repo, PatientFacade patients) {
    this.repo = repo;
    this.patients = patients;
  }

  /** Patient de l'entrée, seulement s'il appartient au praticien courant. */
  private Optional<UUID> ownedPatientOf(UUID entryId) {
    Optional<CurrentPractitioner> me = CurrentPractitioner.get();
    if (me.isEmpty()) {
      return Optional.empty();
    }
    return repo.patientOf(entryId).filter(p -> patients.ownedBy(p, me.get().id()));
  }

  /** Le patient appartient au praticien courant. */
  private boolean ownedByMe(UUID patientId) {
    return CurrentPractitioner.get().filter(me -> patients.ownedBy(patientId, me.id())).isPresent();
  }

  @Override
  public Optional<SessionView> currentSession(UUID patientId) {
    return ownedByMe(patientId) ? repo.currentSession(patientId) : Optional.empty();
  }

  @Override
  public Optional<SessionView> openSessionOf(UUID patientId) {
    return ownedByMe(patientId) ? repo.openSession(patientId) : Optional.empty();
  }

  @Override
  public boolean exists(UUID entryId) {
    return ownedPatientOf(entryId).isPresent();
  }

  @Override
  public boolean belongsToPatient(UUID entryId, UUID patientId) {
    return ownedPatientOf(entryId).filter(patientId::equals).isPresent();
  }

  @Override
  public Optional<String> typeOf(UUID entryId) {
    return ownedPatientOf(entryId).flatMap(p -> repo.typeOf(entryId)).map(EntryType::code);
  }

  @Override
  public boolean sameOwnedPatient(UUID entryA, UUID entryB) {
    Optional<UUID> a = ownedPatientOf(entryA);
    return a.isPresent() && a.equals(ownedPatientOf(entryB));
  }

  @Override
  public Optional<EntryVersionView> currentVersionOf(UUID entryId) {
    return ownedPatientOf(entryId)
        .flatMap(p -> repo.currentVersion(entryId))
        .map(
            v ->
                new EntryVersionView(
                    v.id(), v.entryId(), v.version(), v.title(), v.body(), v.createdAt()));
  }
}
