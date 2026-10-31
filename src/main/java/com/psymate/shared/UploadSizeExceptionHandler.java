package com.psymate.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Advice global limité à une seule exception : le dépassement du plafond multipart est levé par le
 * DispatcherServlet avant le choix du contrôleur, un advice restreint à un contrôleur ne
 * s'appliquerait donc pas. Message générique : les contrôles précis (photo 5 Mo, justificatif 10
 * Mo) restent dans chaque module.
 */
@RestControllerAdvice
class UploadSizeExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONTENT_TOO_LARGE, "Le fichier dépasse la taille maximale autorisée.");
    pd.setTitle(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase());
    pd.setProperty("code", "UPLOAD_TOO_LARGE");
    return pd;
  }
}
