package com.psymate.clinical.web;

import com.psymate.clinical.application.EntryDetails;
import com.psymate.clinical.application.EntryHistory;
import com.psymate.clinical.domain.EntryVersion;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.domain.MedicationDetail;
import com.psymate.clinical.domain.SessionDetail;
import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.web.EntryDtos.EntryDto;
import com.psymate.clinical.web.EntryDtos.EntryFeelingDto;
import com.psymate.clinical.web.EntryDtos.EntryLinkDto;
import com.psymate.clinical.web.EntryDtos.EntryMedicationDto;
import com.psymate.clinical.web.EntryDtos.EntryOccurredDto;
import com.psymate.clinical.web.EntryDtos.EntrySessionDto;
import com.psymate.clinical.web.EntryDtos.EntryVersionDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entrée du domaine vers DTO REST (MapStruct). authorType vient de la colonne, en lecture seule.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EntryMapper {

  @Mapping(target = "id", source = "entry.id")
  @Mapping(target = "patientId", source = "entry.patientId")
  @Mapping(target = "type", expression = "java(details.entry().type().code())")
  @Mapping(target = "status", expression = "java(details.entry().status().code())")
  @Mapping(target = "visibility", expression = "java(details.entry().visibility().code())")
  @Mapping(target = "authorType", source = "entry.authorType")
  @Mapping(target = "occurred", source = "entry.time")
  @Mapping(target = "recordedAt", source = "recordedAt")
  @Mapping(target = "sessionId", source = "entry.sessionId")
  @Mapping(target = "version", source = "entry.currentVersion")
  @Mapping(target = "title", source = "version.title")
  @Mapping(target = "body", source = "version.body")
  @Mapping(target = "tags", source = "tags")
  @Mapping(target = "feeling", expression = "java(feeling(details))")
  @Mapping(target = "medication", source = "medication")
  @Mapping(target = "session", source = "session")
  @Mapping(target = "linkCount", source = "entry.linkCount")
  @Mapping(target = "links", ignore = true)
  @Mapping(target = "archivedAt", source = "entry.archivedAt")
  EntryDto toDto(EntryDetails details);

  /** Réponse de création : le DTO complet avec les liens posés (null partout ailleurs). */
  default EntryDto withLinks(EntryDto dto, List<EntryLinkDto> links) {
    return new EntryDto(
        dto.id(),
        dto.patientId(),
        dto.type(),
        dto.status(),
        dto.visibility(),
        dto.authorType(),
        dto.occurred(),
        dto.recordedAt(),
        dto.sessionId(),
        dto.version(),
        dto.title(),
        dto.body(),
        dto.tags(),
        dto.feeling(),
        dto.medication(),
        dto.session(),
        dto.linkCount(),
        links,
        dto.archivedAt());
  }

  @Mapping(target = "precision", expression = "java(time.precision().code())")
  EntryOccurredDto toOccurred(TimeSpan time);

  EntryMedicationDto toMedication(MedicationDetail medication);

  EntrySessionDto toSession(SessionDetail session);

  /**
   * Valence et activation sont celles copiées à la saisie (A4.5) ; la famille vient du catalogue.
   */
  default EntryFeelingDto feeling(EntryDetails details) {
    FeelingDetail f = details.feeling();
    if (f == null) {
      return null;
    }
    return new EntryFeelingDto(
        f.emotionCode(), details.emotionFamily(), f.intensity(), f.valence(), f.arousal());
  }

  @Mapping(target = "authorType", source = "authorType")
  EntryVersionDto toVersionDto(EntryVersion version, String authorType);

  default List<EntryVersionDto> toVersionDtos(EntryHistory history) {
    return history.versions().stream().map(v -> toVersionDto(v, history.authorType())).toList();
  }
}
