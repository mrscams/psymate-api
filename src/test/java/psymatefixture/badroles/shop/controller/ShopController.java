package psymatefixture.badroles.shop.controller;

import org.springframework.web.bind.annotation.RestController;
import psymatefixture.badroles.shop.dto.ItemDto;
import psymatefixture.badroles.shop.service.ShopService;

/** Correct : controller → service et controller → dto. */
@RestController
public class ShopController {
  ShopService service;

  public ItemDto get() {
    return new ItemDto("x");
  }
}
