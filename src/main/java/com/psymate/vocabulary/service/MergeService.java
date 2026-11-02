package com.psymate.vocabulary.service;

import com.psymate.vocabulary.api.EmotionResolution;
import com.psymate.vocabulary.api.MergeFacade;
import com.psymate.vocabulary.model.Tag;
import com.psymate.vocabulary.repository.EmotionMergeRepository;
import com.psymate.vocabulary.repository.TagRepository;
import com.psymate.vocabulary.service.MergeResolver.EmotionEdge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** État des fusions d'un praticien, lu par les abonnés des événements de fusion (M9-03). */
@Service
public class MergeService implements MergeFacade {

  private final EmotionMergeRepository merges;
  private final TagRepository tags;

  public MergeService(EmotionMergeRepository merges, TagRepository tags) {
    this.merges = merges;
    this.tags = tags;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EmotionResolution> emotionResolutions(UUID practitionerId, String code) {
    List<EmotionEdge> edges =
        merges.findByPractitionerId(practitionerId).stream()
            .map(m -> new EmotionEdge(m.getSourceCode(), m.getTargetCode(), m.isUndone()))
            .toList();
    return MergeResolver.emotions(edges, code);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UUID> tagResolutions(UUID practitionerId) {
    Map<UUID, UUID> mergedInto = new HashMap<>();
    for (Tag t : tags.findMerged(practitionerId)) {
      mergedInto.put(t.getId(), t.getMergedInto());
    }
    return MergeResolver.tags(mergedInto);
  }
}
