package com.psymate.vocabulary;

import java.util.UUID;

/**
 * Fusion de tags : requêtes gardées en SQL natif (checklist §1) car elles reposent sur des
 * sous-requêtes que JPQL exprime mal ({@code delete ... where entry_id in (select ...)}, {@code
 * usage_count + (select ...)}), sur des tables hors du module (entry_tag, patient_tag, jamais
 * mappées ici) et sur {@code to_regclass}.
 */
interface TagNativeQueries {

  /**
   * Réétiquette entry_tag (ignoré tant que la table n'existe pas) et patient_tag de la source vers
   * la cible sans doublon, additionne atomiquement {@code usage_count} de la source à la cible,
   * puis rafraîchit l'entité cible du contexte de persistance (sinon elle porterait l'ancien
   * compteur). L'archivage de la source reste une méthode d'entité ({@code Tag.archiveInto}).
   */
  void moveTagUsages(UUID source, UUID target);
}
