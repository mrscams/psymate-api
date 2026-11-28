package com.psymate.billing.service;

import java.util.List;

/**
 * Feuille d'une facture prête à rendre (M15-09, G Q93) : tous les textes déjà mis en forme, en
 * français, dans l'ordre de la maquette ({@code paper} des maquettes du web) : émetteur, titre et
 * numéro, destinataire, dates, lignes, totaux, mentions, tampon, pied. Aucun texte clinique : les
 * lignes portent une date et une désignation (G Q83). Le rendu ({@link InvoicePdfRenderer}) échappe
 * chaque texte.
 *
 * @param watermark filigrane en travers de chaque page (« Brouillon »), null sinon
 * @param issuerName nom du praticien
 * @param issuerLines titre, adresse, identifiants (SIRET, numéro professionnel, TVA)
 * @param title titre du document (« Facture », « Facture acquittée »)
 * @param number ligne du numéro (« N° F-2026-0001 », ou « N° attribué à l'émission »)
 * @param recipientHeading intitulé du bloc destinataire
 * @param recipientName nom du destinataire
 * @param recipientLines adresse du destinataire, ligne par ligne
 * @param meta dates (émission, échéance ou règlement)
 * @param columns en-têtes des colonnes des lignes (date, désignation, quantité, prix, montant)
 * @param rows lignes de la facture
 * @param totals totaux, le principal marqué {@code big}
 * @param legal mentions (TVA, modalités, coordonnées bancaires, mention de bas de page)
 * @param stamp tampon (« Acquittée »), null sinon
 * @param footer pied de page (nom, titre, contact)
 */
record InvoiceSheet(
    String watermark,
    String issuerName,
    List<String> issuerLines,
    String title,
    String number,
    String recipientHeading,
    String recipientName,
    List<String> recipientLines,
    List<Meta> meta,
    List<String> columns,
    List<Row> rows,
    List<Total> totals,
    List<String> legal,
    String stamp,
    String footer) {

  InvoiceSheet {
    issuerLines = List.copyOf(issuerLines);
    recipientLines = List.copyOf(recipientLines);
    meta = List.copyOf(meta);
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
    totals = List.copyOf(totals);
    legal = List.copyOf(legal);
  }

  /** Une date de la feuille : intitulé et valeur. */
  record Meta(String label, String value) {}

  /** Une ligne : date (vide sans date), désignation, quantité, prix unitaire, montant. */
  record Row(String date, String label, String quantity, String unitPrice, String amount) {}

  /** Un total : intitulé, montant, mis en avant ou non. */
  record Total(String label, String amount, boolean big) {}
}
