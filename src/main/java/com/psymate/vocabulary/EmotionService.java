package com.psymate.vocabulary;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.vocabulary.api.EmotionAffect;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionMergeReverted;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.EmotionsMerged;
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
class EmotionService implements EmotionCatalog {

  static final Set<String> FAMILIES =
      Set.of("joy", "trust", "fear", "surprise", "sadness", "disgust", "anger", "anticipation");
  static final int UNDO_DAYS = 30;

  @io.swagger.v3.oas.annotations.media.Schema(
      description =
          "Fusion d'une émotion personnalisée dans une autre, annulable pendant 30 jours.")
  record MergeView(
      @io.swagger.v3.oas.annotations.media.Schema(
              description = "Identifiant de la fusion, à fournir pour l'annuler.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @io.swagger.v3.oas.annotations.media.Schema(
              description = "Code de l'émotion personnalisée absorbée (désormais inactive).",
              example = "c_1a2b3c4d")
          String sourceCode,
      @io.swagger.v3.oas.annotations.media.Schema(
              description = "Code de l'émotion qui a absorbé la source.",
              example = "sadness")
          String targetCode,
      @io.swagger.v3.oas.annotations.media.Schema(
              description =
                  "Nombre de ressentis dont l'émotion est passée de la source à la cible.",
              example = "3")
          int affectedCount,
      @io.swagger.v3.oas.annotations.media.Schema(
              description = "Date-heure de la fusion, UTC ISO 8601.",
              example = "2026-03-14T09:30:00Z")
          OffsetDateTime mergedAt,
      @io.swagger.v3.oas.annotations.media.Schema(
              description =
                  "Date-heure limite d'annulation (30 jours après la fusion), UTC ISO 8601.",
              example = "2026-04-13T09:30:00Z")
          OffsetDateTime undoUntil,
      @io.swagger.v3.oas.annotations.media.Schema(
              description =
                  "Date-heure de l'annulation, UTC ISO 8601 ; null tant que la fusion n'a pas été"
                      + " annulée.",
              example = "2026-03-20T10:00:00Z")
          OffsetDateTime undoneAt) {}

  private final EmotionRepository emotions;
  private final EmotionMergeRepository merges;
  private final CurrentPractitioner current;
  private final TransactionTemplate tx;
  private final DomainEventPublisher events;

  EmotionService(
      EmotionRepository emotions,
      EmotionMergeRepository merges,
      CurrentPractitioner current,
      TransactionTemplate tx,
      DomainEventPublisher events) {
    this.emotions = emotions;
    this.merges = merges;
    this.current = current;
    this.tx = tx;
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EmotionView> activeEmotions() {
    return emotions.findActiveFor(current.id()).stream().map(VocabularyViews::emotion).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmotionView> findActiveByLabel(String label) {
    return emotions
        .findFirstActiveByNormalized(current.id(), TextNormalizer.normalize(label))
        .map(VocabularyViews::emotion);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmotionAffect> affectOf(String code) {
    return emotions.findUsable(current.id(), code).map(VocabularyViews::affect);
  }

  /**
   * Pas de {@code @Transactional} ici : l'unicité du libellé (ux_emotion_custom_label) est
   * contrôlée au flush / commit, donc le 409 d'une course est attrapé APRÈS le commit (même schéma
   * que PatientService.addFlag).
   */
  EmotionView create(String labelFr, String family) {
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
    UUID practitioner = current.id();
    String norm = TextNormalizer.normalize(label);
    if (emotions.labelTaken(practitioner, norm, "")) {
      throw conflict();
    }
    String code = "c_" + HexFormat.of().formatHex(randomBytes());
    Emotion created =
        emotions.save(
            Emotion.custom(
                code, current.tenantId(), practitioner, family, label, norm, Timestamps.now()));
    return VocabularyViews.emotion(created);
  }

  /** Même principe que {@link #create} : le renommage peut violer l'unicité au commit. */
  EmotionView rename(String code, String labelFr) {
    try {
      return tx.execute(status -> doRename(code, labelFr));
    } catch (DataIntegrityViolationException race) {
      throw conflict();
    }
  }

  private EmotionView doRename(String code, String labelFr) {
    UUID practitioner = current.id();
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
  MergeView merge(String sourceCode, String targetCode) {
    UUID practitioner = current.id();
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
    int affected = emotions.rewriteFeelings(sourceCode, targetCode);
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
        new EmotionsMerged(
            current.tenantId(), merge.getId(), practitioner, sourceCode, targetCode));
    return view(merge);
  }

  /** Annule une fusion dans les 30 jours ; après undo_until ou déjà annulée : 409. */
  @Transactional
  MergeView undo(UUID mergeId) {
    UUID practitioner = current.id();
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
    emotions.restoreFeelings(m.getSourceCode(), m.getTargetCode());
    emotions.findById(m.getSourceCode()).ifPresent(Emotion::activate);
    events.publish(
        new EmotionMergeReverted(
            current.tenantId(), m.getId(), practitioner, m.getSourceCode(), m.getTargetCode()));
    return view(m);
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
