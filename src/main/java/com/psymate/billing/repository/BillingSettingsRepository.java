package com.psymate.billing.repository;

import com.psymate.billing.model.BillingSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès JPA à {@code billing_settings} (V18, G Q32), une ligne par praticien, clé = praticien :
 * toute lecture est donc filtrée par praticien. Verrou consultatif par le fragment {@link
 * BillingNativeQueries} (G Q38).
 */
public interface BillingSettingsRepository
    extends JpaRepository<BillingSettings, UUID>, BillingNativeQueries {}
