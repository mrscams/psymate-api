package psymatefixture.badroles.shop.config;

import psymatefixture.badroles.shop.repository.ShopRepository;

/** Violation : seul service appelle repository. */
public class RepositoryBackedConfig {
  ShopRepository repository;
}
