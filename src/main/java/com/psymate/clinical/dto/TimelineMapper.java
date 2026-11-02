package com.psymate.clinical.dto;

import com.psymate.clinical.model.TimeSpan;
import com.psymate.clinical.model.TimelineFacets;
import com.psymate.clinical.model.TimelineItem;
import com.psymate.clinical.model.TimelinePage;
import com.psymate.clinical.model.TimelineSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Timeline du domaine vers DTO REST (MapStruct). authorType vient de la colonne. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TimelineMapper {

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
