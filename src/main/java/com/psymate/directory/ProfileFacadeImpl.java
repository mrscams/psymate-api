package com.psymate.directory;

import com.psymate.directory.api.ProfileFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class ProfileFacadeImpl implements ProfileFacade {

  private final JdbcClient jdbc;

  ProfileFacadeImpl(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ProfileSummary> find(UUID practitionerId) {
    return jdbc.sql("select slug, published from practitioner_profile where practitioner_id = ?")
        .param(practitionerId)
        .query((rs, i) -> new ProfileSummary(practitionerId, rs.getString(1), rs.getBoolean(2)))
        .optional();
  }
}
