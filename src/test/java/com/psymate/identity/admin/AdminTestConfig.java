package com.psymate.identity.admin;

import com.psymate.identity.api.PractitionerApproved;
import com.psymate.identity.api.PractitionerRejected;
import com.psymate.identity.api.PractitionerSuspended;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** Postgres (pgvector), Mailpit et enregistreur d'événements pour les tests d'administration. */
@TestConfiguration(proxyBeanMethods = false)
@Import({SharedPostgres.class, SharedMailpit.class})
public class AdminTestConfig {

  /** Événements PractitionerApproved, PractitionerRejected et PractitionerSuspended reçus. */
  public static class Recorder {
    public final List<UUID> approved = new CopyOnWriteArrayList<>();
    public final List<UUID> rejected = new CopyOnWriteArrayList<>();
    public final List<UUID> suspended = new CopyOnWriteArrayList<>();

    @EventListener
    void on(PractitionerSuspended e) {
      suspended.add(e.getPractitionerId());
    }

    @EventListener
    void on(PractitionerApproved e) {
      approved.add(e.getPractitionerId());
    }

    @EventListener
    void on(PractitionerRejected e) {
      rejected.add(e.getPractitionerId());
    }
  }

  @Bean
  Recorder recorder() {
    return new Recorder();
  }
}
