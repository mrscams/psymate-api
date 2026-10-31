package com.psymate.patients;

import com.psymate.patients.PatientDtos.CreatePatientRequest;
import java.util.UUID;
import org.springframework.context.ApplicationContext;

/**
 * Passerelle de test vers {@code PatientService} (package-private) pour les tests transverses :
 * crée un patient par le chemin applicatif réel (donc avec l'événement PatientCreated), sans passer
 * par REST. Le praticien courant est lu dans le SecurityContext, comme en production.
 */
public final class PatientTestSupport {

  private PatientTestSupport() {}

  public static UUID createPatient(ApplicationContext context, String displayName) {
    PatientService service = context.getBean(PatientService.class);
    return service
        .create(
            new CreatePatientRequest(
                displayName, null, null, null, null, null, null, null, null, null, null))
        .id();
  }
}
