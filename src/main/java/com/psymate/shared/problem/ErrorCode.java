package com.psymate.shared.problem;

/**
 * Codes d'erreur stables du champ {@code code} des réponses RFC 9457 (voir {@link Problems}). Les
 * modules gardent leurs codes métier ; ces constantes couvrent les erreurs transverses (HTTP,
 * sécurité, validation, limitation de débit) et sont celles que renvoie {@link
 * GlobalExceptionHandler} quand aucun handler de module ne répond.
 */
public final class ErrorCode {

  public static final String MALFORMED_JSON = "MALFORMED_JSON";
  public static final String BAD_REQUEST = "BAD_REQUEST";
  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String ADMIN_ROLE_REQUIRED = "ADMIN_ROLE_REQUIRED";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
  public static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";
  public static final String CONFLICT = "CONFLICT";
  public static final String UPLOAD_TOO_LARGE = "UPLOAD_TOO_LARGE";
  public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String LOCKED = "LOCKED";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  /** Code d'un champ en erreur dans {@code errors[]} (validation Jakarta). */
  public static final String INVALID_FIELD = "INVALID_FIELD";

  private ErrorCode() {}

  /** Code par défaut d'un statut HTTP, quand l'erreur n'en porte pas. */
  public static String forStatus(int status) {
    return switch (status) {
      case 400 -> BAD_REQUEST;
      case 401 -> UNAUTHENTICATED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 409 -> CONFLICT;
      case 413 -> UPLOAD_TOO_LARGE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      case 422 -> VALIDATION_FAILED;
      case 423 -> LOCKED;
      case 429 -> RATE_LIMITED;
      default -> status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
    };
  }
}
