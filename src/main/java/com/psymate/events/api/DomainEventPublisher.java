package com.psymate.events.api;

/** Façade de publication des événements de domaine, exposée aux autres modules. */
public interface DomainEventPublisher {

  /** Publie un événement ; il est persisté dans le registre au commit de la transaction. */
  void publish(Object event);
}
