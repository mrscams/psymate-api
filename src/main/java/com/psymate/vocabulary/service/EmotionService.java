package com.psymate.vocabulary.service;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.shared.time.Timestamps;
import com.psymate.vocabulary.api.EmotionAffect;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionMergeReverted;
import com.psymate.vocabulary.api.EmotionUsagePort;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.EmotionsMerged;
import com.psymate.vocabulary.dto.MergeView;
import com.psymate.vocabulary.model.Emotion;
import com.psymate.vocabulary.model.EmotionMerge;
import com.psymate.vocabulary.model.TextNormalizer;
import com.psymate.vocabulary.model.VocabularyException;
import com.psymate.vocabulary.repository.EmotionMergeRepository;
import com.psymate.vocabulary.repository.EmotionRepository;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmotionService implements EmotionCatalog {

  public static final Set<String> FAMILIES =
      Set.of("joy", "trust", "fear", "surprise", "sadness", "disgust", "anger", "anticipation");
  public static final int UNDO_DAYS = 30;

  private final EmotionRepository emotions;
  private final EmotionMergeRepository merges;
  private final TransactionTemplate tx;
  private final DomainEventPublisher events;
  private final EmotionUsagePort usage;

  public EmotionService(
      EmotionRepository emotions,
      EmotionMergeRepository merges,
      TransactionTemplate tx,
      DomainEventPublisher events,
      EmotionUsagePort usage) {
    this.emotions = emotions;
    this.merges = merges;
    this.tx = tx;
    this.events = events;
    this.usage = usage;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EmotionView> activeEmotions() {
    return emotions.findActiveFor(me().id()).stream().map(VocabularyViews::emotion).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmotionView> findActiveByLabel(String label) {
    return emotions
        .findFirstActiveByNormalized(me().id(), TextNormalizer.normalize(label))
        .map(VocabularyViews::emotion);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmotionAffect> affectOf(String code) {
    return emotions.findUsable(me().id(), code).map(VocabularyViews::affect);
  }

  /**
   * Pas de {@code @Transactional} ici : l'unicité du libellé (ux_emotion_custom_label) est
   * contrôlée au flush / commit, donc le 409 d'une course est attrapé APRÈS le commit (même schéma
   * que PatientService.addFlag).
   */
  public EmotionView create(String labelFr, String family) {
    String label = validateLabel(labelFr);
    if (family == null || family.isBlank()) {
      throw VocabularyException.invalid("family", "La famille est obligatoire.");
    }
    if (!FAMILIES.contains(family)) {
      throw VocabularyException.invalid("family", "Famille inconnue : " + family + ".");
    }
    try {
      return tx.execute(status -> doCreate(label, family));
    } catch (DataIntegrityViolationException race) {
      throw conflict();
    }
  }

  private EmotionView doCreate(String label, String family) {
    UUID practitioner = me().id();
    String norm = TextNormalizer.normalize(label);
    if (emotions.labelTaken(practitioner, norm, "")) {
      throw conflict();
    }
    String code = "c_" + HexFormat.of().formatHex(randomBytes());
    Emotion created =
        emotions.save(
            Emotion.custom(
                code, me().tenantId(), practitioner, family, label, norm, Timestamps.now()));
    return VocabularyViews.emotion(created);
  }

  /** Même principe que {@link #create} : le renommage peut violer l'unicité au commit. */
  public EmotionView rename(String code, String labelFr) {
    try {
      return tx.execute(status -> doRename(code, labelFr));
    } catch (DataIntegrityViolationException race) {
      throw conflict();
    }
  }

  private EmotionView doRename(String code, String labelFr) {
    UUID practitioner = me().id();
    Emotion e = activeOwn(practitioner, code);
    String label = validateLabel(labelFr);
    String norm = TextNormalizer.normalize(label);
    if (emotions.labelTaken(practitioner, norm, e.getCode())) {
      throw conflict();
    }
    e.rename(label, norm);
    return VocabularyViews.emotion(e);
  }

  /** Fusionne une custom active dans une globale active ou une custom active du praticien. */
  @Transactional
  public MergeView merge(String sourceCode, String targetCode) {
    UUID practitioner = me().id();
    // Verrou de la source d'abord : deux fusions concurrentes de la même émotion se sérialisent.
    Emotion source =
        emotions.findByCodeForUpdate(sourceCode).orElseThrow(EmotionService::emotionNotFound);
    requireActiveOwn(practitioner, source);
    if (targetCode == null || targetCode.isBlank()) {
      throw VocabularyException.invalid("targetCode", "La cible est obligatoire.");
    }
    if (targetCode.equals(source.getCode())) {
      throw VocabularyException.invalid("targetCode", "La cible doit différer de la source.");
    }
    emotions
        .findUsable(practitioner, targetCode)
        .orElseThrow(
            () ->
                VocabularyException.of(
                    HttpStatus.NOT_FOUND, "EMOTION_NOT_FOUND", "Émotion cible introuvable."));
    // Les ressentis sont réécrits par clinical, abonné d'EmotionsMerged (M9-03) ; le compte est
    // celui des ressentis qui portent la source au moment de la fusion.
    int affected = usage.feelingsWith(sourceCode);
    EmotionMerge merge =
        merges.save(
            EmotionMerge.open(
                UUID.randomUUID(),
                practitioner,
                sourceCode,
                targetCode,
                affected,
                Timestamps.now(),
                UNDO_DAYS));
    source.deactivate();
    events.publish(
        new EmotionsMerged(me().tenantId(), merge.getId(), practitioner, sourceCode, targetCode));
    return view(merge);
  }

  /** Annule une fusion dans les 30 jours ; après undo_until ou déjà annulée : 409. */
  @Transactional
  public MergeView undo(UUID mergeId) {
    UUID practitioner = me().id();
    // Fusion verrouillée : deux undo concurrents se sérialisent, le second voit undone_at posé.
    EmotionMerge m =
        merges
            .findOwnForUpdate(mergeId, practitioner)
            .orElseThrow(
                () ->
                    VocabularyException.of(
                        HttpStatus.NOT_FOUND, "MERGE_NOT_FOUND", "Fusion introuvable."));
    if (m.isUndone()) {
      throw VocabularyException.of(
          HttpStatus.CONFLICT, "MERGE_ALREADY_UNDONE", "Cette fusion a déjà été annulée.");
    }
    OffsetDateTime now = Timestamps.now();
    if (!m.isUndoableAt(now)) {
      throw VocabularyException.of(
          HttpStatus.CONFLICT,
          "MERGE_UNDO_EXPIRED",
          "Le délai d'annulation de 30 jours est dépassé.");
    }
    m.markUndone(now);
    emotions.findById(m.getSourceCode()).ifPresent(Emotion::activate);
    events.publish(
        new EmotionMergeReverted(
            me().tenantId(), m.getId(), practitioner, m.getSourceCode(), m.getTargetCode()));
    return view(m);
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

  private Emotion activeOwn(UUID practitioner, String code) {
    Emotion e =
        emotions.findOwnCustom(practitioner, code).orElseThrow(EmotionService::emotionNotFound);
    requireActive(e);
    return e;
  }

  private static void requireActiveOwn(UUID practitioner, Emotion e) {
    if (!e.isCustomOf(practitioner)) {
      throw emotionNotFound();
    }
    requireActive(e);
  }

  private static void requireActive(Emotion e) {
    if (!e.isActive()) {
      throw VocabularyException.of(
          HttpStatus.CONFLICT, "EMOTION_INACTIVE", "Cette émotion a été fusionnée.");
    }
  }

  private static VocabularyException emotionNotFound() {
    return VocabularyException.of(
        HttpStatus.NOT_FOUND, "EMOTION_NOT_FOUND", "Émotion introuvable.");
  }

  private static String validateLabel(String labelFr) {
    if (labelFr == null || TextNormalizer.normalize(labelFr).isEmpty()) {
      throw VocabularyException.invalid("labelFr", "Le libellé est obligatoire.");
    }
    return labelFr.trim();
  }

  private static VocabularyException conflict() {
    return VocabularyException.of(
        HttpStatus.CONFLICT, "EMOTION_LABEL_EXISTS", "Une émotion porte déjà ce libellé.");
  }

  private static byte[] randomBytes() {
    long v = UUID.randomUUID().getMostSignificantBits();
    return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
  }

  private static MergeView view(EmotionMerge m) {
    return new MergeView(
        m.getId(),
        m.getSourceCode(),
        m.getTargetCode(),
        m.getAffectedCount(),
        m.getMergedAt(),
        m.getUndoUntil(),
        m.getUndoneAt());
  }
}
