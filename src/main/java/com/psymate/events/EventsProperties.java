package com.psymate.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages du registre d'événements (préfixe {@code app.events}).
 *
 * @param republishOnStartup rejoue les publications incomplètes au démarrage
 * @param republishOlderThan âge minimal d'une publication incomplète pour être rejouée au démarrage
 *     (évite de rejouer ce qu'une autre instance est en train de traiter)
 * @param completedRetention durée de conservation des publications complétées avant purge
 */
@ConfigurationProperties(prefix = "app.events")
record EventsProperties(
    boolean republishOnStartup, Duration republishOlderThan, Duration completedRetention) {}
