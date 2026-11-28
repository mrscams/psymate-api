package com.psymate.billing.repository;

import com.psymate.billing.model.BillingPatient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès JPA à {@code billing_patient} (V18, G Q32), une ligne par patient. L'appelant vérifie la
 * propriété du patient (garde des patients) avant toute lecture ou écriture.
 */
public interface BillingPatientRepository extends JpaRepository<BillingPatient, UUID> {}
