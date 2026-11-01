package com.psymate.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accès JPA à {@code document_access_log} : insertion seulement (table append-only). */
public interface DocumentAccessLogJpaRepository extends JpaRepository<DocumentAccessLog, UUID> {}
