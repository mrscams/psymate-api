package com.psymate.clinical.application;

import com.psymate.clinical.api.EntriesRelabeled;
import com.psymate.clinical.persistence.RelabelRepository;
import com.psymate.clinical.persistence.RelabelRepository.RelabeledEntry;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.patients.api.PatientFacade;
import com.psymate.vocabulary.api.EmotionResolution;
import com.psymate.vocabulary.api.MergeFacade;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réécrit les ressentis et les tags des entrées après une fusion du vocabulaire (M9-03, revue
 * ARCH-01), dans les tables de clinical. L'état voulu est relu dans vocabulary ({@link
 * MergeFacade}) sous un verrou par praticien, jamais déduit de l'événement : l'ordre d'arrivée des
 * fusions et des annulations ne compte pas, et un rejeu ne change rien.
 *
 * <p>Pour chaque patient touché : {@code patient.clinical_rev} est décalé (la vue d'ensemble
 * stockée devient périmée) et {@link EntriesRelabeled} est publié (recherche et vue d'ensemble se
 * recalculent). Aucune version n'est créée : la fusion change le libellé porté, pas le contenu
 * clinique, et {@code original_emotion_code} garde l'émotion d'origine.
 */
@Service
public class EntryRelabelService {

  private final RelabelRepository repo;
  private final MergeFacade merges;
  private final PatientFacade patients;
  private final DomainEventPublisher events;

  EntryRelabelService(
      RelabelRepository repo,
      MergeFacade merges,
      PatientFacade patients,
      DomainEventPublisher events) {
    this.repo = repo;
    this.merges = merges;
    this.patients = patients;
    this.events = events;
  }

  /** Réécrit les ressentis concernés par une fusion (ou son annulation) touchant {@code code}. */
  @Transactional
  public void relabelFeelings(UUID tenantId, UUID practitionerId, String code) {
    repo.lockPractitioner(practitionerId);
    List<RelabeledEntry> changed = new ArrayList<>();
    for (EmotionResolution r : merges.emotionResolutions(practitionerId, code)) {
      changed.addAll(repo.relabelFeelings(r.sourceCode(), r.currentCode(), r.historicalCodes()));
    }
    propagate(tenantId, changed);
  }

  /** Remplace, dans les entrées, chaque tag archivé du praticien par sa cible finale. */
  @Transactional
  public void relabelTags(UUID tenantId, UUID practitionerId) {
    repo.lockPractitioner(practitionerId);
    propagate(tenantId, repo.relabelTags(merges.tagResolutions(practitionerId)));
  }

  private void propagate(UUID tenantId, List<RelabeledEntry> changed) {
    Map<UUID, Set<UUID>> byPatient = new LinkedHashMap<>();
    for (RelabeledEntry e : changed) {
      byPatient.computeIfAbsent(e.patientId(), k -> new LinkedHashSet<>()).add(e.entryId());
    }
    byPatient.forEach(
        (patientId, entryIds) -> {
          patients.touchClinicalRev(patientId);
          events.publish(new EntriesRelabeled(tenantId, patientId, List.copyOf(entryIds)));
        });
  }
}
