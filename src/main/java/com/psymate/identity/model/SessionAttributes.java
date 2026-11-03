package com.psymate.identity.model;

/** Attributs que PsyMate pose sur la session Spring Session à son ouverture (M10-06). */
public final class SessionAttributes {

  /** Libellé de l'appareil déduit de l'User-Agent (voir {@code UserAgents.device}). */
  public static final String DEVICE = "psymate.device";

  /**
   * Instant (ISO 8601, horloge de l'application) de l'ouverture de la session, pris avant que la
   * connexion écrive son propre événement {@code login_ok} : la connexion précédente est le dernier
   * {@code login_ok} strictement antérieur.
   */
  public static final String OPENED_AT = "psymate.openedAt";

  private SessionAttributes() {}
}
