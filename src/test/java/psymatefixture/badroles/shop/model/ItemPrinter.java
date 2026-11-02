package psymatefixture.badroles.shop.model;

import psymatefixture.badroles.shop.dto.ItemDto;

/** Violation : model ne depend que de model et d'api (pas de dto). */
public class ItemPrinter {
  ItemDto dto;
}
