package com.psymate.billing.dto;

import com.psymate.billing.model.BillingPatient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Profil de facturation du domaine vers DTO REST (MapStruct, G Q46). Tarif en centimes (G Q84).
 * Sans ligne en base, le profil est vide ({@link BillingProfileDto#empty()}).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BillingProfileMapper {

  @Mapping(target = "rate", source = "rateCents")
  BillingProfileDto toDto(BillingPatient profile);
}
