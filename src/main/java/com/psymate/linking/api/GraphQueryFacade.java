package com.psymate.linking.api;

import java.util.List;
import java.util.UUID;

/** Lecture du graphe d'un patient pour les autres modules (vue d'ensemble, recherche…). */
public interface GraphQueryFacade {

  /**
   * Voisinage de la racine à la profondeur demandée (A4.4), limité à 150 nœuds : au-delà, les nœuds
   * de degré 1 d'un même type reliés au même voisin sont regroupés en super-nœuds. Seuls les liens
   * acceptés comptent ; les entrées archivées n'apparaissent pas.
   *
   * @throws com.psymate.patients.api.PatientAccessException 403 si le patient est d'un autre
   *     praticien, 404 s'il n'existe pas
   * @throws com.psymate.linking.domain.GraphRequestException 422 si la profondeur, un type de lien
   *     ou la période est invalide
   * @throws com.psymate.linking.domain.LinkException ENTRY_NOT_FOUND (404, racine inconnue ou d'un
   *     autre praticien), DIFFERENT_PATIENT (422, racine d'un autre patient du même praticien),
   *     ENTRY_ARCHIVED (409)
   */
  GraphView neighborhood(GraphQuery query);

  /**
   * Développe un groupe : les nœuds du groupe et tous les liens acceptés qui les touchent. Le
   * groupe est désigné soit par l'identifiant d'un super-nœud ({@code superNodeId}), soit par la
   * liste de ses membres ({@code memberIds}) : exactement l'un des deux. Un super-nœud se développe
   * sans les filtres (types de liens, période) du graphe qui l'a produit : ses membres sont toutes
   * les entrées non archivées du type, reliées au voisin par leur seul lien accepté ; au-delà de
   * 500 membres la réponse est tronquée ({@code truncated = true}). Pour un développement exact, le
   * client envoie les {@code memberIds} reçus avec le super-nœud.
   *
   * @throws com.psymate.linking.domain.GraphRequestException 422 si les paramètres sont invalides
   *     ou si un membre n'est pas une entrée non archivée du patient
   * @throws com.psymate.linking.domain.LinkException SUPER_NODE_NOT_FOUND (404) ; garde de
   *     propriété comme {@link #neighborhood}
   */
  GraphView expand(UUID patientId, String superNodeId, List<UUID> memberIds);
}
