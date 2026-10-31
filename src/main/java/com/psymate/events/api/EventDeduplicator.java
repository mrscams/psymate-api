package com.psymate.events.api;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Support d'idempotence des abonnés d'événements (M4-01).
 *
 * <p>Règle : tout abonné {@code @ApplicationModuleListener} doit être idempotent, car la
 * republication au redémarrage ou un rejeu concurrent peut le réinvoquer avec le même événement. Un
 * abonné dont l'effet n'est pas naturellement idempotent l'enveloppe dans {@link #once}.
 *
 * <p>Garantie : {@link #once} marque la publication complétée dans la transaction même de l'effet.
 * Un rejeu (redémarrage, resoumission manuelle, même événement publié deux fois) retrouve donc soit
 * une publication complétée, et n'exécute rien, soit une transaction non commitée, et attend sa fin
 * sur le verrou. Ce que le support ne couvre pas : un effet exécuté hors de la transaction de
 * l'abonné (appel réseau, fichier) ; l'abonné le rend lui-même rejouable, par exemple par un test
 * d'existence métier.
 *
 * <p>Recommandation : déclarer {@code @ApplicationModuleListener(id = "...")} avec un identifiant
 * stable. Sans id, Spring utilise le nom de la classe et de la méthode ; renommer la classe change
 * le {@code listener_id} et orpheline les publications incomplètes.
 */
public interface EventDeduplicator {

  /**
   * Exécute {@code effect} au plus une fois pour le couple (événement, abonné) : retourne {@code
   * false}, sans exécuter, si une publication complétée de cet événement existe déjà pour cet
   * abonné ; sinon exécute l'effet et marque la publication complétée dans la transaction courante.
   * À appeler dans la transaction de l'abonné (celle que {@code @ApplicationModuleListener} ouvre
   * en REQUIRES_NEW) ; lève {@link IllegalStateException} sinon. Les exécutions concurrentes du
   * même couple sont sérialisées par un verrou advisory transactionnel PostgreSQL, tenu jusqu'à la
   * fin de la transaction.
   *
   * @param listenerId identifiant de l'abonné, tel que stocké dans {@code listener_id}
   * @return {@code true} si l'effet a été exécuté
   */
  boolean once(DomainEvent event, String listenerId, Runnable effect);

  /**
   * Identifiant que Spring attribue à une méthode d'écoute sans attribut {@code id} : {@code
   * FQCN.methode(FQCN des paramètres séparés par des virgules)}. Spring prend la classe qui déclare
   * la méthode : pour une méthode d'écoute héritée, passer la classe parente.
   */
  static String listenerId(Class<?> listenerClass, String method, Class<?>... parameterTypes) {
    return listenerClass.getName()
        + "."
        + method
        + Arrays.stream(parameterTypes)
            .map(Class::getName)
            .collect(Collectors.joining(",", "(", ")"));
  }
}
