package com.psymate.identity.repository;

import com.psymate.identity.model.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accès JPA à {@code tenant}. */
public interface TenantJpaRepository extends JpaRepository<Tenant, UUID> {}
