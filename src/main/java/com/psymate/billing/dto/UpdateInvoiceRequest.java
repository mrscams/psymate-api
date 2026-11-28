package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Modification partielle d'un brouillon (PATCH /api/invoices/{id}, L5.3). Un champ absent reste
 * inchangé ; la requête n'est pas un record parce que chaque setter retient que la clé était
 * présente dans le JSON, null compris (comme {@link UpdateBillableSessionRequest}). Sens de null :
 * {@code recipient} revient au destinataire par défaut du patient, {@code dueOn} et {@code
 * footerNote} s'effacent ; {@code patientId}, {@code issueDate}, {@code dueTerm} et {@code lines}
 * ne peuvent pas être null (REQUIRED).
 */
@Schema(
    description =
        "Modification partielle d'un brouillon : un champ absent reste inchangé. lines remplace"
            + " toutes les lignes (une ligne de séance retirée rend la séance à facturer)."
            + " Montants en centimes d'euro entiers : un nombre décimal ou une chaîne est refusé"
            + " (INVALID_FORMAT). Tout autre champ est refusé (UNKNOWN_FIELD).")
public final class UpdateInvoiceRequest {

  @Schema(
      description =
          "Nouveau patient, tant que le brouillon n'a aucune ligne de séance (sinon 422"
              + " INVALID_VALUE) ; le destinataire repasse à celui du nouveau patient, sauf si"
              + " recipient est donné.")
  private UUID patientId;

  private boolean patientIdPresent;

  @Schema(description = "Destinataire ; null revient au destinataire par défaut du patient.")
  @Valid
  private InvoiceRecipientRequest recipient;

  private boolean recipientPresent;

  @Schema(description = "Date d'émission prévue (AAAA-MM-JJ).", example = "2026-11-30")
  private LocalDate issueDate;

  private boolean issueDatePresent;

  @Schema(
      description = "Échéance : on_receipt, days_15, days_30, ou date (dueOn obligatoire).",
      allowableValues = {"on_receipt", "days_15", "days_30", "date"},
      example = "days_15")
  private String dueTerm;

  private boolean dueTermPresent;

  @Schema(
      description =
          "Date d'échéance (AAAA-MM-JJ), seulement avec l'échéance date, au plus tôt la date"
              + " d'émission ; null l'efface.",
      types = {"string", "null"},
      format = "date")
  private LocalDate dueOn;

  private boolean dueOnPresent;

  @Schema(
      description = "Mention de bas de page, 500 caractères au plus ; null ou vide l'efface.",
      types = {"string", "null"},
      maxLength = 500)
  @Size(max = 500)
  private String footerNote;

  private boolean footerNotePresent;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Toutes les lignes, dans l'ordre (remplacement complet) : 100 lignes libres et"
                      + " 200 lignes de séance au plus. Les lignes sont recréées (leur id"
                      + " change)."),
      maxItems = 300)
  @Size(max = 300)
  private List<@NotNull @Valid InvoiceLineRequest> lines;

  private boolean linesPresent;

  public UpdateInvoiceRequest() {}

  public UUID getPatientId() {
    return patientId;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
    this.patientIdPresent = true;
  }

  public InvoiceRecipientRequest getRecipient() {
    return recipient;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setRecipient(InvoiceRecipientRequest recipient) {
    this.recipient = recipient;
    this.recipientPresent = true;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setIssueDate(LocalDate issueDate) {
    this.issueDate = issueDate;
    this.issueDatePresent = true;
  }

  public String getDueTerm() {
    return dueTerm;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setDueTerm(String dueTerm) {
    this.dueTerm = dueTerm;
    this.dueTermPresent = true;
  }

  public LocalDate getDueOn() {
    return dueOn;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setDueOn(LocalDate dueOn) {
    this.dueOn = dueOn;
    this.dueOnPresent = true;
  }

  public String getFooterNote() {
    return footerNote;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setFooterNote(String footerNote) {
    this.footerNote = footerNote;
    this.footerNotePresent = true;
  }

  public List<InvoiceLineRequest> getLines() {
    return lines;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setLines(List<InvoiceLineRequest> lines) {
    this.lines = lines;
    this.linesPresent = true;
  }

  public boolean patientIdProvided() {
    return patientIdPresent;
  }

  public boolean recipientProvided() {
    return recipientPresent;
  }

  /**
   * La clé issueDate figurait dans le corps. Pas de nom en « is… » : Jackson et springdoc y
   * verraient un accesseur booléen ({@code issueDateProvided} deviendrait un champ du contrat).
   */
  public boolean hasIssueDate() {
    return issueDatePresent;
  }

  public boolean dueTermProvided() {
    return dueTermPresent;
  }

  public boolean dueOnProvided() {
    return dueOnPresent;
  }

  public boolean footerNoteProvided() {
    return footerNotePresent;
  }

  public boolean linesProvided() {
    return linesPresent;
  }
}
