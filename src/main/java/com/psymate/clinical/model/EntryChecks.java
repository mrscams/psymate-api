package com.psymate.clinical.model;

/** Groupes de validation Jakarta : un par type d'entrée (champs minimaux de la tâche M1-03). */
public final class EntryChecks {

  private EntryChecks() {}

  /** note : body. */
  public interface NoteChecks {}

  /** event : title. */
  public interface EventChecks {}

  /** situation : title + occurred_from. */
  public interface SituationChecks {}

  /** feeling : emotionCode + intensity. */
  public interface FeelingChecks {}

  /** session : modality. */
  public interface SessionChecks {}

  /** medication : molecule. */
  public interface MedicationChecks {}

  /** Groupe du type donné. */
  public static Class<?> groupOf(EntryType type) {
    return switch (type) {
      case NOTE -> NoteChecks.class;
      case EVENT -> EventChecks.class;
      case SITUATION -> SituationChecks.class;
      case FEELING -> FeelingChecks.class;
      case SESSION -> SessionChecks.class;
      case MEDICATION -> MedicationChecks.class;
    };
  }
}
