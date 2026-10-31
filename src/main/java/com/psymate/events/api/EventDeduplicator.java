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
 * <p>Limite : l'achèvement d'une publication est écrit après le commit de la transaction de
 * l'abonné. Si le processus s'arrête entre les deux, l'effet est commité sans que la publication
 * soit marquée complétée, et {@code once} ne peut pas le savoir. L'abonné dont l'effet n'est pas
 * idempotent par nature ajoute donc aussi un test d'existence métier.
 *
 * <p>Recommandation : déclarer {@code @ApplicationModuleListener(id = "...")} avec un identifiant
 * stable. Sans id, Spring utilise le nom de la classe et de la méthode ; renommer la classe change
 * le {@code listener_id} et orpheline les publications incomplètes.
 */
public interface EventDeduplicator {

  /**
   * Exécute {@code effect} au plus une fois pour le couple (événement, abonné) : retourne {@code
   * false}, sans exécuter, si une publication complétée de cet événement existe déjà pour cet
   * abonné. À appeler dans la transaction de l'abonné (celle que {@code @ApplicationModuleListener}
   * ouvre en REQUIRES_NEW) ; lève {@link IllegalStateException} sinon. Les exécutions concurrentes
   * du même couple sont sérialisées par un verrou advisory transactionnel PostgreSQL.
   *
   * @param listenerId identifiant de l'abonné, tel que stocké dans {@code listener_id}
   * @return {@code true} si l'effet a été exécuté
   */
  boolean once(DomainEvent event, String listenerId, Runnable effect);

  /**
   * Identifiant que Spring attribue à une méthode d'écoute sans attribut {@code id} : {@code
   * FQCN.methode(FQCN des paramètres séparés par des virgules)}.
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
