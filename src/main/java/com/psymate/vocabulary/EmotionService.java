package com.psymate.vocabulary;

import com.psymate.vocabulary.VocabularyRepository.MergeRow;
import com.psymate.vocabulary.api.EmotionAffect;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private final VocabularyRepository repo;
  private final CurrentPractitioner current;

  EmotionService(VocabularyRepository repo, CurrentPractitioner current) {
    this.repo = repo;
    this.current = current;
  }

  @Override
  public List<EmotionView> activeEmotions() {
    return repo.activeEmotions(current.id());
  }

  @Override
  public Optional<EmotionView> findActiveByLabel(String label) {
    return repo.activeEmotionByNormalized(current.id(), TextNormalizer.normalize(label));
  }

  @Override
  public Optional<EmotionAffect> affectOf(String code) {
    return repo.emotionAffect(current.id(), code);
  }

  @Transactional
  EmotionView create(String labelFr, String family) {
    String label = validateLabel(labelFr);
    if (family == null || family.isBlank()) {
      throw VocabularyException.invalid("family", "La famille est obligatoire.");
    }
    if (!FAMILIES.contains(family)) {
      throw VocabularyException.invalid("family", "Famille inconnue : " + family + ".");
    }
    UUID practitioner = current.id();
    String norm = TextNormalizer.normalize(label);
    if (repo.emotionLabelTaken(practitioner, norm, null)) {
      throw conflict();
    }
    String code = "c_" + HexFormat.of().formatHex(randomBytes());
    try {
      repo.insertCustomEmotion(code, current.tenantId(), practitioner, family, label, norm);
    } catch (DuplicateKeyException e) {
      throw conflict();
    }
    return repo.ownCustomEmotion(practitioner, code).orElseThrow();
  }

  @Transactional
  EmotionView rename(String code, String labelFr) {
    UUID practitioner = current.id();
    EmotionView e = activeOwn(practitioner, code);
    String label = validateLabel(labelFr);
    String norm = TextNormalizer.normalize(label);
    if (repo.emotionLabelTaken(practitioner, norm, e.code())) {
      throw conflict();
    }
    try {
      repo.renameEmotion(code, label, norm);
    } catch (DuplicateKeyException ex) {
      throw conflict();
    }
    return repo.ownCustomEmotion(practitioner, code).orElseThrow();
  }

  /** Fusionne une custom active dans une globale active ou une custom active du praticien. */
  @Transactional
  MergeView merge(String sourceCode, String targetCode) {
    UUID practitioner = current.id();
    repo.lockEmotion(sourceCode);
    EmotionView source = activeOwn(practitioner, sourceCode);
    if (targetCode == null || targetCode.isBlank()) {
      throw VocabularyException.invalid("targetCode", "La cible est obligatoire.");
    }
    if (targetCode.equals(source.code())) {
      throw VocabularyException.invalid("targetCode", "La cible doit différer de la source.");
    }
    repo.usableEmotion(practitioner, targetCode)
        .orElseThrow(
            () ->
                VocabularyException.of(
                    HttpStatus.NOT_FOUND, "EMOTION_NOT_FOUND", "Émotion cible introuvable."));
    int affected = repo.rewriteFeelings(sourceCode, targetCode);
    UUID id = UUID.randomUUID();
    repo.insertMerge(id, practitioner, sourceCode, targetCode, affected, UNDO_DAYS);
    repo.setEmotionActive(sourceCode, false);
    return view(repo.merge(id, practitioner).orElseThrow());
  }

  /** Annule une fusion dans les 30 jours ; après undo_until ou déjà annulée : 409. */
  @Transactional
  MergeView undo(UUID mergeId) {
    UUID practitioner = current.id();
    MergeRow m =
        repo.merge(mergeId, practitioner)
            .orElseThrow(
                () ->
                    VocabularyException.of(
                        HttpStatus.NOT_FOUND, "MERGE_NOT_FOUND", "Fusion introuvable."));
    if (m.undoneAt() != null) {
      throw VocabularyException.of(
          HttpStatus.CONFLICT, "MERGE_ALREADY_UNDONE", "Cette fusion a déjà été annulée.");
    }
    // Mise à jour conditionnelle : échoue si le délai est dépassé ou si un undo concurrent a gagné.
    if (!repo.markMergeUndone(mergeId)) {
      throw VocabularyException.of(
          HttpStatus.CONFLICT,
          "MERGE_UNDO_EXPIRED",
          "Le délai d'annulation de 30 jours est dépassé.");
    }
    repo.restoreFeelings(m.sourceCode(), m.targetCode());
    repo.setEmotionActive(m.sourceCode(), true);
    return view(repo.merge(mergeId, practitioner).orElseThrow());
  }

  private EmotionView activeOwn(UUID practitioner, String code) {
    EmotionView e =
        repo.ownCustomEmotion(practitioner, code)
            .orElseThrow(
                () ->
                    VocabularyException.of(
                        HttpStatus.NOT_FOUND, "EMOTION_NOT_FOUND", "Émotion introuvable."));
    if (!e.active()) {
      throw VocabularyException.of(
          HttpStatus.CONFLICT, "EMOTION_INACTIVE", "Cette émotion a été fusionnée.");
    }
    return e;
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

  private static MergeView view(MergeRow m) {
    return new MergeView(
        m.id(),
        m.sourceCode(),
        m.targetCode(),
        m.affectedCount(),
        m.mergedAt(),
        m.undoUntil(),
        m.undoneAt());
  }
}
