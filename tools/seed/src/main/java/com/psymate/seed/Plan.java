package com.psymate.seed;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Plan de seed : ce que le générateur ({@link Scenario}) produit et ce que le {@link Runner} (HTTP)
 * ou le test de performance (MockMvc) exécutent. Un plan ne contient aucun identifiant : les
 * entrées sont désignées par des clés symboliques ({@code ref}) résolues à l'exécution.
 */
public record Plan(List<PatientPlan> patients) {

  /** Praticien du seed auquel le patient est rattaché. */
  public enum Practitioner {
    CLAIRE,
    KARIM
  }

  /**
   * Un patient et son historique.
   *
   * @param displayName préfixé « SEED · » (ou « DEMO · ») : sert à l'idempotence
   * @param birthDate AAAA-MM-JJ, toujours renseignée (la phase de vie « enfance » en dépend)
   * @param tags libellés de tags du patient, créés avant lui
   * @param steps étapes dans l'ordre d'exécution
   */
  public record PatientPlan(
      String displayName,
      String firstName,
      String lastName,
      String birthDate,
      String pronoun,
      List<String> tags,
      Practitioner practitioner,
      List<Step> steps) {}

  /** Une étape du dossier. */
  public sealed interface Step {}

  /** Démarre une séance : POST entries type session, {@code occurred.from} = début réel. */
  public record StartSession(String ref, Instant occurredFrom, String modality) implements Step {}

  /** Clôture la séance : fin = début + durée. */
  public record CloseSession(String sessionRef, int durationMin, String summary) implements Step {}

  /**
   * Crée une entrée. {@code body} est le corps JSON exact de l'API, sans {@code sessionId} ni
   * {@code links} (résolus à l'exécution à partir de {@code sessionRef} et {@code links}).
   */
  public record CreateEntry(
      String ref, String sessionRef, Map<String, Object> body, List<LinkPlan> links)
      implements Step {}

  /** Corrige une entrée (PATCH) : fermeture de situation, fin de médicament. */
  public record PatchEntry(String ref, Map<String, Object> body) implements Step {}

  /**
   * Lien posé avec une nouvelle entrée.
   *
   * @param direction {@code outgoing} (la nouvelle entrée est la source) ou {@code incoming}
   */
  public record LinkPlan(String targetRef, String linkType, String direction) {}
}
