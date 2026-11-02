package com.psymate.linking.dto;

import com.psymate.linking.model.GraphView;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Graphe de la façade vers DTO REST (MapStruct). */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GraphMapper {

  GraphDto toDto(GraphView view);
}
