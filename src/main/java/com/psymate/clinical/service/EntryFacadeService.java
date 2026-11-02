package com.psymate.clinical.service;

import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.api.EntrySummary;
import com.psymate.clinical.api.EntryVersionView;
import com.psymate.clinical.model.Entry;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.repository.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientFacade;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
  public Optional<EntryVersionView> currentVersionOf(UUID entryId) {
    return ownedPatientOf(entryId)
        .flatMap(p -> repo.currentVersion(entryId))
        .map(
            v ->
                new EntryVersionView(
                    v.id(), v.entryId(), v.version(), v.title(), v.body(), v.createdAt()));
  }

  @Override
  public Optional<UUID> patientOf(UUID entryId) {
    return repo.patientOf(entryId);
  }

  @Override
  @Transactional
  public void adjustLinkCount(UUID entryId, int delta) {
    repo.adjustLinkCount(entryId, delta);
  }

  @Override
  public boolean isArchived(UUID entryId) {
    return repo.find(entryId).map(Entry::archived).orElse(false);
  }

  /** Une seule vérification de propriété par patient distinct du lot (ADR-09). */
  @Override
  public Map<UUID, EntrySummary> summariesOf(Collection<UUID> entryIds) {
    Optional<CurrentPractitioner> me = CurrentPractitioner.get();
    if (me.isEmpty() || entryIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, EntrySummary> all = repo.summaries(entryIds);
    Map<UUID, Boolean> owned = new HashMap<>();
    Map<UUID, EntrySummary> out = new LinkedHashMap<>();
    all.forEach(
        (id, s) -> {
          if (owned.computeIfAbsent(s.patientId(), p -> patients.ownedBy(p, me.get().id()))) {
            out.put(id, s);
          }
        });
    return out;
  }
}
