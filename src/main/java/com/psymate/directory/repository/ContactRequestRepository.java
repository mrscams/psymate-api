package com.psymate.directory.repository;

import com.psymate.directory.model.ContactRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès JPA à contact_request (trace technique 30 jours). Jamais de lien vers un dossier patient.
 */
public interface ContactRequestRepository extends JpaRepository<ContactRequest, UUID> {

  /**
   * DELETE physique voulu (E2 : table technique) des lignes dont purge_after est dépassé. Appelé
   * par la tâche planifiée sans transaction : {@code @Transactional} explicite, car une méthode
   * {@code @Modifying} déclarée sur l'interface hérite sinon d'une transaction en lecture seule.
   */
  @Transactional
  @Modifying
  @Query("delete from ContactRequest c where c.purgeAfter < current_timestamp")
  int deleteDue();
}
