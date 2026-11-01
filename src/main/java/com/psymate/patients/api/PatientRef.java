package com.psymate.patients.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Référence minimale d'un patient (identifiant, nom affiché, date de naissance, archivé ou non) :
 * aucun contact.
 */
public record PatientRef(UUID id, String displayName, LocalDate birthDate, boolean archived) {}
