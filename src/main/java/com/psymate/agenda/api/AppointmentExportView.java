package com.psymate.agenda.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Un rendez-vous tel que l'export le lit (G Q61, M11-11) : données brutes, mise en forme laissée à
 * l'appelant. {@code modality} et {@code status} portent la valeur SQL ({@code in_person}, {@code
 * video}, {@code phone} ; {@code planned}, {@code tentative}, {@code cancelled}, {@code done}).
 *
 * @param patientId patient visé, {@code null} pour un rendez-vous invité (G Q60)
 * @param subjectDisplayName nom affiché du patient, « Prénom Nom » de l'invité (le nom seul sans
 *     prénom), ou « Invité purgé »
 * @param guestPhone téléphone de l'invité, {@code null} pour un patient, un invité sans téléphone
 *     ou un invité purgé
 * @param seriesId série du rendez-vous, {@code null} hors série
 */
public record AppointmentExportView(
    UUID id,
    UUID patientId,
    String subjectDisplayName,
    String guestPhone,
    Instant startsAt,
    Instant endsAt,
    int durationMin,
    String modality,
    String status,
    UUID seriesId,
    String planningNote,
    String cancelReason,
    Instant cancelledAt) {}
