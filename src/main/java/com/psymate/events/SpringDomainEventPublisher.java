package com.psymate.events;

import com.psymate.events.api.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implémentation sur l'ApplicationEventPublisher de Spring ; le registre JDBC de Spring Modulith
 * (table event_publication) persiste les publications destinées aux abonnés transactionnels.
 */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher delegate;

  SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
    this.delegate = delegate;
  }

  @Override
  public void publish(Object event) {
    delegate.publishEvent(event);
  }
}
