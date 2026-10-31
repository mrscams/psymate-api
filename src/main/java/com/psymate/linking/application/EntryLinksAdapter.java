package com.psymate.linking.application;

import com.psymate.clinical.api.CreatedLink;
import com.psymate.clinical.api.EntryLinkException;
import com.psymate.clinical.api.EntryLinksPort;
import com.psymate.clinical.api.LinkRequest;
import com.psymate.linking.domain.Link;
import com.psymate.linking.domain.LinkError;
import com.psymate.linking.domain.LinkException;
import com.psymate.vocabulary.api.LinkTypeView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implémentation par linking du port {@link EntryLinksPort} de clinical (M2-02, A7 séquence 2). Les
 * liens sont posés par {@link LinkService#create}, avec toutes ses gardes, dans la transaction de
 * l'appelant (propagation REQUIRED) : un lien refusé annule l'entrée et les liens déjà posés.
 *
 * <p>Toute {@link LinkException} est traduite en {@link EntryLinkException} (422 côté REST) avec
 * l'index du lien dans {@code links[]}. Le champ fautif est {@code linkType} pour UNKNOWN_LINK_TYPE
 * et LINK_TYPE_NOT_ALLOWED, {@code targetId} pour les autres (ENTRY_NOT_FOUND, SELF_LINK,
 * DIFFERENT_PATIENT, LINK_EXISTS, ENTRY_ARCHIVED : le doublon dans {@code links[]} est détecté ici,
 * par LINK_EXISTS, au second élément). Une entrée cible d'un patient d'un AUTRE praticien n'est pas
 * traduite : le 403 PATIENT_FORBIDDEN de la garde de propriété remonte tel quel, comme pour POST
 * /api/entries/{id}/links.
 */
@Component
class EntryLinksAdapter implements EntryLinksPort {

  private final LinkService links;

  EntryLinksAdapter(LinkService links) {
    this.links = links;
  }

  @Override
  public List<CreatedLink> createForNewEntry(UUID entryId, List<LinkRequest> requests) {
    List<CreatedLink> created = new ArrayList<>();
    for (int i = 0; i < requests.size(); i++) {
      LinkRequest request = requests.get(i);
      UUID source = request.incoming() ? request.targetId() : entryId;
      UUID target = request.incoming() ? entryId : request.targetId();
      try {
        Link link = links.create(source, target, request.linkType(), null);
        created.add(new CreatedLink(link.id(), link.sourceId(), link.targetId(), link.linkType()));
      } catch (LinkException e) {
        throw translate(i, e);
      }
    }
    return created;
  }

  private static EntryLinkException translate(int index, LinkException e) {
    LinkError error = e.error();
    String field =
        error == LinkError.UNKNOWN_LINK_TYPE || error == LinkError.LINK_TYPE_NOT_ALLOWED
            ? "linkType"
            : "targetId";
    List<String> allowed = e.allowedTypes().stream().map(LinkTypeView::code).toList();
    return new EntryLinkException(index, field, error.name(), e.getMessage(), allowed);
  }
}
