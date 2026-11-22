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

  /**
   * Création d'une demande (M14-06) : {@code startsAt} n'est pas le début d'un créneau offert, ou
   * la modalité n'est pas offerte. 409 : l'information est déjà publique (G Q78).
   */
  public static BookingException slotNotAvailable() {
    return new BookingException(
        409, "SLOT_NOT_AVAILABLE", null, null, "Ce créneau n'est pas proposé à la réservation.");
  }

  /** Jeton de vérification ou de gestion inconnu (M14-06, G Q80). */
  public static BookingException tokenInvalid() {
    return new BookingException(
        404, "BOOKING_TOKEN_INVALID", null, null, "Ce lien n'est pas valable.");
  }

  /** Jeton de vérification expiré, demande jamais vérifiée (G Q73). */
  public static BookingException tokenExpired() {
    return new BookingException(410, "BOOKING_TOKEN_EXPIRED", null, null, "Ce lien a expiré.");
  }

  /** Le créneau n'est plus offert au moment de la vérification (G Q77). */
  public static BookingException slotTaken() {
    return new BookingException(409, "SLOT_TAKEN", null, null, "Ce créneau vient d'être pris.");
  }

  /**
   * Un rendez-vous en ligne actif et à venir existe déjà chez ce praticien pour cette adresse (G
   * Q79) ; seul le détenteur de la boîte, qui suit le lien, le voit.
   */
  public static BookingException alreadyActive() {
    return new BookingException(
        409,
        "BOOKING_ALREADY_ACTIVE",
        null,
        null,
        "Vous avez déjà un rendez-vous à venir avec ce praticien.");
  }

  /** Annulation par le demandeur d'un rendez-vous commencé ou terminé (M14-06). */
  public static BookingException appointmentNotActive() {
    return new BookingException(
        409,
        "APPOINTMENT_NOT_ACTIVE",
        null,
        null,
        "Ce rendez-vous a commencé ou est terminé : il ne peut plus être annulé.");
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
