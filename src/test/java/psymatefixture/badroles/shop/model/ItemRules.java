package psymatefixture.badroles.shop.model;

import psymatefixture.badroles.shop.service.ShopService;

/** Violation : model ne depend que de model et d'api. */
public class ItemRules {
  ShopService service;
}
