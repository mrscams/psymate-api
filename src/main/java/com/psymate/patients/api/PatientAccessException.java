package com.psymate.patients.api;

import com.psymate.shared.problem.ProblemException;

/**
 * Accès refusé à un patient par {@link PatientOwnershipGuard} (ADR-09) : 403 {@code
 * PATIENT_FORBIDDEN} s'il appartient à un autre praticien ou à un autre cabinet, 404 {@code
 * PATIENT_NOT_FOUND} s'il n'existe pas. Rendue en application/problem+json par le handler commun
 * des {@link ProblemException} ({@code shared.problem.GlobalExceptionHandler}) : les modules
 * consommateurs n'ont rien à traduire.
 */
public final class PatientAccessException extends ProblemException {

  /** Les deux refus de la garde, avec leur statut, leur code et leur détail (non localisé). */
  public enum Reason {
    FORBIDDEN(403, "PATIENT_FORBIDDEN", "Ce patient n'est pas le vôtre."),
    NOT_FOUND(404, "PATIENT_NOT_FOUND", "Patient introuvable.");

    private final int status;
    private final String code;
    private final String detail;

    Reason(int status, String code, String detail) {
      this.status = status;
      this.code = code;
      this.detail = detail;
    }

    public int status() {
      return status;
    }

    public String code() {
      return code;
    }
  }

  private final Reason reason;

  private PatientAccessException(Reason reason) {
    super(reason.status, reason.code, reason.detail);
    this.reason = reason;
  }

  /** 403 PATIENT_FORBIDDEN : le patient est à un autre praticien ou à un autre cabinet. */
  public static PatientAccessException forbidden() {
    return new PatientAccessException(Reason.FORBIDDEN);
  }

  /** 404 PATIENT_NOT_FOUND : aucun patient avec cet identifiant. */
  public static PatientAccessException notFound() {
    return new PatientAccessException(Reason.NOT_FOUND);
  }

  public Reason reason() {
    return reason;
  }
}
