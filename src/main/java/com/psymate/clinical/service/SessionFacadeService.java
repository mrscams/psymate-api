package com.psymate.clinical.service;

import com.psymate.clinical.api.SessionFacade;
import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.repository.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de {@link SessionFacade}. Comme {@link EntryFacadeService}, ne révèle aucune
 * séance dont le patient n'appartient pas au praticien courant (ADR-09).
 */
@Service
@Transactional(readOnly = true)
class SessionFacadeService implements SessionFacade {

  private final EntryRepository repo;
  private final PatientFacade patients;

  SessionFacadeService(EntryRepository repo, PatientFacade patients) {
    this.repo = repo;
    this.patients = patients;
  }

  @Override
  public Optional<SessionView> closedSession(UUID sessionId) {
    Optional<CurrentPractitioner> me = CurrentPractitioner.get();
    if (me.isEmpty() || sessionId == null) {
      return Optional.empty();
    }
    return repo.closedSession(sessionId)
        .filter(s -> patients.ownedBy(s.patientId(), me.get().id()));
  }
}
