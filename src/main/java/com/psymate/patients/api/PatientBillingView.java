package com.psymate.patients.api;

import java.util.UUID;

/**
 * Ce que la facturation doit savoir d'un patient pour lui adresser une facture (M15-04, G Q85) :
 * son identité, son adresse e-mail et son état d'archivage. Rien d'autre : ni {@code follow_up}
 * ({@code fee}, {@code prescriber}, G Q56 et Q87), ni contexte, ni téléphone, ni donnée clinique.
 *
 * @param id identifiant du patient
 * @param ownerId praticien propriétaire : l'appelant le compare au praticien courant
 * @param displayName nom affiché, toujours présent
 * @param firstName prénom, null s'il n'est pas renseigné
 * @param lastName nom, null s'il n'est pas renseigné
 * @param email adresse e-mail, null si elle n'est pas renseignée
 * @param archived le patient est archivé
 */
public record PatientBillingView(
    UUID id,
    UUID ownerId,
    String displayName,
    String firstName,
    String lastName,
    String email,
    boolean archived) {}
