package com.psymate.vocabulary;

import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TagService implements TagFacade {

  private final VocabularyRepository repo;
  private final CurrentPractitioner current;

  TagService(VocabularyRepository repo, CurrentPractitioner current) {
    this.repo = repo;
    this.current = current;
  }

  @Override
  public List<TagView> tagsOfCurrentPractitioner() {
    return repo.activeTags(current.id());
  }

  @Override
  public Optional<TagView> resolve(String label) {
    return repo.activeTagByNormalized(current.id(), TextNormalizer.normalize(label));
  }

  @Override
  public java.util.Map<UUID, String> labelsByIds(java.util.Collection<UUID> ids) {
    return repo.activeTagLabels(current.id(), ids);
  }

  @Transactional
  TagView create(String label) {
    UUID practitioner = current.id();
    String clean = validate(label);
    String norm = TextNormalizer.normalize(clean);
    checkNotEmotion(practitioner, norm);
    if (repo.activeTagByNormalized(practitioner, norm).isPresent()) {
      throw conflict();
    }
    UUID id = UUID.randomUUID();
    try {
      repo.insertTag(id, current.tenantId(), practitioner, clean, norm);
    } catch (DuplicateKeyException e) {
      throw conflict();
    }
    return repo.activeTag(practitioner, id).orElseThrow();
  }

  @Transactional
  TagView rename(UUID id, String label) {
    UUID practitioner = current.id();
    repo.activeTag(practitioner, id).orElseThrow(TagService::notFound);
    String clean = validate(label);
    String norm = TextNormalizer.normalize(clean);
    checkNotEmotion(practitioner, norm);
    Optional<TagView> same = repo.activeTagByNormalized(practitioner, norm);
    if (same.isPresent() && !same.get().id().equals(id)) {
      throw conflict();
    }
    repo.renameTag(id, clean, norm);
    return repo.activeTag(practitioner, id).orElseThrow();
  }

  /** Fusionne la source dans la cible : réétiquette, archive la source (merged_into). */
  @Transactional
  TagView merge(UUID sourceId, UUID targetId) {
    UUID practitioner = current.id();
    repo.activeTag(practitioner, sourceId).orElseThrow(TagService::notFound);
    TagMergePolicy.check(sourceId, targetId);
    repo.activeTag(practitioner, targetId).orElseThrow(TagService::notFound);
    repo.mergeTags(sourceId, targetId);
    return repo.activeTag(practitioner, targetId).orElseThrow();
  }

  private void checkNotEmotion(UUID practitioner, String norm) {
    Optional<EmotionView> e = repo.activeEmotionByNormalized(practitioner, norm);
    if (e.isPresent()) {
      String label = e.get().labelFr();
      throw new VocabularyException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "TAG_IS_EMOTION",
          "Ce libellé est déjà une émotion : utilisez « " + label + " ».",
          List.of(
              new VocabularyException.FieldError(
                  "label", "Ce libellé correspond à l'émotion « " + label + " ».")),
          Map.of("suggestedEmotion", e.get()));
    }
  }

  private static String validate(String label) {
    if (label == null || TextNormalizer.normalize(label).isEmpty()) {
      throw VocabularyException.invalid("label", "Le libellé est obligatoire.");
    }
    return label.replace("#", "").trim();
  }

  private static VocabularyException conflict() {
    return VocabularyException.of(
        HttpStatus.CONFLICT, "TAG_EXISTS", "Un tag porte déjà ce libellé.");
  }

  private static VocabularyException notFound() {
    return VocabularyException.of(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "Tag introuvable.");
  }
}
