package com.psymate.shared.problem;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Filet de sécurité RFC 9457 de toute l'API : transforme en application/problem+json, avec les
 * mêmes champs que les handlers de module ({@code type}, {@code title}, {@code status}, {@code
 * detail}, {@code instance}, {@code code}, {@code errors[]}), les erreurs que ces handlers ne
 * traitent pas (JSON illisible, route inconnue, méthode ou type de contenu non pris en charge,
 * paramètre invalide, erreur inattendue…).
 *
 * <p>Précédence la plus basse : les advices propres aux modules (limités à leurs contrôleurs) et
 * les {@code @ExceptionHandler} locaux passent toujours avant, leurs codes et leurs corps ne
 * changent pas. Le Content-Type est posé explicitement, ce qui évite la négociation de contenu (un
 * {@code Accept: text/html} ne doit pas transformer l'erreur en 406 ou en page HTML).
 *
 * <p>Jamais le message d'une exception inattendue dans la réponse : seul un détail générique sort,
 * la cause est dans le journal.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ProblemException.class)
  ResponseEntity<ProblemDetail> handle(ProblemException ex, HttpServletRequest request) {
    ProblemDetail pd = Problems.of(ex.status(), ex.code(), ex.title(), ex.detail(), ex.errors());
    if (ex.status().is5xxServerError()) {
      LOG.error("Erreur applicative {} sur {}", ex.code(), request.getRequestURI(), ex);
    }
    return response(pd, request, null);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handle(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return standard(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_JSON, request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ProblemDetail> handle(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    return withErrors(
        HttpStatus.BAD_REQUEST,
        ErrorCode.BAD_REQUEST,
        List.of(
            new FieldProblem(
                ex.getParameterName(),
                ErrorCode.INVALID_FIELD,
                ProblemMessages.detail("MISSING_PARAMETER", locale))),
        request);
  }

  @ExceptionHandler(TypeMismatchException.class)
  ResponseEntity<ProblemDetail> handle(TypeMismatchException ex, HttpServletRequest request) {
    String field =
        ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
    return withErrors(
        HttpStatus.BAD_REQUEST,
        ErrorCode.BAD_REQUEST,
        List.of(
            new FieldProblem(
                field,
                ErrorCode.INVALID_FIELD,
                ProblemMessages.invalidValue(ProblemMessages.localeOf(request)))),
        request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ProblemDetail> handle(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    List<FieldProblem> errors = new ArrayList<>();
    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      if (result instanceof ParameterErrors pe && !pe.getFieldErrors().isEmpty()) {
        for (FieldError fe : pe.getFieldErrors()) {
          errors.add(fieldProblem(fe.getField(), fe.getDefaultMessage(), locale));
        }
      } else {
        String name = result.getMethodParameter().getParameterName();
        for (MessageSourceResolvable error : result.getResolvableErrors()) {
          errors.add(fieldProblem(name, error.getDefaultMessage(), locale));
        }
      }
    }
    return withErrors(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, errors, request);
  }

  /** Jakarta Validation sur un corps {@code @Valid} : 422 avec errors[] par champ. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handle(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    List<FieldProblem> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fieldProblem(fe.getField(), fe.getDefaultMessage(), locale))
            .toList();
    return withErrors(
        HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_FAILED, errors, request);
  }

  /** Liaison de paramètres de formulaire ou de requête qui échoue (hors corps JSON). */
  @ExceptionHandler(BindException.class)
  ResponseEntity<ProblemDetail> handle(BindException ex, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    List<FieldProblem> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fieldProblem(fe.getField(), fe.getDefaultMessage(), locale))
            .toList();
    return withErrors(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, errors, request);
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  ResponseEntity<ProblemDetail> notFound(Exception ex, HttpServletRequest request) {
    return standard(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> handle(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    ProblemDetail pd =
        standardProblem(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, request);
    return response(pd, request, ex.getHeaders());
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ProblemDetail> handle(
      HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
    return standard(HttpStatus.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE, request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> handle(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    ProblemDetail pd =
        standardProblem(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    return response(pd, request, ex.getHeaders());
  }

  /**
   * Plafond multipart dépassé : levé par le DispatcherServlet avant le choix du contrôleur, donc
   * aucun advice de module ne s'applique. Message générique : les contrôles précis (photo 5 Mo,
   * justificatif 10 Mo) restent dans chaque module.
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> handle(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return standard(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.UPLOAD_TOO_LARGE, request);
  }

  /** Le client a fermé la connexion : rien à répondre. */
  @ExceptionHandler(AsyncRequestNotUsableException.class)
  void clientGone(AsyncRequestNotUsableException ex) {
    LOG.debug("Client déconnecté avant la fin de la réponse", ex);
  }

  /**
   * Tout le reste : les exceptions Spring MVC qui portent un statut ({@link ErrorResponse}, dont
   * {@code ResponseStatusException}) gardent ce statut ; une exception de sécurité est relancée au
   * mécanisme de Spring Security (401/403 gérés par sa chaîne) ; le reste est une erreur 500 au
   * détail générique, journalisée en ERROR.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleAny(Exception ex, HttpServletRequest request)
      throws Exception {
    if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
      throw ex;
    }
    if (ex instanceof ErrorResponse er) {
      return fromErrorResponse(er, ex, request);
    }
    LOG.error("Erreur inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
    return standard(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, request);
  }

  private ResponseEntity<ProblemDetail> fromErrorResponse(
      ErrorResponse er, Exception ex, HttpServletRequest request) {
    HttpStatusCode status = er.getStatusCode();
    if (status.is5xxServerError()) {
      LOG.error(
          "Erreur {} sur {} {}", status.value(), request.getMethod(), request.getRequestURI(), ex);
      return standard(status, ErrorCode.forStatus(status.value()), request);
    }
    String code = ErrorCode.forStatus(status.value());
    // Raison fournie par le code appelant (ResponseStatusException) : sûre pour un 4xx.
    String reason = ex instanceof ErrorResponseException ere ? ere.getBody().getDetail() : null;
    ProblemDetail pd = standardProblem(status, code, request);
    if (reason != null && !reason.isBlank()) {
      pd.setDetail(reason);
    }
    HttpHeaders headers = er.getHeaders();
    return response(pd, request, headers.isEmpty() ? null : headers);
  }

  // ---- construction

  private static FieldProblem fieldProblem(String field, String message, Locale locale) {
    return new FieldProblem(
        field,
        ErrorCode.INVALID_FIELD,
        message == null ? ProblemMessages.invalidValue(locale) : message);
  }

  private static ResponseEntity<ProblemDetail> standard(
      HttpStatusCode status, String code, HttpServletRequest request) {
    return response(standardProblem(status, code, request), request, null);
  }

  private static ProblemDetail standardProblem(
      HttpStatusCode status, String code, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    return Problems.of(
        status, code, Problems.titleOf(status), ProblemMessages.detail(code, locale));
  }

  private static ResponseEntity<ProblemDetail> withErrors(
      HttpStatusCode status, String code, List<FieldProblem> errors, HttpServletRequest request) {
    Locale locale = ProblemMessages.localeOf(request);
    ProblemDetail pd =
        Problems.of(
            status, code, Problems.titleOf(status), ProblemMessages.detail(code, locale), errors);
    return response(pd, request, null);
  }

  private static ResponseEntity<ProblemDetail> response(
      ProblemDetail pd, HttpServletRequest request, HttpHeaders extraHeaders) {
    if (pd.getInstance() == null) {
      try {
        pd.setInstance(URI.create(request.getRequestURI()));
      } catch (IllegalArgumentException e) {
        // URI brute non conforme : instance omise.
      }
    }
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(pd.getStatus());
    if (extraHeaders != null) {
      builder.headers(extraHeaders);
    }
    return builder.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
  }
}
