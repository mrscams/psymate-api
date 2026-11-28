package com.psymate.billing.service;

import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingIssuer;
import com.psymate.billing.model.Invoice;
import com.psymate.billing.model.InvoiceLine;
import com.psymate.billing.model.InvoiceTotals;
import com.psymate.billing.model.Payment;
import com.psymate.billing.repository.InvoiceLineRepository;
import com.psymate.billing.repository.InvoiceRepository;
import com.psymate.billing.repository.PaymentRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.shared.problem.ProblemException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PDF des factures (M15-09, G Q93). Le PDF d'une facture émise est rendu une fois, à l'émission,
 * dans sa transaction, et rangé chiffré par {@link EncryptedStorageFacade} sous {@code
 * billing/invoice/{id}.pdf} : il est ensuite servi tel quel, octet pour octet, même si les réglages
 * changent. Rendus à la demande, jamais rangés : le brouillon (filigrane « Brouillon », émetteur et
 * TVA des réglages du moment) et la facture acquittée ({@code variant=receipted} : titre « Facture
 * acquittée », tampon « Acquittée », paiements ; 409 INVOICE_NOT_PAID si elle n'est pas entièrement
 * réglée).
 *
 * <p>La feuille est toujours en français (textes fixes {@code billing.pdf.*} de messages_fr),
 * quelle que soit la langue servie ; elle ne porte aucune donnée du dossier hormis les dates et les
 * désignations des lignes (G Q83). Montants en euros à virgule, espaces insécables.
 */
@Service
public class InvoicePdfService {

  private static final Logger LOG = LoggerFactory.getLogger(InvoicePdfService.class);

  /** Préfixe des clés de stockage des PDF de facture (G Q93). */
  static final String KEY_PREFIX = "billing/invoice/";

  /** Variante « facture acquittée » de {@code GET /api/invoices/{id}/pdf}. */
  public static final String RECEIPTED = "receipted";

  /** Nom du fichier d'un brouillon, qui n'a pas de numéro. */
  public static final String DRAFT_FILENAME = "facture-brouillon.pdf";

  /** Suffixe du fichier d'une facture acquittée. */
  static final String RECEIPTED_SUFFIX = "-acquittee";

  /** Espace insécable (dans le jeu WinAnsi des polices PDF intégrées). */
  static final char NBSP = ' ';

  private static final DateTimeFormatter LONG_DATE =
      DateTimeFormatter.ofPattern("d MMMM uuuu", ServedLanguage.FR.locale());

