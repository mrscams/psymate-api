package psymatefixture.shareddb;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Interdit : la clé seule, sa valeur dans un autre littéral de l'instruction. */
public class RegistryRepublish {
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.events.republish-older-than", () -> "0s");
  }
}
