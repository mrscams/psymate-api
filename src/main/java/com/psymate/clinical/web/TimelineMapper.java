package com.psymate.clinical.web;

import com.psymate.clinical.application.TimelineFacets;
import com.psymate.clinical.application.TimelineItem;
import com.psymate.clinical.application.TimelinePage;
import com.psymate.clinical.application.TimelineSummary;
import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.web.EntryDtos.EntryOccurredDto;
import com.psymate.clinical.web.TimelineDtos.TimelineEmotionDto;
import com.psymate.clinical.web.TimelineDtos.TimelineFacetsDto;
import com.psymate.clinical.web.TimelineDtos.TimelineItemDto;
import com.psymate.clinical.web.TimelineDtos.TimelinePageDto;
import com.psymate.clinical.web.TimelineDtos.TimelinePeriodDto;
import com.psymate.clinical.web.TimelineDtos.TimelineSummaryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Timeline du domaine vers DTO REST (MapStruct). authorType vient de la colonne. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface TimelineMapper {

  TimelinePageDto toPage(TimelinePage page);

  @Mapping(target = "emotion", expression = "java(emotion(item))")
  TimelineItemDto toItem(TimelineItem item);

  @Mapping(target = "precision", expression = "java(time.precision().code())")
  EntryOccurredDto toOccurred(TimeSpan time);

  TimelineFacetsDto toFacets(TimelineFacets facets);

  TimelineSummaryDto toSummary(TimelineSummary summary);

  TimelinePeriodDto toPeriod(TimelineSummary.Period period);

  /** Seul un ressenti porte une émotion. */
  default TimelineEmotionDto emotion(TimelineItem item) {
    return item.emotionCode() == null
        ? null
        : new TimelineEmotionDto(item.emotionCode(), item.emotionFamily());
  }
}
