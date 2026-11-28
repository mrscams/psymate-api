package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.billing.model.InvoiceRecipients.Recipient;
import com.psymate.patients.api.PatientBillingView;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Destinataire par défaut d'une facture : tiers payeur, sinon patient. */
class InvoiceRecipientsTest {

  private static final UUID PRACTITIONER = UUID.randomUUID();
  private static final UUID TENANT = UUID.randomUUID();

  private static PatientBillingView patient(String first, String last, String display) {
    return new PatientBillingView(
        UUID.randomUUID(), PRACTITIONER, display, first, last, null, false);
  }

  private static BillingPatient profile(
      PatientBillingView patient, String address, String thirdName, String thirdAddress) {
    BillingPatient p = new BillingPatient(patient.id(), TENANT, PRACTITIONER);
    p.replace(TENANT, PRACTITIONER, null, address, thirdName, thirdAddress, OffsetDateTime.now());
    return p;
  }

  @Test
  void theRecipientIsThePatientWithTheProfileAddress() {
    PatientBillingView patient = patient("Hélène", "Roux", "Hélène R.");
    assertThat(InvoiceRecipients.defaultFor(patient, profile(patient, "1 rue A", null, null)))
        .isEqualTo(new Recipient("Hélène Roux", "1 rue A"));
    assertThat(InvoiceRecipients.defaultFor(patient, null))
        .isEqualTo(new Recipient("Hélène Roux", null));
  }

  @Test
  void withoutFirstOrLastNameTheDisplayNameIsUsed() {
    PatientBillingView patient = patient(null, " ", "Théo B.");
    assertThat(InvoiceRecipients.fullName(patient)).isEqualTo("Théo B.");
    assertThat(InvoiceRecipients.fullName(patient(null, "Roux", "H. R."))).isEqualTo("Roux");
  }

  @Test
  void aThirdPartyPayerBecomesTheRecipient() {
    PatientBillingView patient = patient("Léa", "Martin", "Léa M.");
    assertThat(
            InvoiceRecipients.defaultFor(
                patient, profile(patient, "1 rue A", "Mutuelle X", "2 avenue B")))
        .isEqualTo(new Recipient("Mutuelle X", "2 avenue B"));
  }
}
