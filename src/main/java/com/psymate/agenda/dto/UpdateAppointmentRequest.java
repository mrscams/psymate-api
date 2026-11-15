package com.psymate.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Modification partielle d'un rendez-vous (I3). Un champ absent (ou null) reste inchangé, sauf
 * planningNote : un null explicite l'efface. C'est pourquoi la requête n'est pas un record : le
 * setter de planningNote retient que la clé était présente dans le JSON.
 */
@Schema(
    description =
        "Modification partielle : un champ absent reste inchangé ; planningNote à null efface la"
            + " note. patientId, tentative, recurrence, status, practitionerId, tenantId,"
            + " sessionEntryId, authorType et submissionId sont refusés (UNKNOWN_FIELD).")
public final class UpdateAppointmentRequest {

  @Schema(
      description =
          "Nouveau début, UTC ISO 8601 ; la durée est conservée si elle n'est pas fournie.",
      example = "2026-11-16T10:00:00Z")
  private Instant startsAt;

  @Schema(
      description = "Nouvelle durée en minutes, de 5 à 600 ; le début est conservé.",
      minimum = "5",
      maximum = "600",
      example = "50")
  @Min(5)
  @Max(600)
  private Integer durationMin;

  @Schema(
      description = "Nouvelle modalité.",
      allowableValues = {"in_person", "video", "phone"})
  @Pattern(regexp = "in_person|video|phone")
  private String modality;

  @Schema(
      description = "Nouvelle note de planification (2 000 caractères au plus) ; null l'efface.",
      types = {"string", "null"},
      maxLength = 2000)
  @Size(max = 2000)
  private String planningNote;

  private boolean planningNotePresent;

  public UpdateAppointmentRequest() {}

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Integer getDurationMin() {
    return durationMin;
  }

  public void setDurationMin(Integer durationMin) {
    this.durationMin = durationMin;
  }

  public String getModality() {
    return modality;
  }

  public void setModality(String modality) {
    this.modality = modality;
  }

  public String getPlanningNote() {
    return planningNote;
  }

  /** Appelé par Jackson dès que la clé est présente, y compris avec null. */
  public void setPlanningNote(String planningNote) {
    this.planningNote = planningNote;
    this.planningNotePresent = true;
  }

  /** La clé planningNote figurait dans le corps (valeur éventuellement null). */
  public boolean planningNoteProvided() {
    return planningNotePresent;
  }
}
