package psymatefixture.badroles.shop.service;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/** Violation : un repository Spring Data hors de repository/. */
public interface StrayJpaRepository extends CrudRepository<StrayEntity, UUID> {}
