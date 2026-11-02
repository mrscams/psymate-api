package com.psymate.vocabulary.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domaine de l'entité Tag, sans Spring ni base. */
class TagTest {

  private static final OffsetDateTime T0 =
      OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 123_456_789, ZoneOffset.UTC);

  private static Tag tag(UUID id) {
    return new Tag(id, UUID.randomUUID(), UUID.randomUUID(), "Boulot", "boulot", T0);
  }

  @Test
  void aNewTagIsActiveNewAndUnused() {
    UUID id = UUID.randomUUID();
    Tag t = tag(id);

    assertThat(t.getId()).isEqualTo(id);
    assertThat(t.getLabel()).isEqualTo("Boulot");
    assertThat(t.getNormalizedLabel()).isEqualTo("boulot");
    assertThat(t.getUsageCount()).isZero();
    assertThat(t.getMergedInto()).isNull();
    assertThat(t.isArchived()).isFalse();
    assertThat(t.isNew()).isTrue();
    assertThat(t.getCreatedAt()).isEqualTo(T0.withNano(123_456_000));
  }

  @Test
  void renameReplacesBothLabels() {
    Tag t = tag(UUID.randomUUID());

    t.rename("Travail", "travail");

    assertThat(t.getLabel()).isEqualTo("Travail");
    assertThat(t.getNormalizedLabel()).isEqualTo("travail");
  }

  @Test
  void archiveIntoRecordsTheTargetAndTheDate() {
    Tag t = tag(UUID.randomUUID());
    UUID target = UUID.randomUUID();

    t.archiveInto(target, T0.plusHours(1));

    assertThat(t.isArchived()).isTrue();
    assertThat(t.getMergedInto()).isEqualTo(target);
    assertThat(t.getArchivedAt()).isEqualTo(T0.plusHours(1).withNano(123_456_000));
    assertThatThrownBy(() -> t.archiveInto(null, T0)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void equalityIsOnTheIdOnly() {
    UUID id = UUID.randomUUID();
    Tag a = tag(id);
    Tag same = tag(id);
    same.rename("Autre", "autre");

    assertThat(a).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(tag(UUID.randomUUID()));
    assertThat(a).isNotEqualTo(null).isNotEqualTo(id);
  }

  @Test
  void markNotNewFlipsIsNew() {
    Tag t = tag(UUID.randomUUID());
    t.markNotNew();
    assertThat(t.isNew()).isFalse();
  }
}
