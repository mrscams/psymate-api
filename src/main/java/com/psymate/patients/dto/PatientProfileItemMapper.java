package com.psymate.patients.dto;

import com.psymate.patients.model.PatientProfileItem;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Information du Profil 360 vers DTO REST (MapStruct, G Q46) : la rubrique sort en texte (valeur
 * SQL), l'année en entier. L'entité ne quitte jamais le service.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PatientProfileItemMapper {

  PatientProfileItemDto toDto(PatientProfileItem item);
}
