package com.psymate.patients;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.patients.persistence.Patient;
import com.psymate.patients.persistence.PatientFlag;
import com.psymate.patients.persistence.PatientTag;
import com.psymate.patients.persistence.PatientTagId;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domaine de l'entité Patient, sans Spring ni base. */
class PatientTest {

  private static final OffsetDateTime T0 =
      OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC);

  private static Patient patient(UUID id) {
    return new Patient(
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Marie D.",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        T0);
  }

  @Test
  void newPatientStartsAtVersionOneWithEmptyJsonAndNoArchive() {
    Patient p = patient(UUID.randomUUID());
    assertThat(p.getCurrentVersion()).isEqualTo(1);
    assertThat(p.getContext()).isEmpty();
    assertThat(p.getFollowUp()).isEmpty();
    assertThat(p.getArchivedAt()).isNull();
    assertThat(p.isArchived()).isFalse();
    assertThat(p.getCreatedAt()).isEqualTo(T0);
    assertThat(p.getUpdatedAt()).isEqualTo(T0);
  }

  @Test
  void applyEditIncrementsVersionAndUpdatedAtAndReplacesTheEditableColumns() {
    Patient p = patient(UUID.randomUUID());
    OffsetDateTime later = T0.plusMinutes(5);

    int version =
        p.applyEdit(
            "Marie Dupont",
            "Marie",
            "Dupont",
            null,
            "elle",
            "0102",
            "m@example.test",
            Map.of("activity", "infirmière"),
            Map.of("rhythm", "weekly"),
            later);

    assertThat(version).isEqualTo(2);
    assertThat(p.getCurrentVersion()).isEqualTo(2);
    assertThat(p.getUpdatedAt()).isEqualTo(later);
    assertThat(p.getCreatedAt()).isEqualTo(T0);
    assertThat(p.getDisplayName()).isEqualTo("Marie Dupont");
    assertThat(p.getFirstName()).isEqualTo("Marie");
    assertThat(p.getPronoun()).isEqualTo("elle");
    assertThat(p.getContext()).containsEntry("activity", "infirmière");
    assertThat(p.getFollowUp()).containsEntry("rhythm", "weekly");
    assertThat(p.applyEdit("Marie Dupont", null, null, null, null, null, null, null, null, later))
        .isEqualTo(3);
    assertThat(p.getContext()).isEmpty();
  }

  @Test
  void applyEditKeepsItsOwnCopyOfTheJsonMaps() {
    Patient p = patient(UUID.randomUUID());
    Map<String, Object> source = new java.util.HashMap<>(Map.of("a", 1));
    p.applyEdit("X", null, null, null, null, null, null, source, null, T0);
    source.put("b", 2);
    assertThat(p.getContext()).containsOnlyKeys("a");
  }

  @Test
  void archiveThenRestore() {
    Patient p = patient(UUID.randomUUID());
    OffsetDateTime archivedAt = T0.plusHours(1);
    p.archive(archivedAt);
    assertThat(p.isArchived()).isTrue();
    assertThat(p.getArchivedAt()).isEqualTo(archivedAt);
    assertThat(p.getUpdatedAt()).isEqualTo(archivedAt);

    OffsetDateTime restoredAt = T0.plusHours(2);
    p.restore(restoredAt);
    assertThat(p.isArchived()).isFalse();
    assertThat(p.getArchivedAt()).isNull();
    assertThat(p.getUpdatedAt()).isEqualTo(restoredAt);
    assertThat(p.getCurrentVersion()).as("archiver ne crée pas de version").isEqualTo(1);
  }

  @Test
  void equalsAndHashCodeUseTheIdOnly() {
    UUID id = UUID.randomUUID();
    Patient a = patient(id);
    Patient b = patient(id);
    b.applyEdit("Autre nom", "x", "y", null, null, null, null, null, null, T0.plusDays(1));

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(patient(UUID.randomUUID()));
    assertThat(a).isNotEqualTo(null);
  }

  @Test
  void isNewUntilPersistedOrLoaded() throws Exception {
    Patient p = patient(UUID.randomUUID());
    assertThat(p.isNew()).isTrue();
    assertThat(p.getId()).isNotNull();

    // @PostPersist / @PostLoad : appelé par Hibernate, package-private.
    Method callback = Patient.class.getDeclaredMethod("markNotNew");
    callback.setAccessible(true);
    callback.invoke(p);
    assertThat(p.isNew()).isFalse();
  }

  @Test
  void flagClearAndTagIdEquality() {
    PatientFlag flag =
        new PatientFlag(UUID.randomUUID(), UUID.randomUUID(), "risk", null, UUID.randomUUID(), T0);
    UUID by = UUID.randomUUID();
    assertThat(flag.getClearedAt()).isNull();
    flag.clear(by, T0.plusDays(1));
    assertThat(flag.getClearedAt()).isEqualTo(T0.plusDays(1));
    assertThat(flag.getClearedBy()).isEqualTo(by);

    UUID patient = UUID.randomUUID();
    UUID tag = UUID.randomUUID();
    PatientTagId one = new PatientTagId(patient, tag);
    assertThat(one).isEqualTo(new PatientTagId(patient, tag)).hasSameHashCodeAs(one);
    assertThat(one).isNotEqualTo(new PatientTagId(patient, UUID.randomUUID()));
    assertThat(new PatientTag(one, T0))
        .isEqualTo(new PatientTag(new PatientTagId(patient, tag), T0.plusDays(3)));
  }
}
