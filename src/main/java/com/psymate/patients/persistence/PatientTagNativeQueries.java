package com.psymate.patients.persistence;

import java.util.List;
import java.util.UUID;

/**
 * Réécriture de {@code patient_tag} après une fusion de tags (M9-03), en SQL natif (G Q38) : verrou
 * consultatif et instruction unique {@code delete ... returning} suivie d'un {@code insert ... on
 * conflict do nothing}, que JPQL n'exprime pas.
 */
public interface PatientTagNativeQueries {

  /**
   * Verrou transactionnel par praticien : les réécritures après fusion d'un même praticien se
   * sérialisent jusqu'au commit, et l'état des fusions est relu après le verrou.
   */
  void lockPractitionerForTagMerge(UUID practitionerId);

  /**
   * Remplace {@code source} par {@code target} sur chaque patient, sans doublon, en une seule
   * instruction (un seul instantané : aucune ligne retirée sans être recopiée) ; la date d'ajout du
   * tag est gardée. Rend les patients qui portaient la source.
   */
  List<UUID> replaceTag(UUID source, UUID target);
}
