package com.psymate.directory;

import com.psymate.directory.api.DirectoryFacade;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class DirectoryFacadeImpl implements DirectoryFacade {

  private final PublicDirectoryRepository repository;

  DirectoryFacadeImpl(PublicDirectoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<PublishedProfile> findPublished(String slug) {
    return repository
        .findPublished(slug)
        .map(d -> new PublishedProfile(d.slug(), d.displayName(), d.city()));
  }
}
