package com.psymate.billing.model;

import com.psymate.patients.api.PatientBillingView;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Destinataire par défaut d'une facture (L5.3 {@code recipient}) : le tiers payeur du profil de
 * facturation s'il est renseigné (nom et adresse du tiers), sinon le patient (prénom et nom, à
 * défaut son nom affiché) avec l'adresse de facturation de son profil. C'est une donnée de la
 * facture, posée à la création du brouillon (ou au changement de patient), pas une donnée clinique.
 */
public final class InvoiceRecipients {

  /** Longueurs de {@code invoice.recipient_name} et {@code recipient_address} (V18). */
  static final int NAME_MAX = 200;

  static final int ADDRESS_MAX = 300;

  /** Nom et adresse d'un destinataire. */
  public record Recipient(String name, String addressLines) {}

  private InvoiceRecipients() {}

  /** Prénom et nom du patient s'ils sont renseignés, sinon son nom affiché. */
  public static String fullName(PatientBillingView patient) {
    String full =
        Stream.of(patient.firstName(), patient.lastName())
            .filter(Objects::nonNull)
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(" "));
    return full.isEmpty() ? patient.displayName() : full;
  }

  /** Destinataire par défaut ; {@code profile} null si le patient n'a pas de profil. */
  public static Recipient defaultFor(PatientBillingView patient, BillingPatient profile) {
    if (profile != null && profile.getThirdPartyName() != null) {
      return new Recipient(
          cut(profile.getThirdPartyName(), NAME_MAX),
          cut(profile.getThirdPartyAddress(), ADDRESS_MAX));
    }
    return new Recipient(
        cut(fullName(patient), NAME_MAX),
        profile == null ? null : cut(profile.getAddressLines(), ADDRESS_MAX));
  }

  private static String cut(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
