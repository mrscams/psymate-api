package com.psymate.clinical.model;

import com.psymate.clinical.model.EntryChecks.MedicationChecks;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Détail d'un médicament (H9). */
public record MedicationDetail(
    @NotBlank(groups = MedicationChecks.class) String molecule,
    String dosage,
    String frequency,
    String prescriber,
    LocalDate periodFrom,
    LocalDate periodTo) {}
