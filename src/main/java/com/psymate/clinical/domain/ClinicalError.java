package com.psymate.clinical.domain;

/**
 * Codes d'erreur métier du module clinical, avec le statut HTTP que M1-05 leur associera. Le nom de
 * la constante est le champ {@code code} de la réponse RFC 9457 ; pour les 422, errors[0].field
 * vaut sessionId (SESSION_*), occurred (INVALID_TIME) ou feeling.emotionCode (UNKNOWN_EMOTION).
 *
 * <ul>
 *   <li>ENTRY_NOT_FOUND (404) : entrée ou séance inexistante.
 *   <li>ENTRY_ARCHIVED (409) : correction ou clôture d'une entrée archivée.
 *   <li>UNAUTHENTICATED (401) : aucun praticien dans le contexte de sécurité.
 *   <li>SESSION_REQUIRED (422) : aucune séance courante pour rattacher l'entrée.
 *   <li>SESSION_INVALID (422) : sessionId qui n'est pas une séance du patient, ou entrée à clôturer
 *       qui n'est pas une séance.
 *   <li>SESSION_NOT_ALLOWED (422) : sessionId fourni pour une séance.
 *   <li>SESSION_ALREADY_OPEN (409) : une séance est déjà ouverte pour ce patient.
 *   <li>SESSION_ALREADY_CLOSED (409) : la séance est déjà clôturée.
 *   <li>UNKNOWN_EMOTION (422) : émotion absente du catalogue ou inactive.
 *   <li>INVALID_TIME (422) : bornes de temps inexploitables.
 *   <li>NO_CURRENT_SESSION (404) : le patient n'a aucune séance.
 * </ul>
 */
public enum ClinicalError {
  ENTRY_NOT_FOUND(404),
  ENTRY_ARCHIVED(409),
  UNAUTHENTICATED(401),
  SESSION_REQUIRED(422),
  SESSION_INVALID(422),
  SESSION_NOT_ALLOWED(422),
  SESSION_ALREADY_OPEN(409),
  SESSION_ALREADY_CLOSED(409),
  UNKNOWN_EMOTION(422),
  INVALID_TIME(422),
  NO_CURRENT_SESSION(404);

  private final int status;

  ClinicalError(int status) {
    this.status = status;
  }

  public int status() {
    return status;
  }
}
