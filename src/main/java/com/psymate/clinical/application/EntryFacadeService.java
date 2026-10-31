package com.psymate.clinical.application;

import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.api.EntryVersionView;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.persistence.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation de la façade de lecture exposée aux autres modules. */
@Service
@Transactional(readOnly = true)
class EntryFacadeService implements EntryFacade {

  private final EntryRepository repo;
  private final PatientFacade patients;

  EntryFacadeService(EntryRepository repo, PatientFacade patients) {
    this.repo = repo;
    this.patients = patients;
  }

  @Override
  public boolean exists(UUID entryId) {
    return repo.patientOf(entryId).isPresent();
  }

  @Override
  public boolean belongsToPatient(UUID entryId, UUID patientId) {
    return repo.patientOf(entryId).filter(patientId::equals).isPresent();
  }

  @Override
  public Optional<String> typeOf(UUID entryId) {
    return repo.typeOf(entryId).map(EntryType::code);
  }

  @Override
  public boolean sameOwnedPatient(UUID entryA, UUID entryB) {
    Optional<UUID> a = repo.patientOf(entryA);
    Optional<UUID> b = repo.patientOf(entryB);
    if (a.isEmpty() || b.isEmpty() || !a.get().equals(b.get())) {
      return false;
    }
    return CurrentPractitioner.get().map(p -> patients.ownedBy(a.get(), p.id())).orElse(false);
  }

  @Override
  public Optional<EntryVersionView> currentVersionOf(UUID entryId) {
    return repo.currentVersion(entryId)
        .map(
            v ->
                new EntryVersionView(
                    v.id(), v.entryId(), v.version(), v.title(), v.body(), v.createdAt()));
  }
}
