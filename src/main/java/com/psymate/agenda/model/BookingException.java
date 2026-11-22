package com.psymate.agenda.model;

import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.Problems;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * Erreur des routes publiques de prise de rendez-vous en ligne (G Q71, K5.1), rendue en RFC 9457
 * par {@code AgendaExceptionHandler} avec un message localisé ({@code agenda.error.booking.CODE}).
 * Le corps porte toujours l'{@code instance} fixe {@link #INSTANCE} au lieu du chemin de la requête
 * : il ne répète jamais le slug demandé, et le 404 est identique octet pour octet quelle que soit
 * sa cause (G Q78).
 */
public class BookingException extends ProblemException {

  /** Largeur maximale, en jours, to inclus, d'une fenêtre de créneaux publics (K5.1). */
  public static final int MAX_WINDOW_DAYS = 31;

  /** {@code instance} de toutes les erreurs de prise de rendez-vous (G Q78). */
  public static final URI INSTANCE = URI.create("/api/public/booking");

  private final String field;
  private final String fieldCode;

  private BookingException(int status, String code, String field, String fieldCode, String detail) {
    super(
        HttpStatusCode.valueOf(status),
        code,
        Problems.titleOf(HttpStatusCode.valueOf(status)),
        detail,
        field == null ? List.of() : List.of(new FieldProblem(field, fieldCode, detail)));
    this.field = field;
    this.fieldCode = fieldCode;
  }

  /** Champ de {@code errors[]} pour un 422, {@code null} sinon. */
  public String field() {
    return field;
  }

  /**
   * Code de l'erreur de champ : le code racine, sauf pour une erreur de validation
   * (VALIDATION_FAILED à la racine, INVALID_VALUE sur le champ).
   */
  public String fieldCode() {
    return fieldCode;
  }

  /**
   * Prise de rendez-vous non offerte (G Q78) : slug inconnu, fiche non publiée, praticien non
   * approuvé, fonction désactivée ou aucune plage. Une seule réponse pour toutes ces causes.
   */
  public static BookingException notAvailable() {
    return new BookingException(
        404,
        "BOOKING_NOT_AVAILABLE",
        null,
        null,
        "Prise de rendez-vous en ligne indisponible à cette adresse.");
  }

  /** Fenêtre de plus de {@link #MAX_WINDOW_DAYS} jours (to inclus) : 422, errors[0] sur to. */
  public static BookingException windowTooLarge() {
    return new BookingException(
        422,
        "WINDOW_TOO_LARGE",
        "to",
        "WINDOW_TOO_LARGE",
        "La fenêtre demandée dépasse " + MAX_WINDOW_DAYS + " jours.");
  }

  /** to avant from : 422 VALIDATION_FAILED, errors[0] = to INVALID_VALUE. */
  public static BookingException toBeforeFrom() {
    return new BookingException(
        422,
        "VALIDATION_FAILED",
        "to",
        "INVALID_VALUE",
        "La fin de la fenêtre (to) ne peut pas précéder son début (from).");
  }
}
