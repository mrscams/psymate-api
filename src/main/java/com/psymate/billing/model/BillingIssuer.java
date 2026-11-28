package com.psymate.billing.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Émetteur d'une facture (bloc {@code issuer} de L5.3) : identité du praticien (identity),
 * coordonnées, régime et mention de TVA, modalités de règlement. Pour un brouillon, ce sont les
 * réglages du moment ({@code BillingSettingsService#issuerOfTheMoment}) ; pour un document émis,
 * l'instantané rangé à l'émission ({@code invoice.issuer_snapshot}, M15-09), lu par {@link
 * #fromSnapshot}. L'instantané porte les clés de {@link #toSnapshot} (celles du bloc {@code issuer}
 * de la réponse, plus {@code vatRegime}). {@code invoicePrefix}, {@code dueTerm} et {@code
 * footerNote} sont les valeurs par défaut des réglages pour un nouveau brouillon et le prochain
 * numéro ; ils ne font pas partie de l'instantané.
 *
 * @param vatMention mention légale du régime (G Q92), null en régime assujetti
 * @param vatRateBp taux en points de base en régime assujetti, null sinon
 * @param bankDetailsShown coordonnées bancaires imprimées : réglage activé et IBAN enregistré
 */
public record BillingIssuer(
    String name,
    String title,
    String professionalNumber,
    String siret,
    String addressLines,
    String displayEmail,
    String displayPhone,
    String vatRegime,
    String vatMention,
    Integer vatRateBp,
    String vatNumber,
    String chequePayee,
    List<String> acceptedModes,
    boolean bankDetailsShown,
    String invoicePrefix,
    String dueTerm,
    String footerNote) {

  public BillingIssuer {
    acceptedModes = acceptedModes == null ? List.of() : List.copyOf(acceptedModes);
  }

  /**
   * Instantané à ranger à l'émission (M15-09) : clés du bloc {@code issuer} et {@code vatRegime}.
   */
  public Map<String, Object> toSnapshot() {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("name", name);
    s.put("title", title);
    s.put("professionalNumber", professionalNumber);
    s.put("siret", siret);
    s.put("addressLines", addressLines);
    s.put("displayEmail", displayEmail);
    s.put("displayPhone", displayPhone);
    s.put("vatRegime", vatRegime);
    s.put("vatMention", vatMention);
    s.put("vatRateBp", vatRateBp);
    s.put("vatNumber", vatNumber);
    s.put("chequePayee", chequePayee);
    s.put("acceptedModes", acceptedModes);
    s.put("bankDetailsShown", bankDetailsShown);
    return s;
  }

  /** Émetteur d'un document émis, lu dans son instantané ; une clé absente vaut null (ou faux). */
  public static BillingIssuer fromSnapshot(Map<String, Object> snapshot) {
    Map<String, Object> s = snapshot == null ? Map.of() : snapshot;
    List<String> modes = new ArrayList<>();
    if (s.get("acceptedModes") instanceof List<?> list) {
      for (Object mode : list) {
        if (mode instanceof String m) {
          modes.add(m);
        }
      }
    }
    return new BillingIssuer(
        text(s, "name"),
        text(s, "title"),
        text(s, "professionalNumber"),
        text(s, "siret"),
        text(s, "addressLines"),
        text(s, "displayEmail"),
        text(s, "displayPhone"),
        text(s, "vatRegime"),
        text(s, "vatMention"),
        s.get("vatRateBp") instanceof Number n ? n.intValue() : null,
        text(s, "vatNumber"),
        text(s, "chequePayee"),
        modes,
        Boolean.TRUE.equals(s.get("bankDetailsShown")),
        null,
        null,
        null);
  }

  private static String text(Map<String, Object> s, String key) {
    return s.get(key) instanceof String value ? value : null;
  }
}
