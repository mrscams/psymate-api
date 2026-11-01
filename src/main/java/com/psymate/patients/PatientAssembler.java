package com.psymate.patients;

import com.psymate.patients.PatientDtos.FlagDto;
import com.psymate.patients.PatientDtos.PatientDto;
import com.psymate.patients.PatientDtos.TagDto;
import com.psymate.patients.persistence.Patient;
import com.psymate.patients.persistence.PatientFlag;
import com.psymate.patients.persistence.PatientFlagRepository;
import com.psymate.patients.persistence.PatientTag;
import com.psymate.patients.persistence.PatientTagRepository;
import com.psymate.vocabulary.api.TagFacade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entité vers DTO : charge les drapeaux actifs et les tags de plusieurs patients PAR LOT (une
 * requête chacun, puis les libellés par la façade vocabulary), sans N+1. Les entités ne sortent
 * jamais du module : les Map jsonb sont copiées.
 */
@Component
class PatientAssembler {

  private final PatientFlagRepository flags;
  private final PatientTagRepository tags;
  private final TagFacade tagFacade;

  PatientAssembler(PatientFlagRepository flags, PatientTagRepository tags, TagFacade tagFacade) {
    this.flags = flags;
    this.tags = tags;
    this.tagFacade = tagFacade;
  }

  PatientDto toDto(Patient p, List<FlagDto> flagDtos, List<TagDto> tagDtos) {
    return new PatientDto(
        p.getId(),
        p.getDisplayName(),
        p.getFirstName(),
        p.getLastName(),
        p.getBirthDate(),
        p.getPronoun(),
        p.getPhone(),
        p.getEmail(),
        new LinkedHashMap<>(p.getContext()),
        new LinkedHashMap<>(p.getFollowUp()),
        flagDtos,
        tagDtos,
        p.getCurrentVersion(),
        p.getArchivedAt(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  List<PatientDto> withRelations(List<Patient> patients) {
    if (patients.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = patients.stream().map(Patient::getId).toList();
    Map<UUID, List<FlagDto>> flagsByPatient = new HashMap<>();
    for (PatientFlag f : flags.findByPatientIdInAndClearedAtIsNullOrderBySetAt(ids)) {
      flagsByPatient
          .computeIfAbsent(f.getPatientId(), k -> new ArrayList<>())
          .add(new FlagDto(f.getKind(), f.getReason(), f.getSetAt()));
    }
    Map<UUID, List<UUID>> tagIdsByPatient = new HashMap<>();
    for (PatientTag t : tags.findByIdPatientIdIn(ids)) {
      tagIdsByPatient
          .computeIfAbsent(t.getId().getPatientId(), k -> new ArrayList<>())
          .add(t.getId().getTagId());
    }
    Map<UUID, String> labels =
        tagFacade.labelsByIds(
            tagIdsByPatient.values().stream().flatMap(List::stream).distinct().toList());
    List<PatientDto> out = new ArrayList<>();
    for (Patient p : patients) {
      List<TagDto> tagDtos =
          tagIdsByPatient.getOrDefault(p.getId(), List.of()).stream()
              .filter(labels::containsKey)
              .map(t -> new TagDto(t, labels.get(t)))
              .sorted(Comparator.comparing(TagDto::label))
              .toList();
      out.add(toDto(p, flagsByPatient.getOrDefault(p.getId(), List.of()), tagDtos));
    }
    return out;
  }
}
