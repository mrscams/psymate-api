package com.psymate.identity.verification;

import com.psymate.identity.api.VerificationSubmitted;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** Postgres (pgvector), Mailpit et enregistreur d'événements pour les tests du dossier. */
@TestConfiguration(proxyBeanMethods = false)
@Import({SharedPostgres.class, SharedMailpit.class})
public class VerificationTestConfig {

  /** Événements VerificationSubmitted reçus. */
  public static class Recorder {
    public final List<UUID> submitted = new CopyOnWriteArrayList<>();

    @EventListener
    void on(VerificationSubmitted e) {
      submitted.add(e.getPractitionerId());
    }
  }

  @Bean
  Recorder recorder() {
    return new Recorder();
  }
}
