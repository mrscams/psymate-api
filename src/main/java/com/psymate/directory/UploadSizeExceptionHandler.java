package com.psymate.directory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Advice global limité à une seule exception : le dépassement de taille multipart est levé par le
 * DispatcherServlet avant le choix du contrôleur, un advice restreint à ProfileController ne
 * s'appliquerait donc pas. Le seul upload de l'API en V1 est la photo de profil.
 */
@RestControllerAdvice
class UploadSizeExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "La photo dépasse 5 Mo.");
    pd.setTitle(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase());
    pd.setProperty("code", "PHOTO_TOO_LARGE");
    return pd;
  }
}
