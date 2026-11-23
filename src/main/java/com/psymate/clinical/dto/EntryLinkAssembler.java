package com.psymate.clinical.dto;

import com.psymate.clinical.api.CreatedLink;
import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Liens posés à la création d'une entrée, vus depuis cette entrée. Le libellé vient du catalogue de
 * types de liens : le libellé inverse quand la nouvelle entrée est la cible d'un type dirigé
 * (direction incoming), dans la langue servie : anglais pour Accept-Language: en, arabe pour ar (le
 * français à défaut de libellé arabe, G Q98), français sinon.
 */
@Component
public class EntryLinkAssembler {

  private final LinkTypeCatalog catalog;

  EntryLinkAssembler(LinkTypeCatalog catalog) {
    this.catalog = catalog;
  }

  public List<EntryLinkDto> present(UUID entryId, List<CreatedLink> links, Locale locale) {
    return links.stream().map(l -> present(entryId, l, locale)).toList();
  }

  private EntryLinkDto present(UUID entryId, CreatedLink link, Locale locale) {
    return new EntryLinkDto(
        link.id(), link.sourceId(), link.targetId(), link.linkType(), label(entryId, link, locale));
  }

  private String label(UUID entryId, CreatedLink link, Locale locale) {
    ServedLanguage language = ServedLanguage.of(locale);
    LinkTypeView type = catalog.findByCode(link.linkType()).orElse(null);
    if (type == null) {
      return link.linkType();
    }
    boolean inverse = type.directed() && link.targetId().equals(entryId);
    String text = inverse ? type.inverseLabel(language) : type.label(language);
    if (text == null) {
      text = type.label(language);
    }
    return text;
  }
}
