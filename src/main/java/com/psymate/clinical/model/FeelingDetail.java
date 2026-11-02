package com.psymate.clinical.model;

import com.psymate.clinical.model.EntryChecks.FeelingChecks;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Détail d'un ressenti (A4.5). {@code valence} et {@code arousal} sont copiés de l'émotion à la
 * saisie par le service, jamais fournis par l'appelant.
 */
public record FeelingDetail(
    @NotBlank(groups = FeelingChecks.class) String emotionCode,
    String originalEmotionCode,
    @NotNull(groups = FeelingChecks.class)
        @Min(value = 0, groups = FeelingChecks.class)
        @Max(value = 10, groups = FeelingChecks.class)
        Integer intensity,
    Integer valence,
    Integer arousal,
    String contextNote,
    List<String> bodilySigns) {

  public FeelingDetail(String emotionCode, Integer intensity, String contextNote) {
    this(emotionCode, null, intensity, null, null, contextNote, null);
  }

  public FeelingDetail withAffect(Integer valence, Integer arousal) {
    return new FeelingDetail(
        emotionCode, originalEmotionCode, intensity, valence, arousal, contextNote, bodilySigns);
  }
}
