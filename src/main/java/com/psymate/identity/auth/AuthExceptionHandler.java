package com.psymate.identity.auth;

import com.psymate.identity.auth.AuthExceptions.DuplicateEmailException;
import com.psymate.identity.auth.AuthExceptions.InvalidCredentialsException;
import com.psymate.identity.auth.AuthExceptions.InvalidTokenException;
import com.psymate.identity.auth.AuthExceptions.PasswordPolicyException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs RFC 9457 (application/problem+json) avec errors[] par champ, limitées à AuthController.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

  private final AuthMessages messages;

  AuthExceptionHandler(AuthMessages messages) {
    this.messages = messages;
  }

  static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    pd.setProperty("code", code);
    return pd;
  }

  static Map<String, String> error(String field, String code, String message) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("field", field);
    m.put("code", code);
    m.put("message", message);
    return m;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, Locale locale) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      errors.add(error(fe.getField(), "INVALID_FIELD", fe.getDefaultMessage()));
    }
    ProblemDetail pd =
        problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "VALIDATION_FAILED",
            messages.get("error.validation.title", locale),
            messages.get("error.validation.detail", locale));
    pd.setProperty("errors", errors);
    return ResponseEntity.unprocessableContent().body(pd);
  }

  @ExceptionHandler(PasswordPolicyException.class)
  ResponseEntity<ProblemDetail> policy(PasswordPolicyException ex, Locale locale) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (String code : ex.codes()) {
      errors.add(error("password", code, messages.get("error." + code, locale)));
    }
    ProblemDetail pd =
        problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "PASSWORD_POLICY",
            messages.get("error.validation.title", locale),
            messages.get("error.password.detail", locale));
    pd.setProperty("errors", errors);
    return ResponseEntity.unprocessableContent().body(pd);
  }

  @ExceptionHandler(DuplicateEmailException.class)
  ResponseEntity<ProblemDetail> duplicate(Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.CONFLICT,
            "EMAIL_ALREADY_REGISTERED",
            messages.get("error.duplicate.title", locale),
            messages.get("error.duplicate.detail", locale));
    pd.setProperty(
        "errors",
        List.of(
            error(
                "email",
                "EMAIL_ALREADY_REGISTERED",
                messages.get("error.duplicate.detail", locale))));
    return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ProblemDetail> credentials(Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            messages.get("error.credentials.title", locale),
            messages.get("error.credentials.detail", locale));
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
  }

  @ExceptionHandler(InvalidTokenException.class)
  ResponseEntity<ProblemDetail> token(Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.GONE,
            "TOKEN_INVALID_OR_EXPIRED",
            messages.get("error.token.title", locale),
            messages.get("error.token.detail", locale));
    return ResponseEntity.status(HttpStatus.GONE).body(pd);
  }
}
