package com.psymate.linking.application;

import com.psymate.clinical.api.CreatedLink;
import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.api.LinkRequest;
import com.psymate.linking.api.LinkFacade;
import com.psymate.linking.api.LinkView;
import com.psymate.linking.domain.Link;
import com.psymate.linking.persistence.LinkRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Façade des liens. Lecture : une entrée absente ou d'un autre praticien donne une liste vide et un
 * compte nul, rien ne révèle son existence (ADR-09). Écriture : liens d'une entrée neuve (M2-02).
 */
@Service
@Transactional(readOnly = true)
class LinkFacadeService implements LinkFacade {

  private final LinkRepository repo;
  private final EntryFacade entries;
  private final EntryLinksAdapter creator;

  LinkFacadeService(LinkRepository repo, EntryFacade entries, EntryLinksAdapter creator) {
    this.repo = repo;
    this.entries = entries;
    this.creator = creator;
  }

  /** Écriture : rejoint la transaction de l'appelant, ou en ouvre une (non lecture seule). */
  @Override
  @Transactional
  public List<CreatedLink> createForNewEntry(UUID entryId, List<LinkRequest> links) {
    return creator.createForNewEntry(entryId, links);
  }

  @Override
  public List<LinkView> acceptedLinksOf(UUID entryId) {
    return entries.exists(entryId)
        ? repo.acceptedLinksOf(entryId).stream().map(LinkFacadeService::view).toList()
        : List.of();
  }

  @Override
  public int countFor(UUID entryId) {
    return entries.exists(entryId) ? repo.countAccepted(entryId) : 0;
  }

  private static LinkView view(Link l) {
    return new LinkView(
        l.id(),
        l.patientId(),
        l.sourceId(),
        l.targetId(),
        l.linkType(),
        l.directed(),
        l.provenance().code(),
        l.rationale(),
        l.authorId(),
        l.createdAt());
  }
}
