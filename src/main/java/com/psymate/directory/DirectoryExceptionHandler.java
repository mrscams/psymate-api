package com.psymate.directory;

import com.psymate.shared.problem.AdviceOrder;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Erreurs RFC 9457 limitées aux contrôleurs du module directory. Les erreurs ne sont jamais mises
 * en cache (la chaîne publique désactive le Cache-Control automatique).
 */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(
    assignableTypes = {
      ProfileController.class,
      DirectoryVocabularyController.class,
      PublicDirectoryController.class,
      PublicContactController.class
    })
class DirectoryExceptionHandler {

  @ExceptionHandler(ProfileException.class)
  ResponseEntity<ProblemDetail> handle(ProfileException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    pd.setTitle(ex.status().getReasonPhrase());
    pd.setProperty("code", ex.code());
    if (!ex.errors().isEmpty()) {
      pd.setProperty("errors", ex.errors());
    }
    ex.extra().forEach(pd::setProperty);
    return error(pd);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> handle(MethodArgumentTypeMismatchException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Paramètre invalide.");
    pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    pd.setProperty("code", "INVALID_PARAMETERS");
    pd.setProperty(
        "errors", List.of(new ProfileException.FieldError(ex.getName(), "Valeur invalide.")));
    return error(pd);
  }

  /** Jakarta Validation : 422 avec errors[] par champ. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException ex) {
    List<ProfileException.FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ProfileException.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    return handle(ProfileException.invalid(errors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handle(HttpMessageNotReadableException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corps de requête illisible.");
    pd.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
    pd.setProperty("code", "INVALID_BODY");
    return error(pd);
  }

  private static ResponseEntity<ProblemDetail> error(ProblemDetail pd) {
    return ResponseEntity.status(pd.getStatus())
        .header("Cache-Control", "no-store")
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }
}
