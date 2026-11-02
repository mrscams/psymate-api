package com.psymate.overview.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Données structurées du résumé « En bref » (écran 2, vue simple). Calculées par la projection et
 * stockées dans {@code patient_overview.payload} sous la clé {@code narrativeFacts} : elles ne
 * contiennent aucune phrase, car le texte dépend de la langue de la requête et se compose à la
 * lecture ({@code service.NarrativeComposer}). Une donnée absente (null ou liste vide) supprime la
 * phrase qui en dépend.
 *
 * @param followedSince début du suivi (même valeur que {@code facts.followedSince})
 * @param sessionCount nombre de séances non archivées
 * @param rhythmDays intervalle médian, en jours, entre les séances non archivées des 12 derniers
 *     mois ; nul s'il y en a moins de 3
 * @param trend ressentis des 30 derniers jours comparés aux 30 précédents
 * @param dominant ressenti le plus fréquent des 30 derniers jours, nul s'il n'y en a aucun
 * @param situationTitles titres des situations en cours, dans l'ordre de la projection (nombre de
 *     liens décroissant)
 * @param uncommentedEvent événement récent sans lien ni ressenti qui le commente, le plus récent
 * @param medications traitements en cours, le plus récent en tête
 * @param flags drapeaux actifs
 */
public record NarrativeFacts(
    LocalDate followedSince,
    int sessionCount,
    Integer rhythmDays,
    Trend trend,
    Dominant dominant,
    List<String> situationTitles,
    UncommentedEvent uncommentedEvent,
    List<MedicationFact> medications,
    List<FlagFact> flags) {

  /** Sens de la tendance de l'intensité moyenne. */
  public enum Direction {
    UP,
    DOWN,
    STABLE
  }

  /**
   * Intensité moyenne des ressentis non archivés de J-29 à J ({@code recentMean}) et de J-59 à J-30
   * ({@code previousMean}). Moyennes nulles quand la fenêtre est vide ; {@code direction} nulle
   * quand l'une des deux l'est.
   */
  public record Trend(
      Direction direction,
      Double recentMean,
      Double previousMean,
      int recentCount,
      int previousCount) {

    /** Écart minimal, strict, entre les deux moyennes pour parler de hausse ou de baisse. */
    static final double THRESHOLD = 1.0;

    private static final double EPSILON = 1e-9;

    /**
     * Construit la tendance à partir des moyennes exactes : plus de 1 point d'écart (strict) monte
     * ou baisse, sinon reste stable. Les moyennes stockées sont arrondies au dixième.
     */
    public static Trend of(
        Double recentMean, Double previousMean, int recentCount, int previousCount) {
      Direction direction = null;
      if (recentMean != null && previousMean != null) {
        if (recentMean > previousMean + THRESHOLD + EPSILON) {
          direction = Direction.UP;
        } else if (recentMean < previousMean - THRESHOLD - EPSILON) {
          direction = Direction.DOWN;
        } else {
          direction = Direction.STABLE;
        }
      }
      return new Trend(
          direction, round(recentMean), round(previousMean), recentCount, previousCount);
    }

    private static Double round(Double value) {
      return value == null ? null : Math.round(value * 10.0) / 10.0;
    }
  }

  /** Ressenti le plus fréquent ; {@code labelEn} est nul quand l'émotion n'a pas de libellé EN. */
  public record Dominant(String code, String labelFr, String labelEn, int count) {}

  /** Événement récent non commenté ; {@code title} peut être nul. */
  public record UncommentedEvent(String title, LocalDate date) {}

  /** Traitement en cours ; {@code since} est le jour de début de prise. */
  public record MedicationFact(String molecule, String dosage, LocalDate since) {}

  /** Drapeau actif posé à la main ({@code watch}, {@code risk} ou {@code shared_care}). */
  public record FlagFact(String kind, String reason, LocalDate since) {}
}
