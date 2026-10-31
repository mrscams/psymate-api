package com.psymate.directory;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs RFC 9457 limitées aux contrôleurs du module directory. */
@RestControllerAdvice(
    assignableTypes = {
      ProfileController.class,
      DirectoryVocabularyController.class,
      PublicDirectoryController.class
    })
class DirectoryExceptionHandler {

  @ExceptionHandler(ProfileException.class)
  ProblemDetail handle(ProfileException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    pd.setTitle(ex.status().getReasonPhrase());
    pd.setProperty("code", ex.code());
    if (!ex.errors().isEmpty()) {
      pd.setProperty("errors", ex.errors());
    }
    ex.extra().forEach(pd::setProperty);
    return pd;
  }
}
