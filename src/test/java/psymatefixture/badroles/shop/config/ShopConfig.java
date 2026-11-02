package psymatefixture.badroles.shop.config;

import org.springframework.context.annotation.Configuration;
import psymatefixture.badroles.shop.service.ShopService;

/** Correct : config peut dependre de service. */
@Configuration
public class ShopConfig {
  ShopService service;
}
