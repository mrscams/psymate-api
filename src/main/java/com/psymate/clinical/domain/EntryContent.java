package com.psymate.clinical.domain;

import com.psymate.clinical.domain.EntryChecks.EventChecks;
import com.psymate.clinical.domain.EntryChecks.FeelingChecks;
import com.psymate.clinical.domain.EntryChecks.MedicationChecks;
import com.psymate.clinical.domain.EntryChecks.NoteChecks;
import com.psymate.clinical.domain.EntryChecks.SessionChecks;
import com.psymate.clinical.domain.EntryChecks.SituationChecks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Contenu éditable d'une entrée (ce qu'une version fige), validé selon le type par un groupe
 * Jakarta dédié ({@link EntryChecks}).
 */
public record EntryContent(
    @NotBlank(groups = {EventChecks.class, SituationChecks.class}) String title,
    @NotBlank(groups = NoteChecks.class) String body,
    Visibility visibility,
    EntryStatus status,
    @NotNull(groups = SituationChecks.class) TimeSpan time,
    @Valid @NotNull(groups = FeelingChecks.class) FeelingDetail feeling,
    @Valid @NotNull(groups = MedicationChecks.class) MedicationDetail medication,
    @Valid @NotNull(groups = SessionChecks.class) SessionDetail session) {}
