package com.psymate.patients;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.patients.api.PatientUpdated;
import com.psymate.patients.persistence.PatientTagRepository;
import com.psymate.vocabulary.api.MergeFacade;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réécrit {@code patient_tag} après une fusion de tags (M9-03, revue ARCH-01) : chaque tag archivé
 * du praticien est remplacé par sa cible finale, relue dans vocabulary ({@link MergeFacade}) sous
 * un verrou par praticien. L'ordre d'arrivée des fusions ne compte donc pas, et un rejeu ne change
 * rien. Chaque patient touché reçoit {@link PatientUpdated}, comme après PUT /tags.
 */
@Service
class PatientTagRelabelService {

  private final PatientTagRepository patientTags;
  private final MergeFacade merges;
  private final DomainEventPublisher events;

  PatientTagRelabelService(
      PatientTagRepository patientTags, MergeFacade merges, DomainEventPublisher events) {
    this.patientTags = patientTags;
    this.merges = merges;
    this.events = events;
  }

  @Transactional
  void relabel(UUID tenantId, UUID practitionerId) {
    patientTags.lockPractitioner(practitionerId.toString());
    Set<UUID> touched = new TreeSet<>();
    for (Map.Entry<UUID, UUID> m : merges.tagResolutions(practitionerId).entrySet()) {
      touched.addAll(patientTags.findPatientIdsByTagId(m.getKey()));
      patientTags.copyTag(m.getKey(), m.getValue());
      patientTags.deleteByTagId(m.getKey());
    }
    touched.forEach(
        patientId -> events.publish(new PatientUpdated(tenantId, patientId, practitionerId)));
  }
}
