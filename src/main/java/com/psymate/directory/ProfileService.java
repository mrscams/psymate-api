package com.psymate.directory;

import com.psymate.directory.ProfileException.FieldError;
import com.psymate.directory.api.ProfilePublished;
import com.psymate.directory.api.ProfileUnpublished;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cas d'usage du profil public du praticien connecté (UC-13). */
@Service
class ProfileService {

  private static final int SLUG_ATTEMPTS = 5;

  private final ProfileRepository repo;
  private final PhotoStorage storage;
  private final DomainEventPublisher events;

  ProfileService(ProfileRepository repo, PhotoStorage storage, DomainEventPublisher events) {
    this.repo = repo;
    this.storage = storage;
    this.events = events;
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
    repo.setPhoto(p.id(), key);
    if (before.photoAssetKey() != null) {
      storage.delete(before.photoAssetKey());
    }
    return get();
  }

  @Transactional
  public void deletePhoto() {
    CurrentPractitioner p = me();
    ProfileDto before = get();
    if (before.photoAssetKey() != null) {
      repo.setPhoto(p.id(), null);
      storage.delete(before.photoAssetKey());
    }
  }

  @Transactional
  public void publish() {
    CurrentPractitioner p = me();
    ProfileDto profile = get();
    List<String> missing = missingForPublication(p, profile);
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
   * Éléments manquants pour publier : displayName, profession, city, contact (au moins un moyen de
   * contact affiché et renseigné).
   */
  static List<String> missingForPublication(CurrentPractitioner p, ProfileDto profile) {
    List<String> missing = new ArrayList<>();
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
    // M5-06 : ajouter ici la condition verification_status = approved (via identity.api),
    // avec un motif dédié dans "missing".
    return missing;
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
