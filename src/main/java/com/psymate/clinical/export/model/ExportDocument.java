package com.psymate.clinical.export.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Modèle interne d'un export de dossier (M6-05) : tout ce qui est rendu, déjà prêt à afficher
 * (libellés localisés, dates formatées). Les deux rendus, Markdown et HTML (puis PDF), ne font que
 * mettre en page ce modèle. Il ne contient jamais d'entrée privée ni archivée, ni d'ancienne
 * version, ni coordonnées du patient.
 *
 * @param age âge en années à la date de l'export, nul si la date de naissance est inconnue
 * @param sessions séances dans l'ordre chronologique croissant
 * @param unassigned entrées dont la séance n'est pas exportée (« Sans séance »)
 * @param appointments rendez-vous du patient, triés par début (G Q61, M11-11) ; section omise si
 *     vide
 */
public record ExportDocument(
    String patientName,
    LocalDate birthDate,
    Integer age,
    LocalDate exportedOn,
    List<SessionBlock> sessions,
    List<EntryBlock> unassigned,
    List<AppointmentBlock> appointments) {

  public ExportDocument {
    sessions = List.copyOf(sessions);
    unassigned = List.copyOf(unassigned);
    appointments = List.copyOf(appointments);
  }

  /** Dossier sans rendez-vous. */
  public ExportDocument(
      String patientName,
      LocalDate birthDate,
      Integer age,
      LocalDate exportedOn,
      List<SessionBlock> sessions,
      List<EntryBlock> unassigned) {
    this(patientName, birthDate, age, exportedOn, sessions, unassigned, List.of());
  }

  /** Une séance (accueil, séance, contact) avec ses entrées. */
  public record SessionBlock(
      String kind,
      String heading,
      String details,
      String title,
      String body,
      List<EntryBlock> entries) {

    public SessionBlock {
      entries = List.copyOf(entries);
    }
  }

  /**
   * Une entrée de la version courante. {@code feeling} et {@code medication} sont des lignes déjà
   * composées, nulles pour les autres types.
   */
  public record EntryBlock(
      String typeCode,
      String typeLabel,
      String dateLabel,
      String title,
      String body,
      boolean draft,
      String feeling,
      String medication,
      List<String> tags,
      List<String> links) {

    public EntryBlock {
      tags = List.copyOf(tags);
      links = List.copyOf(links);
    }
  }

  /**
   * Un rendez-vous (G Q61), cellules déjà formatées : dates et heures en UTC comme le reste de
   * l'export, libellés localisés. {@code person} et {@code phone} ne servent qu'au fichier {@code
   * rendez-vous.md} de l'export du compte (nuls dans un dossier) ; les champs facultatifs absents
   * sont nuls.
   */
  public record AppointmentBlock(
      String date,
      String hours,
      String duration,
      String modality,
      String status,
      String person,
      String phone,
      String series,
      String planningNote,
      String cancelReason,
      String cancelledAt) {}

  public int sessionCount() {
    return sessions.size();
  }

  /** Entrées exportées hors séances, séances de rattachement comprises dans « Sans séance ». */
  public int entryCount() {
    return sessions.stream().mapToInt(s -> s.entries().size()).sum() + unassigned.size();
  }
}
