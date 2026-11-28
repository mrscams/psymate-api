package com.psymate.billing.dto;

import com.psymate.billing.model.BillingSettings;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Réglages de facturation vers DTO REST (écrit à la main, G Q46) : la réponse mêle la ligne {@code
 * billing_settings} (ou ses valeurs par défaut) et des valeurs calculées par le service (identité
 * lue dans identity, adresse et e-mail proposés, mention de TVA, prochains numéros, verrou, modèles
 * effectifs, {@code missing}), ce que MapStruct n'exprime pas simplement. L'IBAN n'y figure jamais
 * : seulement {@code ibanLast4}.
 */
@Component
public class BillingSettingsAssembler {

  /**
   * DTO complet. {@code address} et {@code email} sont les valeurs effectives (enregistrées ou
   * proposées) ; {@code templates} : {@code {invoice|reminder: {subject, body}}} complets.
   */
  public BillingSettingsDto toDto(
      BillingSettings s,
      String name,
      String title,
      String professionalNumber,
      String address,
      String email,
      String vatMention,
      String nextInvoiceNumber,
      String nextCreditNoteNumber,
      boolean locked,
      Map<String, Map<String, String>> templates,
      List<String> missing) {
    return new BillingSettingsDto(
        new BillingIssuerIdentityDto(name, title, professionalNumber),
        s.getSiret(),
        address,
        email,
        s.getDisplayPhone(),
        new BillingVatDto(s.getVatRegime(), s.getVatRateBp(), s.getVatNumber(), vatMention),
        new BillingNumberingDto(
            s.getInvoicePrefix(),
            s.getCreditNotePrefix(),
            nextInvoiceNumber,
            nextCreditNoteNumber,
            locked),
        new BillingPaymentTermsDto(
            s.getDueTerm(),
            s.getChequePayee(),
            s.getAcceptedModes(),
            s.isShowBankDetails(),
            s.getIbanLast4()),
        s.getFooterNote(),
        new BillingEmailTemplatesDto(
            template(templates, "invoice"), template(templates, "reminder")),
        missing);
  }

  private static BillingEmailTemplateDto template(
      Map<String, Map<String, String>> templates, String kind) {
    Map<String, String> parts = templates.getOrDefault(kind, Map.of());
    return new BillingEmailTemplateDto(parts.get("subject"), parts.get("body"));
  }
}
