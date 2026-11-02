package com.psymate.patients.dto;

import com.psymate.patients.model.Patient;
import com.psymate.patients.model.PatientFlag;
import com.psymate.patients.model.PatientTag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entité vers DTO : regroupe par patient les drapeaux actifs et les tags chargés PAR LOT par {@code
 * PatientService} (une requête chacun, puis les libellés par la façade vocabulary), sans N+1. Les
 * entités ne sortent jamais du module : les Map jsonb sont copiées.
 */
public final class PatientAssembler {

  private PatientAssembler() {}

  private static PatientDto toDto(Patient p, List<FlagDto> flagDtos, List<TagDto> tagDtos) {
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

  /** Identifiants des tags de chaque patient, dans l'ordre de lecture. */
  public static Map<UUID, List<UUID>> tagIdsByPatient(List<PatientTag> tags) {
    Map<UUID, List<UUID>> tagIdsByPatient = new HashMap<>();
    for (PatientTag t : tags) {
      tagIdsByPatient
          .computeIfAbsent(t.getId().getPatientId(), k -> new ArrayList<>())
          .add(t.getId().getTagId());
    }
    return tagIdsByPatient;
  }

  /**
   * Un DTO par patient, dans l'ordre de {@code patients} ; un tag sans libellé (inconnu de
   * vocabulary) est omis, les tags sont triés par libellé.
   */
  public static List<PatientDto> withRelations(
      List<Patient> patients,
      List<PatientFlag> flags,
      Map<UUID, List<UUID>> tagIdsByPatient,
      Map<UUID, String> labels) {
    Map<UUID, List<FlagDto>> flagsByPatient = new HashMap<>();
    for (PatientFlag f : flags) {
      flagsByPatient
          .computeIfAbsent(f.getPatientId(), k -> new ArrayList<>())
          .add(new FlagDto(f.getKind(), f.getReason(), f.getSetAt()));
    }
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
