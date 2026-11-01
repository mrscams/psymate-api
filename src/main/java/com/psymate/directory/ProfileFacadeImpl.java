package com.psymate.directory;

import com.psymate.directory.api.ProfileFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProfileFacadeImpl implements ProfileFacade {

  private final PractitionerProfileRepository profiles;

  ProfileFacadeImpl(PractitionerProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Override
  public Optional<ProfileSummary> find(UUID practitionerId) {
    return profiles
        .findSummary(practitionerId)
        .map(s -> new ProfileSummary(practitionerId, s.slug(), s.published()));
  }
}
