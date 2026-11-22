package com.psymate.agenda.controller;

import com.psymate.agenda.dto.AttachPatientRequest;
import com.psymate.agenda.dto.BookingTokenRequest;
import com.psymate.agenda.dto.CreateBookingRequest;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.model.BookingException;
import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Erreurs RFC 9457 (application/problem+json) des contrôleurs de l'agenda, sur le modèle de
 * clinical : errors[] = [{field, code, message}] pour les 422, statut du domaine pour les erreurs
 * métier ({@link AppointmentException}). Limité par package aux contrôleurs de {@code
 * com.psymate.agenda.controller} : M11-02 n'en livre aucun, M11-05 et M11-06 y posent les leurs
 * sans toucher à cette classe. PATIENT_FORBIDDEN et PATIENT_NOT_FOUND, levés par la garde de
 * propriété des patients, restent rendus par le handler commun de shared.
 *
 * <p>Codes racine (champ {@code code}) : VALIDATION_FAILED (422, errors[] par champ),
 * MALFORMED_REQUEST (400 : corps illisible sans champ identifiable, ou {id} de chemin qui n'est pas
 * un UUID), et les codes de l'agenda (I3).
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = AgendaExceptionHandler.class)
class AgendaExceptionHandler {

  /** Code d'une contrainte qui le porte dans son message ({@code GuestRequest.phone}). */
  static final String INVALID_FORMAT = "INVALID_FORMAT";

  /** Contexte des messages de {@code POST /api/appointments/{id}/attach-patient}. */
  static final String ATTACH_SCOPE = "attach";

  /**
   * Contexte des messages des routes publiques de prise de rendez-vous (M14-05, M14-06) : clés
   * {@code agenda.error.booking.CODE} et {@code agenda.error.CODE.booking.champ}.
   */
  static final String BOOKING_SCOPE = "booking";

  private final AgendaMessages messages;

  AgendaExceptionHandler(AgendaMessages messages) {
    this.messages = messages;
  }

  private ProblemDetail validation(List<FieldProblem> problems, Locale locale) {
    return validation(problems, locale, null);
  }

  /**
   * {@code scope} (ou null) : contexte d'une requête dont les messages diffèrent de la création,
   * clé {@code agenda.error.CODE.scope.champ} d'abord (voir {@link AgendaMessages}).
   */
  private ProblemDetail validation(List<FieldProblem> problems, Locale locale, String scope) {
    Map<String, FieldProblem> byField = new LinkedHashMap<>();
    problems.forEach(p -> byField.putIfAbsent(p.field(), p));
    List<FieldProblem> errors = new ArrayList<>();
    byField
        .values()
        .forEach(
            p ->
                errors.add(
                    new FieldProblem(p.field(), p.code(), messages.message(p, locale, scope))));
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    pd.setTitle(messages.get("agenda.error.validation.title", locale));
    pd.setDetail(messages.get("agenda.error.validation.detail", locale));
    pd.setProperty("code", "VALIDATION_FAILED");
    pd.setProperty("errors", errors);
    return pd;
  }

