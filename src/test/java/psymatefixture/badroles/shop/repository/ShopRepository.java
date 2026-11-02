package psymatefixture.badroles.shop.repository;

import psymatefixture.badroles.shop.model.Item;

/** Correct : repository → model. */
public class ShopRepository {
  public Item find() {
    return new Item();
  }
}
