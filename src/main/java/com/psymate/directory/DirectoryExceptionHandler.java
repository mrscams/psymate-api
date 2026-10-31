package com.psymate.directory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Erreurs RFC 9457 limitées aux contrôleurs du module directory. */
@RestControllerAdvice(
    assignableTypes = {ProfileController.class, DirectoryVocabularyController.class})
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

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Photo trop volumineuse.");
    pd.setProperty("code", "PHOTO_TOO_LARGE");
    return pd;
  }
}
