package com.psymate.linking.web;

import com.psymate.linking.application.GraphView;
import com.psymate.linking.web.GraphDtos.GraphDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Graphe de la façade vers DTO REST (MapStruct). */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface GraphMapper {

  GraphDto toDto(GraphView view);
}
