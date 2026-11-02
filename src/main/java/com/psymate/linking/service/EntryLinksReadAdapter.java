package com.psymate.linking.service;

import com.psymate.clinical.api.EntryLinksReadPort;
import com.psymate.clinical.api.LinkedEntryView;
import com.psymate.linking.repository.LinkRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation par linking du port de lecture {@link EntryLinksReadPort} de clinical (export,
 * M6-05). Lit directement les liens acceptés : {@link LinkService#acceptedLinksOf} exige un
 * principal de session (ADR-09), absent dans le travail d'export du compte suspendu. La garde de
 * propriété est celle de l'appelant (voir le port).
 */
@Component
class EntryLinksReadAdapter implements EntryLinksReadPort {

  private final LinkRepository repo;

  EntryLinksReadAdapter(LinkRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LinkedEntryView> acceptedLinksOf(UUID entryId) {
    return repo.acceptedLinksOf(entryId).stream()
        .map(l -> new LinkedEntryView(l.id(), l.sourceId(), l.targetId(), l.linkType()))
        .toList();
  }
}
