package com.psymate.linking.web;

import com.psymate.linking.domain.Link;
import com.psymate.linking.web.LinkDtos.LinkDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Lien du domaine vers DTO REST (MapStruct). Le libellé et la phrase sont calculés à part. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LinkMapper {

  @Mapping(target = "id", source = "link.id")
  @Mapping(target = "sourceId", source = "link.sourceId")
  @Mapping(target = "targetId", source = "link.targetId")
  @Mapping(target = "linkType", source = "link.linkType")
  @Mapping(target = "label", source = "label")
  @Mapping(target = "sentence", source = "sentence")
  @Mapping(target = "provenance", expression = "java(link.provenance().code())")
  @Mapping(target = "status", expression = "java(link.status().code())")
  @Mapping(target = "rationale", source = "link.rationale")
  @Mapping(target = "createdAt", source = "link.createdAt")
  @Mapping(target = "authorId", source = "link.authorId")
  LinkDto toDto(Link link, String label, String sentence);
}
