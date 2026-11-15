package com.psymate.patients.api;

import java.time.LocalDate;

/**
 * Cadre du suivi d'un patient tel que les autres modules (agenda, G Q56) ont besoin de le lire :
 * depuis quand, à quel rythme, selon quelle modalité et pour quelle durée de séance. Chaque champ
 * vaut null s'il est absent ou illisible dans {@code patient.follow_up}. {@code fee} et {@code
 * prescriber} (données financières et tiers) ne sont jamais exposés.
 *
 * @param since début du suivi (clé {@code since}, date ISO AAAA-MM-JJ), null sinon
 * @param rhythm rythme du suivi, texte libre jamais interprété (clé {@code rhythm})
 * @param modality modalité, texte libre rendu tel quel (clé {@code modality})
 * @param durationMin durée de séance en minutes : seulement si la clé {@code durationMin} est un
 *     entier entre 1 et 600, null sinon
 */
public record FollowUpView(LocalDate since, String rhythm, String modality, Integer durationMin) {}
