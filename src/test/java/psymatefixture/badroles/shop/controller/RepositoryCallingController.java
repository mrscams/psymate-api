package psymatefixture.badroles.shop.controller;

import org.springframework.web.bind.annotation.RestController;
import psymatefixture.badroles.shop.repository.ShopRepository;

/** Violation : un controleur n'appelle jamais un repository. */
@RestController
public class RepositoryCallingController {
  ShopRepository repository;
}
