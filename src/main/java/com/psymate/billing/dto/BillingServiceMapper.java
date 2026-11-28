package com.psymate.billing.dto;

import com.psymate.billing.model.BillingService;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Prestation du domaine vers DTO REST (MapStruct, G Q46). Prix en centimes (G Q84). */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BillingServiceMapper {

  @Mapping(target = "price", source = "priceCents")
  @Mapping(target = "isDefault", source = "default")
  BillingServiceDto toDto(BillingService service);

  List<BillingServiceDto> toDtos(List<BillingService> services);
}
