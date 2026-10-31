package com.psymate.clinical.domain;

import com.psymate.clinical.domain.EntryChecks.SessionChecks;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** Détail d'une séance (ADR-10) ; closedAt nul = séance ouverte. */
public record SessionDetail(
    @Pattern(regexp = "intake|session|contact", groups = SessionChecks.class) String kind,
    @NotBlank(groups = SessionChecks.class)
        @Pattern(regexp = "in_person|video|phone|email", groups = SessionChecks.class)
        String modality,
    @Min(value = 1, groups = SessionChecks.class) @Max(value = 600, groups = SessionChecks.class)
        Integer durationMin,
    Instant closedAt) {

  public static final String KIND_INTAKE = "intake";
  public static final String KIND_SESSION = "session";
  public static final String KIND_CONTACT = "contact";

  /** kind absent : « session ». */
  public String kindOrDefault() {
    return kind == null ? KIND_SESSION : kind;
  }
}
