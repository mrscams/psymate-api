package com.psymate.directory;

import com.psymate.directory.ProfileException.FieldError;
import com.psymate.directory.api.ProfilePublished;
import com.psymate.directory.api.ProfileUnpublished;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.PractitionerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Cas d'usage du profil public du praticien connecté (UC-13). */
@Service
class ProfileService {

  private static final int SLUG_ATTEMPTS = 5;
  private static final String APPROVED = "approved";

  private final ProfileRepository repo;
  private final PhotoStorage storage;
  private final DomainEventPublisher events;
  private final PractitionerFacade practitioners;

  ProfileService(
      ProfileRepository repo,
      PhotoStorage storage,
      DomainEventPublisher events,
      PractitionerFacade practitioners) {
    this.repo = repo;
    this.storage = storage;
    this.events = events;
    this.practitioners = practitioners;
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(
            () ->
                ProfileException.of(
                    HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Session requise."));
  }

  /** Profil du praticien connecté, créé vide au premier appel. */
  @Transactional
  public ProfileDto get() {
    CurrentPractitioner p = me();
    for (int i = 0; i < SLUG_ATTEMPTS; i++) {
      var existing = repo.find(p.id(), p.profession());
      if (existing.isPresent()) {
        return existing.get();
      }
      repo.insertIfAbsent(
          p.id(), SlugGenerator.unique(p.displayName(), repo::slugExists), p.displayName());
    }
    throw ProfileException.of(
        HttpStatus.CONFLICT, "SLUG_CONFLICT", "Impossible de générer une adresse unique.");
  }

  @Transactional
  public ProfileDto update(UpdateProfileRequest request) {
    CurrentPractitioner p = me();
    get();
    List<FieldError> errors =
        ProfileValidator.validate(
            request, repo.activeCodes("specialty"), repo.activeCodes("audience"));
    if (!errors.isEmpty()) {
      throw ProfileException.invalid(errors);
    }
    repo.update(p.id(), request);
    return get();
  }

  @Transactional
  public ProfileDto uploadPhoto(byte[] bytes) {
    CurrentPractitioner p = me();
    ProfileDto before = get();
    byte[] processed = PhotoProcessor.process(bytes);
    String key = storage.store(p.id(), processed);
    afterRollbackDelete(key);
    repo.setPhoto(p.id(), key);
    afterCommitDelete(before.photoAssetKey());
    return get();
  }

  @Transactional
  public void deletePhoto() {
    CurrentPractitioner p = me();
    ProfileDto before = get();
    if (before.photoAssetKey() != null) {
      repo.setPhoto(p.id(), null);
      afterCommitDelete(before.photoAssetKey());
    }
  }

  /** Supprime l'ancien fichier seulement une fois la base validée. */
  private void afterCommitDelete(String key) {
    if (key == null) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            storage.delete(key);
          }
        });
  }

  /** Supprime le nouveau fichier si la transaction est annulée. */
  private void afterRollbackDelete(String key) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              storage.delete(key);
            }
          }
        });
  }

  @Transactional
  public void publish() {
    CurrentPractitioner p = me();
    ProfileDto profile = get();
    boolean verified =
        practitioners
            .find(p.id())
            .map(PractitionerView::verificationStatus)
            .map(APPROVED::equals)
            .orElse(false);
    List<String> missing = missingForPublication(p, profile, verified);
    if (!missing.isEmpty()) {
      throw new ProfileException(
          HttpStatus.CONFLICT,
          "PROFILE_INCOMPLETE",
          "Le profil est incomplet.",
          List.of(),
          Map.of("missing", missing));
    }
    if (repo.setPublished(p.id(), true)) {
      events.publish(new ProfilePublished(p.tenantId(), p.id(), profile.slug()));
    }
  }

  @Transactional
  public void unpublish() {
    CurrentPractitioner p = me();
    ProfileDto profile = get();
    if (repo.setPublished(p.id(), false)) {
      events.publish(new ProfileUnpublished(p.tenantId(), p.id(), profile.slug()));
    }
  }

  /**
   * Dépublie le profil d'un praticien hors requête HTTP (réaction à un événement d'identité, par
   * exemple un refus de dossier). Idempotent : sans profil, ou si le profil n'est déjà plus publié,
   * rien n'est fait et aucun événement n'est émis ; ProfileUnpublished n'est publié que si l'état a
   * réellement changé.
   */
  @Transactional
  public void unpublishByPractitioner(UUID tenantId, UUID practitionerId) {
    Optional<String> slug = repo.findSlug(practitionerId);
    if (slug.isPresent() && repo.setPublished(practitionerId, false)) {
      events.publish(new ProfileUnpublished(tenantId, practitionerId, slug.get()));
    }
  }

  /**
   * Éléments manquants pour publier, dans cet ordre : verification (dossier non approuvé par
   * l'administration, voir A10.1), displayName, profession, city, contact (au moins un moyen de
   * contact affiché et renseigné).
   */
  static List<String> missingForPublication(
      CurrentPractitioner p, ProfileDto profile, boolean verified) {
    List<String> missing = new ArrayList<>();
    if (!verified) {
      missing.add("verification");
    }
    if (blank(profile.displayName())) {
      missing.add("displayName");
    }
    if (blank(p.profession())) {
      missing.add("profession");
    }
    if (blank(profile.city())) {
      missing.add("city");
    }
    boolean contact =
        (profile.showPhone() && !blank(profile.contactPhone()))
            || (profile.showEmail() && !blank(profile.contactEmail()))
            || (profile.showAddress() && !blank(profile.address()));
    if (!contact) {
      missing.add("contact");
    }
    return missing;
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
