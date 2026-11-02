package psymatefixture.badroles.shop.service;

import psymatefixture.badroles.shop.model.Item;
import psymatefixture.badroles.shop.repository.ShopRepository;

/** Correct : service → repository et service → model. */
public class ShopService {
  ShopRepository repository;
  Item item;
}
