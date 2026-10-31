package com.psymate.linking.web;

import com.psymate.clinical.api.EntryFacade;
import com.psymate.linking.domain.Link;
import com.psymate.linking.web.LinkDtos.LinkDto;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Construit le DTO d'un lien vu depuis une entrée de référence : le verbe (inverse quand l'entrée
 * est la cible d'un lien dirigé, A4.3) et la phrase lisible de l'écran 6, « entrée verbe autre
 * entrée ». Les libellés viennent du catalogue de types, jamais de texte codé en dur.
 */
@Component
class LinkPresenter {

  private static final int EXCERPT_LENGTH = 60;

  private final LinkTypeCatalog catalog;
  private final EntryFacade entries;
  private final LinkMapper mapper;
  private final LinkMessages messages;

  LinkPresenter(
      LinkTypeCatalog catalog, EntryFacade entries, LinkMapper mapper, LinkMessages messages) {
    this.catalog = catalog;
    this.entries = entries;
    this.mapper = mapper;
    this.messages = messages;
  }

  LinkDto present(Link link, UUID referenceEntryId, Locale locale) {
    String label = label(link, referenceEntryId, locale);
    String sentence =
        entryText(referenceEntryId, locale)
            + " "
            + label
            + " "
            + entryText(link.otherEnd(referenceEntryId), locale);
    return mapper.toDto(link, label, sentence);
  }

  private String label(Link link, UUID referenceEntryId, Locale locale) {
    boolean english = Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
    LinkTypeView type = catalog.findByCode(link.linkType()).orElse(null);
    if (type == null) {
      return link.linkType();
    }
    boolean inverse = type.directed() && link.targetId().equals(referenceEntryId);
    String text =
        english
            ? (inverse ? type.inverseLabelEn() : type.labelEn())
            : (inverse ? type.inverseLabelFr() : type.labelFr());
    if (text == null) {
      text = english ? type.labelEn() : type.labelFr();
    }
    return text;
  }

  /** Titre de l'entrée, sinon début du texte, sinon « une entrée ». */
  private String entryText(UUID entryId, Locale locale) {
    return entries
        .currentVersionOf(entryId)
        .map(
            v -> {
              if (v.title() != null && !v.title().isBlank()) {
                return v.title().strip();
              }
              if (v.body() != null && !v.body().isBlank()) {
                String body = v.body().strip();
                return body.length() <= EXCERPT_LENGTH
                    ? body
                    : body.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
              }
              return null;
            })
        .orElseGet(() -> messages.get("link.entry.untitled", locale));
  }
}
