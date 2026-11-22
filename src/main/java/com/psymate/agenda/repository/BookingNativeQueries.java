package com.psymate.agenda.repository;

import java.util.UUID;

/**
 * SQL natif de la prise de rendez-vous en ligne (G Q38 : fragment Spring Data sur l'{@code
 * EntityManager} du module, une seule unité de travail).
 *
 * <p>Les verrous consultatifs transactionnels ({@code pg_advisory_xact_lock}) sérialisent les
 * transactions qui prennent la même clé ; ils ne protègent que parce que la base tourne en
 * isolation READ COMMITTED (défaut de PostgreSQL, jamais changé par l'application) : chaque
 * instruction lancée <b>après</b> l'obtention du verrou prend un nouvel instantané et voit ce que
 * la transaction précédente a validé. L'appelant doit donc relire ce qu'il protège après le verrou
 * (une entité chargée avant reste périmée). En REPEATABLE READ ou SERIALIZABLE, l'instantané serait
 * pris à la première instruction de la transaction et la relecture ne verrait rien.
 */
public interface BookingNativeQueries {

  /**
   * Verrou consultatif transactionnel du praticien ({@code pg_advisory_xact_lock}, G Q77) : la
   * vérification d'une demande et l'annulation par le demandeur se suivent une à une pour un même
   * praticien. Exige une transaction en cours ; libéré à sa fin (validation ou annulation). Toute
   * lecture faite après l'appel voit ce que la transaction précédente a validé.
   */
  void lockPractitioner(UUID practitionerId);

  /**
   * Verrou consultatif transactionnel d'une empreinte d'adresse ({@code email_hash}, G Q79), dans
   * un espace de clés distinct de celui du praticien : les créations d'une même adresse se suivent
   * une à une, si bien que le comptage des demandes de l'heure puis l'insertion ne peuvent pas
   * dépasser le plafond en parallèle. Exige une transaction en cours ; à prendre avant le comptage.
   */
  void lockEmail(String emailHash);
}
