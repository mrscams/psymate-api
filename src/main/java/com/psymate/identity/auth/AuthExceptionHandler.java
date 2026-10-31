package com.psymate.identity.auth;

import com.psymate.identity.auth.AuthExceptions.AccountLockedException;
import com.psymate.identity.auth.AuthExceptions.AccountSuspendedException;
import com.psymate.identity.auth.AuthExceptions.EmailNotConfirmedException;
import com.psymate.identity.auth.AuthExceptions.FieldValidationException;
import com.psymate.identity.auth.AuthExceptions.InvalidCredentialsException;
import com.psymate.identity.auth.AuthExceptions.InvalidSecondFactorException;
import com.psymate.identity.auth.AuthExceptions.InvalidTokenException;
import com.psymate.identity.auth.AuthExceptions.PasswordPolicyException;
import com.psymate.identity.auth.AuthExceptions.TwoFactorStateException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
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
@RestControllerAdvice(assignableTypes = {AuthController.class, TwoFactorController.class})
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

  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<ProblemDetail> fields(FieldValidationException ex, Locale locale) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (String[] e : ex.errors()) {
      errors.add(error(e[0], e[1], messages.get(e[2], locale)));
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

  @ExceptionHandler(AccountLockedException.class)
  ResponseEntity<ProblemDetail> locked(AccountLockedException ex, Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.LOCKED,
            "ACCOUNT_LOCKED",
            messages.get("error.locked.title", locale),
            messages.get("error.locked.detail", locale));
    pd.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
    return ResponseEntity.status(HttpStatus.LOCKED)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
        .body(pd);
  }

  @ExceptionHandler(EmailNotConfirmedException.class)
  ResponseEntity<ProblemDetail> emailNotConfirmed(Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.FORBIDDEN,
            "EMAIL_NOT_CONFIRMED",
            messages.get("error.emailPending.title", locale),
            messages.get("error.emailPending.detail", locale));
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
  }

  @ExceptionHandler(AccountSuspendedException.class)
  ResponseEntity<ProblemDetail> suspended(Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.FORBIDDEN,
            "ACCOUNT_SUSPENDED",
            messages.get("error.suspended.title", locale),
            messages.get("error.suspended.detail", locale));
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
  }

  @ExceptionHandler(InvalidSecondFactorException.class)
  ResponseEntity<ProblemDetail> secondFactor(InvalidSecondFactorException ex, Locale locale) {
    String code = "code".equals(ex.field()) ? "INVALID_CODE" : "INVALID_PASSWORD";
    ProblemDetail pd =
        problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "VALIDATION_FAILED",
            messages.get("error.validation.title", locale),
            messages.get("error.validation.detail", locale));
    pd.setProperty(
        "errors", List.of(error(ex.field(), code, messages.get("error." + code, locale))));
    return ResponseEntity.unprocessableContent().body(pd);
  }

  @ExceptionHandler(TwoFactorStateException.class)
  ResponseEntity<ProblemDetail> twoFactorState(TwoFactorStateException ex, Locale locale) {
    ProblemDetail pd =
        problem(
            HttpStatus.CONFLICT,
            ex.code(),
            messages.get("error.twofactor.title", locale),
            messages.get("error." + ex.code(), locale));
    return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
  }
}
