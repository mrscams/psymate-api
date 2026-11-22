package com.psymate.directory.service;

import com.psymate.directory.api.PublishedProfileFacade;
import com.psymate.directory.api.PublishedProfileView;
import com.psymate.directory.repository.PractitionerProfileRepository;
import com.psymate.directory.repository.PractitionerProfileRepository.PublishedSummary;
import com.psymate.identity.api.PractitionerFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fiche publiée d'un praticien approuvé (G Q71, Q78) : requête JPQL de {@link
 * PractitionerProfileRepository} sur les fiches publiées, puis filtre « approuvé » lu par {@link
 * PractitionerFacade} (G Q48 (2)). Transaction en lecture seule, aucune écriture.
 */
@Service
@Transactional(readOnly = true)
class PublishedProfileFacadeImpl implements PublishedProfileFacade {

  private static final String APPROVED = "approved";

  private final PractitionerProfileRepository profiles;
  private final PractitionerFacade practitioners;

  PublishedProfileFacadeImpl(
      PractitionerProfileRepository profiles, PractitionerFacade practitioners) {
    this.profiles = profiles;
    this.practitioners = practitioners;
  }

  @Override
  public Optional<PublishedProfileView> bySlug(String slug) {
    if (slug == null || slug.isBlank()) {
      return Optional.empty();
    }
    return profiles.findPublishedSummaryBySlug(slug).flatMap(this::ifApproved);
  }

  @Override
  public Optional<PublishedProfileView> byPractitioner(UUID practitionerId) {
    if (practitionerId == null) {
      return Optional.empty();
    }
    return profiles.findPublishedSummaryByPractitioner(practitionerId).flatMap(this::ifApproved);
  }

  private Optional<PublishedProfileView> ifApproved(PublishedSummary summary) {
    return practitioners
        .find(summary.practitionerId())
        .filter(p -> APPROVED.equals(p.verificationStatus()))
        .map(
            p ->
                new PublishedProfileView(
                    summary.practitionerId(), p.tenantId(), summary.slug(), summary.displayName()));
  }
}
