package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domaine des entités Emotion et EmotionMerge, sans Spring ni base. */
class EmotionTest {

  private static final OffsetDateTime T0 =
      OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 123_456_789, ZoneOffset.UTC);

  private static Emotion custom(String code) {
    return Emotion.custom(
        code, UUID.randomUUID(), UUID.randomUUID(), "sadness", "Lassitude", "lassitude", T0);
  }

  @Test
  void aNewCustomEmotionIsActiveNewAndOwnedByItsPractitioner() {
    UUID practitioner = UUID.randomUUID();
    Emotion e =
        Emotion.custom("c_1a2b3c4d", UUID.randomUUID(), practitioner, "fear", "Peur", "peur", T0);

    assertThat(e.getId()).isEqualTo("c_1a2b3c4d");
    assertThat(e.getScope()).isEqualTo("custom");
    assertThat(e.isActive()).isTrue();
    assertThat(e.isNew()).isTrue();
    assertThat(e.getIntensityLevel()).isNull();
    assertThat(e.getLabelEn()).isNull();
    assertThat(e.getCreatedBy()).isEqualTo(practitioner);
    assertThat(e.getCreatedAt()).isEqualTo(T0.withNano(123_456_000));
    assertThat(e.isCustomOf(practitioner)).isTrue();
    assertThat(e.isCustomOf(UUID.randomUUID())).isFalse();
    assertThat(e.isCustomOf(null)).isFalse();
  }

  @Test
  void aGlobalEmotionIsNeverOwnedByAPractitioner() {
    Emotion global =
        new Emotion(
            "anxiety",
            Emotion.SCOPE_GLOBAL,
            null,
            null,
            "fear",
            null,
            null,
            null,
            "Anxiété",
            "Anxiety",
            "anxiete",
            null,
            T0);

    assertThat(global.isCustomOf(UUID.randomUUID())).isFalse();
    assertThat(global.getLabelEn()).isEqualTo("Anxiety");
  }

  @Test
  void renameReplacesBothLabelsAndDeactivateActivateToggle() {
    Emotion e = custom("c_00000001");

    e.rename("Fatigue", "fatigue");
    assertThat(e.getLabelFr()).isEqualTo("Fatigue");
    assertThat(e.getLabelNormalized()).isEqualTo("fatigue");

    e.deactivate();
    assertThat(e.isActive()).isFalse();
    e.activate();
    assertThat(e.isActive()).isTrue();
  }

  @Test
  void equalityIsOnTheCodeOnly() {
    Emotion a = custom("c_00000001");
    Emotion same = custom("c_00000001");
    same.rename("Autre", "autre");
    Emotion other = custom("c_00000002");

    assertThat(a).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    assertThat(a).isNotEqualTo(null).isNotEqualTo("c_00000001");
  }

  @Test
  void markNotNewFlipsIsNew() {
    Emotion e = custom("c_00000001");
    e.markNotNew();
    assertThat(e.isNew()).isFalse();
  }

  @Test
  void entitiesHaveAProtectedNoArgConstructorAndNoPublicSetter() throws Exception {
    for (Class<?> type : new Class<?>[] {Emotion.class, EmotionMerge.class, Tag.class}) {
      assertThat(Modifier.isProtected(type.getDeclaredConstructor().getModifiers()))
          .as(type.getSimpleName())
          .isTrue();
      assertThat(Arrays.stream(type.getDeclaredMethods()))
          .as(type.getSimpleName())
          .noneMatch(m -> m.getName().startsWith("set") && Modifier.isPublic(m.getModifiers()));
    }
  }

  // ---- EmotionMerge

  @Test
  void aMergeOpensForThirtyDaysAtMicrosecondPrecision() {
    EmotionMerge m =
        EmotionMerge.open(UUID.randomUUID(), UUID.randomUUID(), "c_00000001", "sadness", 3, T0, 30);

    assertThat(m.getMergedAt()).isEqualTo(T0.withNano(123_456_000));
    assertThat(m.getUndoUntil()).isEqualTo(m.getMergedAt().plusDays(30));
    assertThat(m.getAffectedCount()).isEqualTo(3);
    assertThat(m.isUndone()).isFalse();
    assertThat(m.isNew()).isTrue();
  }

  @Test
  void aMergeIsUndoableUntilTheDeadlineThenNever() {
    EmotionMerge m =
        EmotionMerge.open(UUID.randomUUID(), UUID.randomUUID(), "c_00000001", "joy", 0, T0, 30);

    assertThat(m.isUndoableAt(T0.plusDays(29))).isTrue();
    assertThat(m.isUndoableAt(m.getUndoUntil())).as("la limite exacte est dépassée").isFalse();
    assertThat(m.isUndoableAt(T0.plusDays(31))).isFalse();

    m.markUndone(T0.plusDays(1));
    assertThat(m.isUndone()).isTrue();
    assertThat(m.getUndoneAt()).isEqualTo(T0.plusDays(1).withNano(123_456_000));
    assertThat(m.isUndoableAt(T0.plusDays(2))).isFalse();
  }

  @Test
  void markUndoneRefusesAnExpiredOrAlreadyUndoneMerge() {
    EmotionMerge expired =
        EmotionMerge.open(UUID.randomUUID(), UUID.randomUUID(), "c_00000001", "joy", 0, T0, 30);
    assertThatThrownBy(() -> expired.markUndone(T0.plusDays(31)))
        .isInstanceOf(IllegalStateException.class);

    EmotionMerge once =
        EmotionMerge.open(UUID.randomUUID(), UUID.randomUUID(), "c_00000001", "joy", 0, T0, 30);
    once.markUndone(T0.plusDays(1));
    assertThatThrownBy(() -> once.markUndone(T0.plusDays(2)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void mergeEqualityIsOnTheIdOnly() {
    UUID id = UUID.randomUUID();
    EmotionMerge a = EmotionMerge.open(id, UUID.randomUUID(), "c_1", "joy", 1, T0, 30);
    EmotionMerge b = EmotionMerge.open(id, UUID.randomUUID(), "c_2", "fear", 9, T0, 30);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a)
        .isNotEqualTo(
            EmotionMerge.open(UUID.randomUUID(), UUID.randomUUID(), "c_1", "joy", 1, T0, 30));
  }
}
