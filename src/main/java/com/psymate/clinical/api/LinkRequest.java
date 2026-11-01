package com.psymate.clinical.api;

import java.util.UUID;

/**
 * Lien demandé avec une nouvelle entrée (M2-02).
 *
 * @param targetId l'autre extrémité du lien : une entrée existante du même patient
 * @param linkType code du type de lien (catalogue vocabulary)
 * @param direction sens vu depuis la nouvelle entrée ; nul : {@link LinkDirection#OUTGOING}
 */
public record LinkRequest(UUID targetId, String linkType, LinkDirection direction) {

  public LinkRequest {
    direction = direction == null ? LinkDirection.OUTGOING : direction;
  }

  /** Vrai si la nouvelle entrée est la cible du lien. */
  public boolean incoming() {
    return direction == LinkDirection.INCOMING;
  }
}
