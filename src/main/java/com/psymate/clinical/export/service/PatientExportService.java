package com.psymate.clinical.export.service;

import com.psymate.clinical.api.EntryLinksReadPort;
import com.psymate.clinical.api.LinkedEntryView;
import com.psymate.clinical.export.model.ExportDocument;
import com.psymate.clinical.export.model.ExportDocument.EntryBlock;
import com.psymate.clinical.export.model.ExportDocument.SessionBlock;
import com.psymate.clinical.export.model.ExportFormat;
import com.psymate.clinical.export.model.ExportedFile;
import com.psymate.clinical.export.repository.ExportRepository;
import com.psymate.clinical.export.repository.ExportRepository.EntryRow;
import com.psymate.clinical.export.repository.ExportRepository.SessionRow;
import com.psymate.patients.api.PatientFacade;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import com.psymate.vocabulary.api.TagFacade;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Export d'un dossier patient (F14, M6-05) : construit le modèle {@link ExportDocument} puis le
 * rend en Markdown ou en PDF (HTML thème clair). Seules les entrées {@code visibility = 'clinical'}
 * non archivées sortent, avec le texte de leur version courante ; un lien vers une entrée qui n'est
 * pas exportée est omis, pour ne jamais révéler le titre d'une entrée privée.
 *
 * <p>Aucun contrôle d'accès ici : l'appelant a déjà vérifié que le patient est au praticien (garde
 * web {@code PatientOwnershipGuard}, ou liste {@code PatientFacade#ownedBy} du travail d'export du
 * compte). En revanche les catalogues d'émotions et de tags sont ceux du praticien courant : un
 * principal doit être présent dans le contexte de sécurité du thread (le travail d'export du compte
 * le pose).
 */
@Service
public class PatientExportService {

  private static final int LINK_TITLE_MAX = 80;

  private final ExportRepository repository;
  private final PatientFacade patients;
  private final EmotionCatalog emotions;
  private final TagFacade tags;
  private final LinkTypeCatalog linkTypes;
  private final ObjectProvider<EntryLinksReadPort> links;
  private final MarkdownRenderer markdown;
  private final HtmlRenderer html;
  private final PdfRenderer pdf;
  private final TransactionTemplate tx;
  private final Clock clock;

  PatientExportService(
      ExportRepository repository,
      PatientFacade patients,
      EmotionCatalog emotions,
      TagFacade tags,
      LinkTypeCatalog linkTypes,
      ObjectProvider<EntryLinksReadPort> links,
      MarkdownRenderer markdown,
      HtmlRenderer html,
      PdfRenderer pdf,
      TransactionTemplate tx,
      Clock clock) {
    this.repository = repository;
    this.patients = patients;
    this.emotions = emotions;
    this.tags = tags;
    this.linkTypes = linkTypes;
    this.links = links;
    this.markdown = markdown;
    this.html = html;
    this.pdf = pdf;
    this.tx = tx;
    this.clock = clock;
  }

  // ---- API publique

  /** Export Markdown en français (langue par défaut du produit). */
  public String markdown(UUID patientId) {
    return markdown.render(build(patientId, Locale.FRENCH), ExportTexts.of(Locale.FRENCH));
  }

  /** Fichier d'export du patient dans le format et la langue demandés. */
  public ExportedFile export(UUID patientId, ExportFormat format, Locale locale) {
    return render(build(patientId, locale), format, locale);
  }

  /** Rend un modèle déjà construit (le travail d'export du compte rend deux formats). */
  public ExportedFile render(ExportDocument document, ExportFormat format, Locale locale) {
    ExportTexts texts = ExportTexts.of(locale);
    byte[] content =
        switch (format) {
          case MD -> markdown.render(document, texts).getBytes(StandardCharsets.UTF_8);
          case PDF -> pdf.render(html.render(document, texts), texts);
        };
    return new ExportedFile(filename(document, format), format.contentType(), content);
  }

  /** Nom de fichier : export-nom-yyyyMMdd.md ou .pdf. */
  static String filename(ExportDocument document, ExportFormat format) {
    return "export-"
        + slug(document.patientName())
        + "-"
        + DateTimeFormatter.BASIC_ISO_DATE.format(document.exportedOn())
        + "."
        + format.code();
  }

  /** Minuscules sans accents, tirets entre les mots, 40 caractères au plus ; « patient » à vide. */
  static String slug(String name) {
    String ascii =
        Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    if (ascii.length() > 40) {
      ascii = ascii.substring(0, 40).replaceAll("-+$", "");
    }
    return ascii.isEmpty() ? "patient" : ascii;
  }

  /** Construit le modèle du dossier, dans une transaction en lecture seule. */
  public ExportDocument build(UUID patientId, Locale locale) {
    ExportTexts texts = ExportTexts.of(locale);
    return tx.execute(status -> doBuild(patientId, texts));
  }

  // ---- construction

  private ExportDocument doBuild(UUID patientId, ExportTexts texts) {
    String name =
        patients
            .displayName(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
    LocalDate birth = patients.birthDate(patientId).orElse(null);
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    Integer age = birth == null ? null : Period.between(birth, today).getYears();

    List<SessionRow> sessionRows = repository.sessions(patientId);
    List<EntryRow> entryRows = repository.entries(patientId);
    Map<UUID, List<UUID>> tagIdsByEntry = repository.tagIds(patientId);
    Map<UUID, String> tagLabels =
        tags.labelsByIds(tagIdsByEntry.values().stream().flatMap(List::stream).distinct().toList());
    Map<String, String> emotionLabels = emotionLabels(texts);

    // Titres des seules entrées exportées : un lien vers autre chose est omis.
    Map<UUID, String> titles = new HashMap<>();
    Set<UUID> sessionIds = new HashSet<>();
    for (SessionRow s : sessionRows) {
      sessionIds.add(s.id());
      titles.put(s.id(), linkTitle(s.title(), s.body(), sessionHeading(s, texts)));
    }
    for (EntryRow e : entryRows) {
      titles.put(e.id(), linkTitle(e.title(), e.body(), typeLabel(e.type(), texts)));
    }

    Map<UUID, List<EntryBlock>> bySession = new LinkedHashMap<>();
    List<EntryBlock> unassigned = new ArrayList<>();
    for (EntryRow e : entryRows) {
      EntryBlock block = entryBlock(e, texts, emotionLabels, tagIdsByEntry, tagLabels, titles);
      if (e.sessionId() != null && sessionIds.contains(e.sessionId())) {
        bySession.computeIfAbsent(e.sessionId(), k -> new ArrayList<>()).add(block);
      } else {
        unassigned.add(block);
      }
    }
    List<SessionBlock> sessions = new ArrayList<>();
    for (SessionRow s : sessionRows) {
      sessions.add(
          new SessionBlock(
              s.kind(),
              sessionHeading(s, texts),
              sessionDetails(s, texts),
              blankToNull(s.title()),
              blankToNull(s.body()),
              bySession.getOrDefault(s.id(), List.of())));
    }
    return new ExportDocument(name, birth, age, today, sessions, unassigned);
  }

  private EntryBlock entryBlock(
      EntryRow e,
      ExportTexts texts,
      Map<String, String> emotionLabels,
      Map<UUID, List<UUID>> tagIdsByEntry,
      Map<UUID, String> tagLabels,
      Map<UUID, String> titles) {
    String feeling = null;
    if (e.emotionCode() != null) {
      feeling =
          texts.get(
              "entry.feeling",
              emotionLabels.getOrDefault(e.emotionCode(), e.emotionCode()),
              String.valueOf(e.intensity()));
    }
    String medication = e.molecule() == null ? null : medicationLine(e, texts);
    List<String> tagList =
        tagIdsByEntry.getOrDefault(e.id(), List.of()).stream()
            .map(tagLabels::get)
            .filter(l -> l != null && !l.isBlank())
            .sorted(Comparator.comparing(l -> l.toLowerCase(Locale.ROOT)))
            .toList();
    return new EntryBlock(
        e.type(),
        typeLabel(e.type(), texts),
        ExportDates.label(e.time(), texts),
        blankToNull(e.title()),
        blankToNull(e.body()),
        e.draft(),
        feeling,
        medication,
        tagList,
        linkSentences(e.id(), titles, texts));
  }

  /** Une phrase par lien accepté dont l'autre entrée est exportée ; libellé inverse si cible. */
  private List<String> linkSentences(UUID entryId, Map<UUID, String> titles, ExportTexts texts) {
    EntryLinksReadPort port = links.getIfAvailable();
    if (port == null) {
      return List.of();
    }
    boolean english = Locale.ENGLISH.getLanguage().equals(texts.locale().getLanguage());
    List<String> sentences = new ArrayList<>();
    for (LinkedEntryView link : port.acceptedLinksOf(entryId)) {
      boolean incoming = link.targetId().equals(entryId);
      UUID other = incoming ? link.sourceId() : link.targetId();
      String otherTitle = titles.get(other);
      if (otherTitle == null || other.equals(entryId)) {
        continue;
      }
      sentences.add(linkLabel(link.linkType(), incoming, english) + " : " + otherTitle);
    }
    return sentences;
  }

  private String linkLabel(String code, boolean incoming, boolean english) {
    LinkTypeView type = linkTypes.findByCode(code).orElse(null);
    if (type == null) {
      return code;
    }
    boolean inverse = type.directed() && incoming;
    String text =
        english
            ? (inverse ? type.inverseLabelEn() : type.labelEn())
            : (inverse ? type.inverseLabelFr() : type.labelFr());
    if (text == null) {
      text = english ? type.labelEn() : type.labelFr();
    }
    return text == null ? code : text;
  }

  private Map<String, String> emotionLabels(ExportTexts texts) {
    boolean english = Locale.ENGLISH.getLanguage().equals(texts.locale().getLanguage());
    Map<String, String> labels = new HashMap<>();
    for (EmotionView e : emotions.activeEmotions()) {
      String label = english && e.labelEn() != null ? e.labelEn() : e.labelFr();
      if (label != null) {
        labels.put(e.code(), label);
      }
    }
    return labels;
  }

  private static String typeLabel(String type, ExportTexts texts) {
    return texts.get("type." + type);
  }

  private static String sessionHeading(SessionRow s, ExportTexts texts) {
    return texts.get("session.heading." + s.kind(), ExportDates.label(s.time(), texts));
  }

  private static String sessionDetails(SessionRow s, ExportTexts texts) {
    List<String> parts = new ArrayList<>();
    parts.add(texts.get("session.kind." + s.kind()));
    if (s.modality() != null) {
      parts.add(texts.get("session.modality." + s.modality()));
    }
    if (s.durationMin() != null) {
      parts.add(texts.get("session.duration", String.valueOf(s.durationMin())));
    }
    return String.join(" · ", parts);
  }

  private static String medicationLine(EntryRow e, ExportTexts texts) {
    List<String> parts = new ArrayList<>();
    parts.add(e.molecule());
    if (e.dosage() != null && !e.dosage().isBlank()) {
      parts.add(e.dosage());
    }
    if (e.frequency() != null && !e.frequency().isBlank()) {
      parts.add(e.frequency());
    }
    if (e.periodFrom() != null) {
      parts.add(
          e.periodTo() == null
              ? texts.get("period.since", ExportDates.day(e.periodFrom()))
              : texts.get(
                  "period.range", ExportDates.day(e.periodFrom()), ExportDates.day(e.periodTo())));
    }
    return String.join(" · ", parts);
  }

  /** Titre, sinon début du texte (80 caractères), sinon le libellé de repli. */
  private static String linkTitle(String title, String body, String fallback) {
    if (title != null && !title.isBlank()) {
      return title.trim();
    }
    if (body != null && !body.isBlank()) {
      String flat = body.trim().replaceAll("\\s+", " ");
      return flat.length() > LINK_TITLE_MAX ? flat.substring(0, LINK_TITLE_MAX).trim() + "…" : flat;
    }
    return fallback;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