  private ProblemDetail malformed(Locale locale) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle(messages.get("agenda.error.malformed.title", locale));
    pd.setDetail(messages.get("agenda.error.malformed.detail", locale));
    pd.setProperty("code", "MALFORMED_REQUEST");
    return pd;
  }

  /** Code stable à partir du nom de la contrainte Jakarta (NotBlank, Min, Pattern...). */
  static String codeOf(String constraint) {
    return switch (constraint) {
      case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
      case "Min", "Max", "Size", "Positive", "PositiveOrZero", "Range" -> "OUT_OF_RANGE";
      case "Pattern", "AssertTrue" -> "INVALID_VALUE";
      default -> "INVALID";
    };
  }

  /**
   * Erreur métier : statut et code de l'exception, message localisé ; pour un 422, errors[0] porte
   * le champ fautif et le même code.
   */
  @ExceptionHandler(AppointmentException.class)
  ProblemDetail domain(AppointmentException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    if (ex.field() != null && !ex.code().equals(ex.fieldCode())) {
      // Validation métier (G Q60) : même forme que les erreurs Jakarta.
      return validation(List.of(new FieldProblem(ex.field(), ex.fieldCode())), locale);
    }
    String detail = messages.domain(ex.code(), ex.detail(), locale);
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), detail);
    pd.setTitle(ex.title());
    pd.setProperty("code", ex.code());
    if (ex.field() != null) {
      pd.setProperty("errors", List.of(new FieldProblem(ex.field(), ex.code(), detail)));
    }
    return pd;
  }

  /**
   * Erreur des routes publiques de prise de rendez-vous (M14-05) : comme {@link #domain}, messages
   * {@code agenda.error.booking.CODE} et {@code agenda.error.CODE.booking.champ}, et {@code
   * instance} fixe ({@link BookingException#INSTANCE}) : le corps ne répète jamais le slug, et le
   * 404 est le même quelle que soit sa cause (G Q78).
   */
  @ExceptionHandler(BookingException.class)
  ProblemDetail booking(BookingException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    ProblemDetail pd;
    if (ex.field() != null && !ex.code().equals(ex.fieldCode())) {
      pd = validation(List.of(new FieldProblem(ex.field(), ex.fieldCode())), locale, BOOKING_SCOPE);
    } else {
      String detail = messages.domain(BOOKING_SCOPE + "." + ex.code(), ex.detail(), locale);
      pd = ProblemDetail.forStatusAndDetail(ex.status(), detail);
      pd.setTitle(ex.title());
      pd.setProperty("code", ex.code());
      if (ex.field() != null) {
        pd.setProperty("errors", List.of(new FieldProblem(ex.field(), ex.code(), detail)));
      }
    }
    pd.setInstance(BookingException.INSTANCE);
    return pd;
  }

  /** Contraintes Jakarta des corps de requête (@Valid). */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail bean(MethodArgumentNotValidException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    List<FieldProblem> problems = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      // Une contrainte peut porter son code dans son message (téléphone d'un invité : G Q60).
      String code =
          INVALID_FORMAT.equals(fe.getDefaultMessage())
              ? INVALID_FORMAT
              : codeOf(String.valueOf(fe.getCode()));
      problems.add(new FieldProblem(fe.getField(), code));
    }
    Object target = ex.getBindingResult().getTarget();
    if (target instanceof CreateBookingRequest || target instanceof BookingTokenRequest) {
      // Routes publiques de prise de rendez-vous (M14-06) : messages et instance fixe (G Q78).
      return publicBooking(validation(problems, locale, BOOKING_SCOPE));
    }
    // Rattachement d'un invité (G Q60) : patientId seul, sans l'alternative « ou un invité ».
    String scope = target instanceof AttachPatientRequest ? ATTACH_SCOPE : null;
    return validation(problems, locale, scope);
  }

  /** {@code instance} fixe des routes publiques de prise de rendez-vous : jamais le slug. */
  private static ProblemDetail publicBooking(ProblemDetail pd) {
    pd.setInstance(BookingException.INSTANCE);
    return pd;
  }

  private static boolean isPublicBooking(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith(BookingException.INSTANCE.getPath() + "/");
  }

  /** Contraintes Jakarta des paramètres de requête (@Validated). */
  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail constraints(ConstraintViolationException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    List<FieldProblem> problems = new ArrayList<>();
    for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
      String path = v.getPropertyPath().toString();
      String field = path.substring(path.lastIndexOf('.') + 1);
      String constraint =
          v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
      problems.add(new FieldProblem(field, codeOf(constraint)));
    }
    return validation(problems, locale);
  }

  /**
   * Paramètre de requête illisible (date non ISO, UUID, booléen) : 422 sur ce paramètre. Une
   * variable de chemin illisible reste un 400 : l'URL elle-même est mal formée.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail parameter(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
      return malformed(locale);
    }
    return validation(List.of(new FieldProblem(ex.getName(), "INVALID_FORMAT")), locale);
  }

  /** Corps illisible : 422 par champ si Jackson sait lequel (date, UUID, nombre), sinon 400. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    ProblemDetail pd = null;
    for (Throwable t = ex.getCause(); t != null && pd == null; t = t.getCause()) {
      if (t instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
        pd =
            validation(
                List.of(new FieldProblem(path(mismatch), "INVALID_FORMAT")),
                locale,
                isPublicBooking(request) ? BOOKING_SCOPE : null);
      } else if (t.getCause() == t) {
        break;
      }
    }
    if (pd == null) {
      pd = malformed(locale);
    }
    return isPublicBooking(request) ? publicBooking(pd) : pd;
  }

  private static String path(JacksonException ex) {
    StringBuilder sb = new StringBuilder();
    for (JacksonException.Reference ref : ex.getPath()) {
      if (ref.getPropertyName() != null) {
        sb.append(sb.isEmpty() ? "" : ".").append(ref.getPropertyName());
      } else if (ref.getIndex() >= 0) {
        sb.append('[').append(ref.getIndex()).append(']');
      }
    }
    return sb.toString();
  }
}
