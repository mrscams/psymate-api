package com.psymate.clinical.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture d'une séance par les autres modules (G Q85). Sert à billing quand le praticien agit sur
 * une séance avant que {@link SessionClosed} n'ait été traité (G Q86).
 */
public interface SessionFacade {

  /**
   * Séance close (closed_at renseigné), quel que soit son kind. Vide si la séance n'existe pas,
   * n'est pas une séance, est archivée ou n'est pas close ; vide aussi, comme les autres lectures
   * des façades de clinical (ADR-09), sans praticien courant ou si son patient appartient à un
   * autre praticien.
   */
  Optional<SessionView> closedSession(UUID sessionId);
}