  private static final DateTimeFormatter SHORT_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);

  /** Nature d'une feuille. */
  enum Variant {
    DRAFT,
    ISSUED,
    RECEIPTED
  }

  /** Fichier PDF servi : nom et octets. */
  public record PdfFile(String filename, byte[] content) {}

  private final InvoiceRepository invoices;
  private final InvoiceLineRepository lines;
  private final PaymentRepository payments;
  private final BillingSettingsService settings;
  private final EncryptedStorageFacade storage;
  private final InvoicePdfRenderer renderer;
  private final BillingTexts texts;

  public InvoicePdfService(
      InvoiceRepository invoices,
      InvoiceLineRepository lines,
      PaymentRepository payments,
      BillingSettingsService settings,
      EncryptedStorageFacade storage,
      InvoicePdfRenderer renderer,
      BillingTexts texts) {
    this.invoices = invoices;
    this.lines = lines;
    this.payments = payments;
    this.settings = settings;
    this.storage = storage;
    this.renderer = renderer;
    this.texts = texts;
  }

  /** Clé de stockage du PDF d'une facture ou d'un avoir. */
  public static String storageKey(UUID invoiceId) {
    return KEY_PREFIX + invoiceId + ".pdf";
  }

  // ---- émission

  /**
   * PDF d'une facture qui vient d'être émise (appelé dans la transaction d'émission) : numéro,
   * émetteur de l'instantané, montants figés, paiement enregistré à l'émission s'il y en a un.
   */
  byte[] renderIssued(
      Invoice invoice, List<InvoiceLine> rows, BillingIssuer issuer, List<Payment> paid) {
    return renderer.render(
        sheet(
            invoice,
            rows,
            issuer,
            frozenTotals(invoice),
            paid,
            Variant.ISSUED,
            iban(invoice.getPractitionerId(), issuer)));
  }

  /**
   * Range le PDF chiffré. L'effacement est inscrit avant l'écriture : si la transaction est annulée
   * ({@code STATUS_ROLLED_BACK}), le fichier, même écrit à moitié, est effacé ; aucun PDF ne survit
   * à une émission qui n'a pas eu lieu. Une issue inconnue ({@code STATUS_UNKNOWN}) ne l'efface pas
   * : une facture peut-être émise ne perd jamais son fichier. Une écriture qui échoue devient 500
   * PDF_UNAVAILABLE (la transaction est annulée).
   */
  void store(String key, byte[] pdf) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                storage.delete(key);
              }
            }
          });
    }
    try {
      storage.put(key, pdf);
    } catch (RuntimeException e) {
      // Ni clé ni contenu : seule la classe de l'erreur (G Q95).
      LOG.error("Invoice PDF storage failed: {}", e.getClass().getName());
      throw BillingException.pdfUnavailable();
    }
  }

  // ---- lecture

  /**
   * PDF d'une facture du praticien (404 INVOICE_NOT_FOUND sinon). {@code variant} absent : le
   * brouillon rendu à la demande, ou le fichier rangé d'un document émis ; {@code receipted} : la
   * facture acquittée, rendue à la demande (409 INVOICE_NOT_PAID si elle n'est pas réglée) ; autre
   * valeur : 422 variant INVALID_VALUE.
   */
  @Transactional(readOnly = true)
  public PdfFile pdf(UUID id, String variant) {
    CurrentPractitioner me = me();
    Invoice invoice =
        invoices
            .findByIdAndPractitionerId(id, me.id())
            .orElseThrow(BillingException::invoiceNotFound);
    boolean receipted = receipted(variant);
    if (receipted) {
      if (!Invoice.KIND_INVOICE.equals(invoice.getKind())
          || !"paid"
              .equals(
                  InvoiceTotals.status(
                      invoice.getKind(),
                      invoice.getStatus(),
                      invoice.getTotalCents(),
                      invoice.getPaidCents(),
                      invoice.getCreditedCents()))) {
        throw BillingException.invoiceNotPaid();
      }
      byte[] pdf =
          renderer.render(
              sheet(
                  invoice,
                  lines.findByInvoiceIdOrderByPosition(invoice.getId()),
                  BillingIssuer.fromSnapshot(invoice.getIssuerSnapshot()),
                  frozenTotals(invoice),
                  payments.findByInvoiceIdOrderByReceivedOnAscCreatedAtAscIdAsc(invoice.getId()),
                  Variant.RECEIPTED,
                  null));
      return new PdfFile(invoice.getNumber() + RECEIPTED_SUFFIX + ".pdf", pdf);
    }
    if (invoice.isDraft()) {
      BillingIssuer issuer = settings.issuerOfTheMoment(me);
      List<InvoiceLine> rows = lines.findByInvoiceIdOrderByPosition(invoice.getId());
      InvoiceTotals totals =
          InvoiceTotals.of(
              rows.stream().map(InvoiceLine::getAmountCents).toList(),
              issuer.vatRegime(),
              issuer.vatRateBp());
      byte[] pdf =
          renderer.render(
              sheet(
                  invoice,
                  rows,
                  issuer,
                  totals,
                  List.of(),
                  Variant.DRAFT,
                  iban(invoice.getPractitionerId(), issuer)));
      return new PdfFile(DRAFT_FILENAME, pdf);
    }
    String key = invoice.getPdfStorageKey();
    if (key == null || !storage.exists(key)) {
      throw BillingException.pdfUnavailable();
    }
    try {
      return new PdfFile(invoice.getNumber() + ".pdf", storage.get(key));
    } catch (RuntimeException e) {
      // Fichier disparu entre-temps ou indéchiffrable : 500, sans clé ni contenu au journal.
      LOG.error("Invoice PDF read failed: {}", e.getClass().getName());
      throw BillingException.pdfUnavailable();
    }
  }

  /** {@code variant} : absent ou vide, ou {@code receipted} ; 422 variant INVALID_VALUE sinon. */
  static boolean receipted(String variant) {
    if (variant == null || variant.isBlank()) {
      return false;
    }
    if (RECEIPTED.equals(variant)) {
      return true;
    }
    throw BillingException.invalidField("variant", "INVALID_VALUE");
  }

  // ---- feuille

  /**
   * Feuille d'une facture (maquette {@code paper}) : émetteur, titre et numéro, destinataire,
   * dates, lignes, totaux, mentions, tampon, pied. Pure hormis les textes fixes : testée
   * unitairement.
   */
  InvoiceSheet sheet(
      Invoice invoice,
      List<InvoiceLine> rows,
      BillingIssuer issuer,
      InvoiceTotals totals,
      List<Payment> allPayments,
      Variant variant,
      String iban) {
    boolean liable = InvoiceTotals.LIABLE.equals(issuer.vatRegime());
    List<Payment> received =
        allPayments.stream()
            .filter(p -> Payment.KIND_PAYMENT.equals(p.getKind()) && !p.isCancelled())
            .toList();

    List<String> issuerLines = new ArrayList<>();
    addIfPresent(issuerLines, issuer.title());
    issuerLines.addAll(splitLines(issuer.addressLines()));
    if (issuer.siret() != null) {
      issuerLines.add(text("siret").replace("{siret}", siret(issuer.siret())));
    }
    if (issuer.professionalNumber() != null && !issuer.professionalNumber().isBlank()) {
      issuerLines.add(
          text("professionalNumber").replace("{number}", issuer.professionalNumber().strip()));
    }
    if (liable && issuer.vatNumber() != null) {
      issuerLines.add(text("vatNumber").replace("{number}", issuer.vatNumber()));
    }

    String title = text(variant == Variant.RECEIPTED ? "title.receipted" : "title.invoice");
    String number =
        invoice.getNumber() == null
            ? text("number.pending")
            : text("number").replace("{number}", invoice.getNumber());

    List<InvoiceSheet.Meta> meta = new ArrayList<>();
    meta.add(new InvoiceSheet.Meta(text("issueDate"), LONG_DATE.format(invoice.getIssueDate())));
    if (variant == Variant.RECEIPTED) {
      received.stream()
          .map(Payment::getReceivedOn)
          .max(Comparator.naturalOrder())
          .ifPresent(day -> meta.add(new InvoiceSheet.Meta(text("paidOn"), LONG_DATE.format(day))));
    } else {
      LocalDate dueOn =
          InvoiceTotals.dueOn(invoice.getDueTerm(), invoice.getIssueDate(), invoice.getDueOn());
      String due =
          "on_receipt".equals(invoice.getDueTerm())
              ? text("dueOn.onReceipt")
              : LONG_DATE.format(dueOn);
      meta.add(new InvoiceSheet.Meta(text("dueOn"), due));
    }

    List<String> columns =
        List.of(
            text("column.date"),
            text("column.label"),
            text("column.quantity"),
            text(liable ? "column.unitPrice.ht" : "column.unitPrice"),
            text(liable ? "column.amount.ht" : "column.amount"));
    List<InvoiceSheet.Row> sheetRows =
        rows.stream()
            .map(
                l ->
                    new InvoiceSheet.Row(
                        l.getOccurredOn() == null ? "" : SHORT_DATE.format(l.getOccurredOn()),
                        l.getLabel(),
                        Integer.toString(l.getQuantity()),
                        euros(l.getUnitPriceCents()),
                        euros(l.getAmountCents())))
            .toList();

    List<InvoiceSheet.Total> sheetTotals = new ArrayList<>();
    if (liable) {
      sheetTotals.add(new InvoiceSheet.Total(text("subtotal.ht"), euros(totals.subtotal()), false));
      sheetTotals.add(
          new InvoiceSheet.Total(
              text("vat").replace("{rate}", rate(issuer.vatRateBp())), euros(totals.vat()), false));
      sheetTotals.add(new InvoiceSheet.Total(text("total.ttc"), euros(totals.total()), true));
    } else {
      sheetTotals.add(new InvoiceSheet.Total(text("total"), euros(totals.total()), true));
    }
    if (variant == Variant.RECEIPTED) {
      for (Payment p : received) {
        sheetTotals.add(
            new InvoiceSheet.Total(
                text("paidBy")
                    .replace("{mode}", mode(p.getMode()))
                    .replace("{date}", SHORT_DATE.format(p.getReceivedOn())),
                euros(p.getAmountCents()),
                false));
      }
    } else if (!received.isEmpty()) {
      long paid = 0L;
      for (Payment p : received) {
        paid = Math.addExact(paid, p.getAmountCents());
        sheetTotals.add(
            new InvoiceSheet.Total(
                text("alreadyPaid")
                    .replace("{mode}", mode(p.getMode()))
                    .replace("{date}", SHORT_DATE.format(p.getReceivedOn())),
                euros(-p.getAmountCents()),
                false));
      }
      sheetTotals.add(
          new InvoiceSheet.Total(
              text("balanceDue"),
              euros(InvoiceTotals.balanceDue(totals.total(), paid, invoice.getCreditedCents())),
              false));
    }

    List<String> legal = new ArrayList<>();
    if (issuer.vatMention() != null && !issuer.vatMention().isBlank()) {
      legal.add(sentence(issuer.vatMention()));
    }
    if (variant != Variant.RECEIPTED) {
      String modes = modes(issuer.acceptedModes());
      if (modes != null) {
        boolean payee =
            issuer.acceptedModes().contains("cheque")
                && issuer.chequePayee() != null
                && !issuer.chequePayee().isBlank();
        legal.add(
            payee
                ? text("modes.payee")
                    .replace("{modes}", modes)
                    .replace("{payee}", issuer.chequePayee().strip())
                : text("modes").replace("{modes}", modes));
      }
      if (issuer.bankDetailsShown() && iban != null) {
        legal.add(text("iban").replace("{iban}", groupsOfFour(iban)));
      }
    }
    if (invoice.getFooterNote() != null && !invoice.getFooterNote().isBlank()) {
      legal.add(invoice.getFooterNote().strip());
    }

    String footer =
        String.join(
            " · ",
            Stream.of(issuer.name(), issuer.title(), issuer.displayEmail(), issuer.displayPhone())
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .toList());

    return new InvoiceSheet(
        variant == Variant.DRAFT ? text("watermark.draft") : null,
        issuer.name() == null ? "" : issuer.name(),
        issuerLines,
        title,
        number,
        text("recipient"),
        invoice.getRecipientName() == null ? "" : invoice.getRecipientName(),
        splitLines(invoice.getRecipientAddress()),
        meta,
        columns,
        sheetRows,
        sheetTotals,
        legal,
        variant == Variant.RECEIPTED ? text("stamp.receipted") : null,
        footer);
  }

  // ---- mise en forme (pure, testée unitairement)

  /** Montant en euros à la française : {@code 1 234,50 €} (espaces insécables), signe moins. */
  static String euros(long cents) {
    long abs = Math.abs(cents);
    String units = Long.toString(abs / 100);
    StringBuilder grouped = new StringBuilder();
    for (int i = 0; i < units.length(); i++) {
      if (i > 0 && (units.length() - i) % 3 == 0) {
        grouped.append(NBSP);
      }
      grouped.append(units.charAt(i));
    }
    return (cents < 0 ? "-" : "")
        + grouped
        + ","
        + String.format(Locale.ROOT, "%02d", abs % 100)
        + NBSP
        + "€";
  }

  /**
   * Taux en points de base vers un pourcentage français : 2000 → {@code 20 %}, 550 → {@code 5,5 %}.
   */
  static String rate(Integer basisPoints) {
    int bp = basisPoints == null ? 0 : basisPoints;
    String whole = Integer.toString(bp / 100);
    int fraction = bp % 100;
    String decimals =
        fraction == 0
            ? ""
            : ","
                + (fraction % 10 == 0
                    ? Integer.toString(fraction / 10)
                    : String.format(Locale.ROOT, "%02d", fraction));
    return whole + decimals + NBSP + "%";
  }

  /** SIRET groupé comme sur un extrait Kbis : {@code 732 829 320 00074}. */
  static String siret(String siret) {
    String s = siret.strip();
    if (s.length() != 14) {
      return s;
    }
    return s.substring(0, 3)
        + " "
        + s.substring(3, 6)
        + " "
        + s.substring(6, 9)
        + " "
        + s.substring(9);
  }

  /** IBAN par groupes de quatre caractères. */
  static String groupsOfFour(String iban) {
    String compact = iban.replaceAll("\\s+", "");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < compact.length(); i += 4) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(compact, i, Math.min(i + 4, compact.length()));
    }
    return sb.toString();
  }

  /** Modes acceptés en toutes lettres : « virement, chèque ou espèces » ; null sans mode. */
  private String modes(List<String> accepted) {
    List<String> labels = accepted.stream().map(this::mode).toList();
    if (labels.isEmpty()) {
      return null;
    }
    if (labels.size() == 1) {
      return labels.getFirst();
    }
    return String.join(", ", labels.subList(0, labels.size() - 1))
        + " "
        + text("modes.or")
        + " "
        + labels.getLast();
  }

  private String mode(String mode) {
    return text("mode." + mode);
  }

  private String text(String key) {
    return texts.pdf(key);
  }

  /** Une mention se termine par un point. */
  private static String sentence(String text) {
    String t = text.strip();
    return t.endsWith(".") ? t : t + ".";
  }

  private static List<String> splitLines(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return Stream.of(text.split("\\R")).map(String::strip).filter(l -> !l.isEmpty()).toList();
  }

  private static void addIfPresent(List<String> target, String value) {
    if (value != null && !value.isBlank()) {
      target.add(value.strip());
    }
  }

  private static InvoiceTotals frozenTotals(Invoice invoice) {
    return new InvoiceTotals(
        invoice.getSubtotalCents(), invoice.getVatCents(), invoice.getTotalCents());
  }

  /**
   * IBAN à imprimer (réglage activé et IBAN enregistré, G Q95 : lu dans le stockage chiffré, jamais
   * en base), null sinon ; un fichier illisible vaut une absence, sans journal.
   */
  private String iban(UUID practitionerId, BillingIssuer issuer) {
    if (!issuer.bankDetailsShown()) {
      return null;
    }
    String key = BillingIbanService.storageKey(practitionerId);
    try {
      return storage.exists(key) ? new String(storage.get(key), StandardCharsets.UTF_8) : null;
    } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
      // Fichier absent, clé refusée ou contenu indéchiffrable : pas d'IBAN imprimé.
      return null;
    }
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
