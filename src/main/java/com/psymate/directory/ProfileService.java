package com.psymate.directory;

import com.psymate.directory.ProfileException.FieldError;
import com.psymate.directory.api.ProfilePublished;
import com.psymate.directory.api.ProfileUnpublished;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PractitionerFacade.PractitionerView;
import java.time.Clock;
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

  private final PractitionerProfileRepository profiles;
  private final DirectoryVocabularyRepository vocabulary;
  private final PhotoStorage storage;
  private final DomainEventPublisher events;
  private final PractitionerFacade practitioners;
  private final Clock clock;

  ProfileService(
      PractitionerProfileRepository profiles,
      DirectoryVocabularyRepository vocabulary,
      PhotoStorage storage,
      DomainEventPublisher events,
      PractitionerFacade practitioners,
      Clock clock) {
    this.profiles = profiles;
    this.vocabulary = vocabulary;
    this.storage = storage;
    this.events = events;
    this.practitioners = practitioners;
    this.clock = clock;
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
    ensureExists(p);
    return ProfileAssembler.toDto(profiles.findById(p.id()).orElseThrow(), p.profession());
  }

  /**
   * Crée le profil vide s'il n'existe pas (concurrence : le premier gagne). Ne charge pas l'entité
   * : les écritures la chargent ensuite sous verrou, jamais avant (un état déjà chargé ne serait
   * pas relu par {@code findForUpdate}).
   */
  private void ensureExists(CurrentPractitioner p) {
    for (int i = 0; i < SLUG_ATTEMPTS; i++) {
      if (profiles.existsById(p.id())) {
        return;
      }
      profiles.insertIfAbsent(
          p.id(), SlugGenerator.unique(p.displayName(), profiles::existsBySlug), p.displayName());
    }
    throw ProfileException.of(
        HttpStatus.CONFLICT, "SLUG_CONFLICT", "Impossible de générer une adresse unique.");
  }

  private PractitionerProfile lock(UUID practitionerId) {
    return profiles
        .findForUpdate(practitionerId)
        .orElseThrow(
            () ->
                ProfileException.of(
                    HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profil introuvable."));
  }

  @Transactional
  public ProfileDto update(UpdateProfileRequest request) {
    CurrentPractitioner p = me();
    ensureExists(p);
    List<FieldError> errors =
        ProfileValidator.validate(
            request, vocabulary.activeCodes("specialty"), vocabulary.activeCodes("audience"));
    if (!errors.isEmpty()) {
      throw ProfileException.invalid(errors);
    }
    PractitionerProfile profile = lock(p.id());
    profile.applyEdit(request, DirectoryClock.now(clock));
    return ProfileAssembler.toDto(profile, p.profession());
  }

  @Transactional
  public ProfileDto uploadPhoto(byte[] bytes) {
    CurrentPractitioner p = me();
    ensureExists(p);
    byte[] processed = PhotoProcessor.process(bytes);
    String key = storage.store(p.id(), processed);
    afterRollbackDelete(key);
    PractitionerProfile profile = lock(p.id());
    String previousKey = profile.getPhotoAssetKey();
    profile.setPhoto(key, DirectoryClock.now(clock));
    afterCommitDelete(previousKey);
    return ProfileAssembler.toDto(profile, p.profession());
  }

  @Transactional
  public void deletePhoto() {
    CurrentPractitioner p = me();
    ensureExists(p);
    PractitionerProfile profile = lock(p.id());
    String previousKey = profile.getPhotoAssetKey();
    if (previousKey != null) {
      profile.setPhoto(null, DirectoryClock.now(clock));
      afterCommitDelete(previousKey);
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
    ensureExists(p);
    PractitionerProfile entity = lock(p.id());
    ProfileDto profile = ProfileAssembler.toDto(entity, p.profession());
    boolean verified =
        practitioners
            .find(p.id())
            .map(PractitionerView::verificationStatus)
            .map(APPROVED::equals)
            .orElse(false);
    List<String> missing = new ArrayList<>(missingForPublication(p, profile, verified));
    // Un code désactivé par l'administration depuis l'enregistrement du brouillon (M1-14b) ne
    // doit pas être publié : un profil publié n'utilise que des codes actifs.
    if (!vocabulary.activeCodes("specialty").containsAll(profile.specialties())) {
      missing.add("specialties");
    }
    if (!vocabulary.activeCodes("audience").containsAll(profile.audiences())) {
      missing.add("audiences");
    }
    if (!missing.isEmpty()) {
      throw new ProfileException(
          HttpStatus.CONFLICT,
          "PROFILE_INCOMPLETE",
          "Le profil est incomplet.",
          List.of(),
          Map.of("missing", missing));
    }
    if (entity.publish(DirectoryClock.now(clock))) {
      events.publish(new ProfilePublished(p.tenantId(), p.id(), entity.getSlug()));
    }
  }

  @Transactional
  public void unpublish() {
    CurrentPractitioner p = me();
    ensureExists(p);
    PractitionerProfile entity = lock(p.id());
    if (entity.unpublish(DirectoryClock.now(clock))) {
      events.publish(new ProfileUnpublished(p.tenantId(), p.id(), entity.getSlug()));
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
    Optional<PractitionerProfile> found = profiles.findForUpdate(practitionerId);
    if (found.isPresent() && found.get().unpublish(DirectoryClock.now(clock))) {
      events.publish(new ProfileUnpublished(tenantId, practitionerId, found.get().getSlug()));
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
