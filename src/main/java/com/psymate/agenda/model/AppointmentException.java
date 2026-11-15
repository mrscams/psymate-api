package com.psymate.agenda.model;

import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.Problems;
import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * Erreur métier de l'agenda, rendue en RFC 9457 par {@code AgendaExceptionHandler} : le statut et
 * le code viennent d'ici, le message localisé (clé {@code agenda.error.<code>}) du handler. Codes
 * (I3) : APPOINTMENT_NOT_FOUND et SERIES_NOT_FOUND (404, aussi pour le rendez-vous d'un autre
 * praticien, G Q58), APPOINTMENT_NOT_ACTIVE (409), PATIENT_ARCHIVED, TOO_MANY_OCCURRENCES et
 * WINDOW_TOO_LARGE (422, avec errors[] sur le champ fautif).
 */
public class AppointmentException extends ProblemException {

  /** Nombre maximal d'occurrences d'une série (G Q55). */
  public static final int MAX_OCCURRENCES = 26;

  /** Largeur maximale, en jours, d'une fenêtre {@code GET /api/agenda} (I3). */
  public static final int MAX_WINDOW_DAYS = 62;

  private final String field;

  private AppointmentException(int status, String code, String field, String detail) {
    super(
        HttpStatusCode.valueOf(status),
        code,
        Problems.titleOf(HttpStatusCode.valueOf(status)),
        detail,
        field == null ? List.of() : List.of(new FieldProblem(field, code, detail)));
    this.field = field;
  }

  /** Champ de {@code errors[]} pour un 422, {@code null} pour les autres statuts. */
  public String field() {
    return field;
  }

  public static AppointmentException notFound() {
    return new AppointmentException(404, "APPOINTMENT_NOT_FOUND", null, "Rendez-vous introuvable.");
  }

  public static AppointmentException seriesNotFound() {
    return new AppointmentException(404, "SERIES_NOT_FOUND", null, "Série introuvable.");
  }

  /** Le rendez-vous est annulé ou terminé : plus aucune modification (I3). */
  public static AppointmentException notActive() {
    return new AppointmentException(
        409, "APPOINTMENT_NOT_ACTIVE", null, "Ce rendez-vous est annulé ou terminé.");
  }

  public static AppointmentException patientArchived() {
    return new AppointmentException(
        422, "PATIENT_ARCHIVED", "patientId", "Le patient est archivé.");
  }

  public static AppointmentException tooManyOccurrences() {
    return new AppointmentException(
        422,
        "TOO_MANY_OCCURRENCES",
        "recurrence",
        "Une série compte au plus " + MAX_OCCURRENCES + " occurrences.");
  }

  public static AppointmentException windowTooLarge() {
    return new AppointmentException(
        422, "WINDOW_TOO_LARGE", "to", "La fenêtre dépasse " + MAX_WINDOW_DAYS + " jours.");
  }
}
