package com.psymate.vocabulary.service;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.shared.time.Timestamps;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import com.psymate.vocabulary.api.TagsMerged;
import com.psymate.vocabulary.model.Emotion;
import com.psymate.vocabulary.model.Tag;
import com.psymate.vocabulary.model.TagMergePolicy;
import com.psymate.vocabulary.model.TextNormalizer;
import com.psymate.vocabulary.model.VocabularyException;
import com.psymate.vocabulary.repository.EmotionRepository;
import com.psymate.vocabulary.repository.TagRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TagService implements TagFacade {

  private final TagRepository tags;
  private final EmotionRepository emotions;
  private final TransactionTemplate tx;
  private final DomainEventPublisher events;

  public TagService(
      TagRepository tags,
      EmotionRepository emotions,
      TransactionTemplate tx,
      DomainEventPublisher events) {
    this.tags = tags;
    this.emotions = emotions;
    this.tx = tx;
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TagView> tagsOfCurrentPractitioner() {
    return tags.findActive(me().id()).stream().map(VocabularyViews::tag).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TagView> resolve(String label) {
    return tags.findActiveByNormalized(me().id(), TextNormalizer.normalize(label))
        .map(VocabularyViews::tag);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, String> labelsByIds(Collection<UUID> ids) {
    Map<UUID, String> out = new HashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    for (Tag t : tags.findActiveByIds(me().id(), ids)) {
      out.put(t.getId(), t.getLabel());
    }
    return out;
  }

  /**
   * Pas de {@code @Transactional} ici : l'unicité du libellé actif (ux_tag_practitioner_label) est
   * contrôlée au flush / commit, donc le 409 d'une course est attrapé APRÈS le commit (même schéma
   * que PatientService.addFlag).
   */
  public TagView create(String label) {
    String clean = validate(label);
    try {
      return tx.execute(status -> doCreate(clean));
    } catch (DataIntegrityViolationException race) {
      throw conflict();
    }
  }

  private TagView doCreate(String clean) {
    UUID practitioner = me().id();
    String norm = TextNormalizer.normalize(clean);
    checkNotEmotion(practitioner, norm);
    if (tags.findActiveByNormalized(practitioner, norm).isPresent()) {
      throw conflict();
    }
    Tag created =
        tags.save(
            new Tag(
                UUID.randomUUID(), me().tenantId(), practitioner, clean, norm, Timestamps.now()));
    return VocabularyViews.tag(created);
  }

  /** Même principe que {@link #create} : le renommage peut violer l'unicité au commit. */
  public TagView rename(UUID id, String label) {
    try {
      return tx.execute(status -> doRename(id, label));
    } catch (DataIntegrityViolationException race) {
      throw conflict();
    }
  }

  private TagView doRename(UUID id, String label) {
    UUID practitioner = me().id();
    Tag tag = tags.findActiveById(practitioner, id).orElseThrow(TagService::notFound);
    String clean = validate(label);
    String norm = TextNormalizer.normalize(clean);
    checkNotEmotion(practitioner, norm);
    Optional<Tag> same = tags.findActiveByNormalized(practitioner, norm);
    if (same.isPresent() && !same.get().getId().equals(id)) {
      throw conflict();
    }
    tag.rename(clean, norm);
    return VocabularyViews.tag(tag);
  }

  /** Fusionne la source dans la cible : réétiquette, archive la source (merged_into). */
  @Transactional
  public TagView merge(UUID sourceId, UUID targetId) {
    UUID practitioner = me().id();
    Tag source = tags.findActiveById(practitioner, sourceId).orElseThrow(TagService::notFound);
    TagMergePolicy.check(sourceId, targetId);
    Tag target = tags.findActiveById(practitioner, targetId).orElseThrow(TagService::notFound);
    // usage_count en natif (la cible est rafraîchie dans le fragment) ; entry_tag et patient_tag
    // sont réécrits par clinical et patients, abonnés de TagsMerged (M9-03).
    tags.addUsageCount(sourceId, targetId);
    source.archiveInto(targetId, Timestamps.now());
    events.publish(new TagsMerged(me().tenantId(), practitioner, sourceId, targetId));
    return VocabularyViews.tag(target);
  }

  /**
   * Praticien de la session (identity.api). Les endpoints du module sont protégés par la sécurité
   * d'identity : une requête sans principal est une erreur de configuration, jamais un accès
   * anonyme toléré.
   */
  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new IllegalStateException("Aucun praticien authentifié"));
  }

  private void checkNotEmotion(UUID practitioner, String norm) {
    Optional<Emotion> e = emotions.findFirstActiveByNormalized(practitioner, norm);
    if (e.isPresent()) {
      String label = e.get().getLabelFr();
      throw new VocabularyException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "TAG_IS_EMOTION",
          "Ce libellé est déjà une émotion : utilisez « " + label + " ».",
          List.of(
              new VocabularyException.FieldError(
                  "label", "Ce libellé correspond à l'émotion « " + label + " ».")),
          Map.of("suggestedEmotion", VocabularyViews.emotion(e.get())));
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
