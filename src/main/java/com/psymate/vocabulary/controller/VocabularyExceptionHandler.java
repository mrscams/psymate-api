package com.psymate.vocabulary.controller;

import com.psymate.shared.problem.AdviceOrder;
import com.psymate.vocabulary.model.VocabularyException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs RFC 9457 limitées aux contrôleurs du module vocabulary. */
@Order(AdviceOrder.MODULE)
@RestControllerAdvice(
    assignableTypes = {VocabularyController.class, TagController.class, MyEmotionController.class})
class VocabularyExceptionHandler {

  @ExceptionHandler(VocabularyException.class)
  ProblemDetail handle(VocabularyException ex) {
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
