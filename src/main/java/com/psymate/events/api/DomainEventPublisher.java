package com.psymate.events.api;

/** Façade de publication des événements de domaine, exposée aux autres modules. */
public interface DomainEventPublisher {

  /**
   * Publie un événement ; il est persisté dans le registre au commit de la transaction.
   *
   * <p>Tout abonné {@code @ApplicationModuleListener} doit être idempotent : la republication au
   * redémarrage (EventRepublisher) peut le réinvoquer avec le même événement ; voir {@link
   * EventDeduplicator}.
   */
  void publish(Object event);
}
