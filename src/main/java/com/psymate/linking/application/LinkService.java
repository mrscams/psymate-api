package com.psymate.linking.application;

import com.psymate.clinical.api.EntryFacade;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.linking.api.LinkAccepted;
import com.psymate.linking.domain.Link;
import com.psymate.linking.domain.LinkError;
import com.psymate.linking.domain.LinkException;
import com.psymate.linking.domain.LinkRules;
import com.psymate.linking.persistence.LinkRepository;
import com.psymate.patients.api.PatientFacade;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cycle de vie des liens (A7, UC-06) : créer, changer de type, supprimer, et réagir à l'archivage
 * d'une entrée. Toutes les gardes (propriété, même patient, matrice des types, doublon) passent
 * avant la première écriture. Chaque écriture ajuste {@code entry.link_count} des deux entrées par
 * la façade clinical, incrémente {@code patient.clinical_rev} (G Q30) et, quand un lien devient
 * accepté, publie {@link LinkAccepted}.
 *
 * <p>{@code rejected} est réservé aux suggestions de l'IA (V2) : aucun cas d'usage V1 ne l'émet, et
 * {@code LinkRejected} n'est jamais publié.
 */
@Service
public class LinkService {

  private final LinkRepository repo;
  private final EntryFacade entries;
  private final LinkTypeCatalog catalog;
  private final PatientFacade patients;
  private final PatientOwnershipGuard guard;
  private final DomainEventPublisher events;
  private final Clock clock;
  private final TransactionTemplate tx;

  LinkService(
      LinkRepository repo,
      EntryFacade entries,
      LinkTypeCatalog catalog,
      PatientFacade patients,
      PatientOwnershipGuard guard,
      DomainEventPublisher events,
      Clock clock,
      TransactionTemplate tx) {
    this.repo = repo;
    this.entries = entries;
    this.catalog = catalog;
    this.patients = patients;
    this.guard = guard;
    this.events = events;
    this.clock = clock;
    this.tx = tx;
  }

  // ---- création

  /**
   * Pose un lien manuel accepté de {@code sourceId} vers {@code targetId}. Les deux entrées doivent
   * appartenir à un même patient du praticien courant.
   *
   * @throws LinkException ENTRY_NOT_FOUND (404, entrée absente), SELF_LINK, DIFFERENT_PATIENT,
   *     UNKNOWN_LINK_TYPE, LINK_TYPE_NOT_ALLOWED (422) ou LINK_EXISTS (409) ; 403 de la garde de
   *     propriété si une entrée est d'un patient d'un autre praticien
   */
  public Link create(UUID sourceId, UUID targetId, String linkType, String rationale) {
    String reason = normalize(rationale);
    try {
      return tx.execute(status -> doCreate(sourceId, targetId, linkType, reason));
    } catch (DuplicateKeyException race) {
      throw duplicateAfterRace(race, sourceId, targetId, linkType);
    }
  }

  private Link doCreate(UUID sourceId, UUID targetId, String linkType, String rationale) {
    CurrentPractitioner me = currentPractitioner();
    UUID patientId = requireSamePatient(sourceId, targetId);
    requireNotArchived(sourceId, targetId);
    String sourceType = typeOf(sourceId);
    String targetType = typeOf(targetId);
    LinkTypeView type =
        LinkRules.validate(
            sourceId, targetId, sourceType, targetType, linkType, catalog.activeLinkTypes());
    requireNoDuplicate(sourceId, targetId, type);

    Link link =
        Link.manual(
            me.tenantId(),
            patientId,
            sourceId,
            targetId,
            type.code(),
            type.directed(),
            rationale,
            me.id(),
            clock.instant());
    repo.insert(link);
    adjustLinkCounts(link, +1);
    patients.touchClinicalRev(patientId);
    events.publish(accepted(link, null));
    return link;
  }

  // ---- changement de type

  /**
   * Change le type d'un lien : un nouveau lien manuel accepté est créé (mêmes extrémités, même
   * justification) et l'ancien passe {@code superseded}, relié au nouveau par {@code
   * superseded_by}. Le nombre de liens de chaque entrée est inchangé au final.
   *
   * @return le nouveau lien
   * @throws LinkException LINK_NOT_FOUND (404), LINK_NOT_ACTIVE (409, lien déjà remplacé ou
   *     supprimé), UNKNOWN_LINK_TYPE, LINK_TYPE_NOT_ALLOWED (422) ou LINK_EXISTS (409, y compris le
   *     même type que le lien actuel)
   */
  public Link changeType(UUID linkId, String newType) {
    Link current = repo.find(linkId).orElse(null);
    try {
      return tx.execute(status -> doChangeType(linkId, newType));
    } catch (DuplicateKeyException race) {
      if (current == null) {
        throw race;
      }
      throw duplicateAfterRace(race, current.sourceId(), current.targetId(), newType);
    }
  }

  private Link doChangeType(UUID linkId, String newType) {
    CurrentPractitioner me = currentPractitioner();
    Link old = lockOwnedLink(linkId);
    requireActive(old);
    // Les extrémités sont du patient du lien, déjà vérifié comme appartenant au praticien.
    // LINK_NOT_ACTIVE passe avant ENTRY_ARCHIVED : l'abonné d'EntryArchived a déjà remplacé le
    // lien.
    requireNotArchived(old.sourceId(), old.targetId());
    String sourceType = typeOf(old.sourceId());
    String targetType = typeOf(old.targetId());
    LinkTypeView type =
        LinkRules.validate(
            old.sourceId(),
            old.targetId(),
            sourceType,
            targetType,
            newType,
            catalog.activeLinkTypes());
    requireNoDuplicate(old.sourceId(), old.targetId(), type);

    Instant now = clock.instant();
    Link replacement =
        Link.manual(
            me.tenantId(),
            old.patientId(),
            old.sourceId(),
            old.targetId(),
            type.code(),
            type.directed(),
            old.rationale(),
            me.id(),
            now);
    repo.insert(replacement);
    repo.supersede(old.id(), replacement.id(), me.id(), now);
    // Le compte net est inchangé (un lien actif remplace un lien actif) : aucun ajustement.
    patients.touchClinicalRev(old.patientId());
    events.publish(accepted(replacement, old.id()));
    return replacement;
  }

  // ---- suppression

  /**
   * Supprime un lien : il passe {@code superseded} sans lien de remplacement (jamais de suppression
   * physique, A7) et ses deux entrées perdent un lien. Sans effet si le lien n'est plus actif.
   * Aucun événement : {@code LinkRejected} est réservé aux suggestions de l'IA (V2).
   *
   * @throws LinkException LINK_NOT_FOUND (404)
   */
  @Transactional
  public void remove(UUID linkId) {
    CurrentPractitioner me = currentPractitioner();
    Link link = lockOwnedLink(linkId);
    if (!link.status().active()) {
      return;
    }
    repo.supersede(link.id(), null, me.id(), clock.instant());
    if (link.status().countsInLinkCount()) {
      adjustLinkCounts(link, -1);
    }
    patients.touchClinicalRev(link.patientId());
  }

  // ---- archivage d'une entrée

  /**
   * Passe {@code superseded} tous les liens vivants dont l'entrée est source ou cible, et retire un
   * lien du compteur de l'autre extrémité et de l'entrée elle-même. Appelé par l'abonné
   * d'EntryArchived : aucun praticien courant, donc aucune garde de propriété (l'événement vient de
   * clinical, qui a déjà contrôlé l'archivage). Idempotent : un second appel ne trouve plus de lien
   * vivant. Aucun événement ; la restauration de l'entrée ne rétablit pas les liens (M1-05).
   */
  @Transactional
  public void supersedeLinksOf(UUID entryId) {
    List<Link> links = repo.activeLinksOfForUpdate(entryId);
    if (links.isEmpty()) {
      return;
    }
    Instant now = clock.instant();
    // Deltas agrégés par entrée, appliqués dans l'ordre croissant des UUID (comme create) : pas
    // d'interblocage avec une création concurrente.
    Map<UUID, Integer> deltas = new TreeMap<>();
    for (Link link : links) {
      repo.supersede(link.id(), null, null, now);
      if (link.status().countsInLinkCount()) {
        deltas.merge(link.sourceId(), -1, Integer::sum);
        deltas.merge(link.targetId(), -1, Integer::sum);
      }
    }
    deltas.forEach(entries::adjustLinkCount);
    links.stream().map(Link::patientId).distinct().forEach(patients::touchClinicalRev);
  }

  // ---- lecture

  /**
   * Liens acceptés de l'entrée (source ou cible).
   *
   * @throws LinkException ENTRY_NOT_FOUND (404) ; 403 de la garde de propriété si le patient est
   *     d'un autre praticien
   */
  @Transactional(readOnly = true)
  public List<Link> acceptedLinksOf(UUID entryId) {
    currentPractitioner();
    requireOwnedPatientOf(entryId);
    return repo.acceptedLinksOf(entryId);
  }

  // ---- internes

  private CurrentPractitioner currentPractitioner() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new LinkException(LinkError.UNAUTHENTICATED, "Non authentifié"));
  }

  private static LinkException entryNotFound() {
    return new LinkException(LinkError.ENTRY_NOT_FOUND, "Entrée introuvable");
  }

  /**
   * Patient de l'entrée, après garde de propriété comme clinical : 404 ENTRY_NOT_FOUND si l'entrée
   * n'existe pas, 403 (PATIENT_FORBIDDEN) si son patient est d'un autre praticien.
   */
  private UUID requireOwnedPatientOf(UUID entryId) {
    UUID patientId = entries.patientOf(entryId).orElseThrow(LinkService::entryNotFound);
    guard.requireOwned(patientId);
    return patientId;
  }

  /**
   * Source puis cible passent la garde de propriété ; elles ne sont pas la même entrée et sont du
   * même patient (422 DIFFERENT_PATIENT) ; renvoie ce patient.
   */
  private UUID requireSamePatient(UUID sourceId, UUID targetId) {
    UUID sourcePatient = requireOwnedPatientOf(sourceId);
    UUID targetPatient = requireOwnedPatientOf(targetId);
    LinkRules.requireDistinct(sourceId, targetId);
    if (!sourcePatient.equals(targetPatient)) {
      throw new LinkException(
          LinkError.DIFFERENT_PATIENT, "Les deux entrées doivent appartenir au même patient");
    }
    return sourcePatient;
  }

  /**
   * Refuse un lien dont une extrémité est archivée (A7) : 409 ENTRY_ARCHIVED, comme clinical pour
   * une correction d'entrée archivée. Après les gardes de propriété, avant toute écriture.
   */
  private void requireNotArchived(UUID sourceId, UUID targetId) {
    if (entries.isArchived(sourceId)) {
      throw LinkException.archived("sourceId");
    }
    if (entries.isArchived(targetId)) {
      throw LinkException.archived("targetId");
    }
  }

  private String typeOf(UUID entryId) {
    return entries.typeOf(entryId).orElseThrow(LinkService::entryNotFound);
  }

  /**
   * Lien chargé et verrouillé, dont le patient appartient au praticien courant (403, sinon 404).
   */
  private Link lockOwnedLink(UUID linkId) {
    Link link =
        repo.findForUpdate(linkId)
            .orElseThrow(() -> new LinkException(LinkError.LINK_NOT_FOUND, "Lien introuvable"));
    guard.requireOwned(link.patientId());
    return link;
  }

  private static void requireActive(Link link) {
    if (!link.status().active()) {
      throw new LinkException(LinkError.LINK_NOT_ACTIVE, "Ce lien n'est plus actif");
    }
  }

  private void requireNoDuplicate(UUID sourceId, UUID targetId, LinkTypeView type) {
    repo.findActiveDuplicate(sourceId, targetId, type.code(), !type.directed())
        .ifPresent(
            id -> {
              throw LinkException.exists(id);
            });
  }

  /** Une écriture concurrente a posé le même lien entre-temps : on rend son identifiant. */
  private RuntimeException duplicateAfterRace(
      DuplicateKeyException race, UUID sourceId, UUID targetId, String linkType) {
    boolean undirected = catalog.findByCode(linkType).map(t -> !t.directed()).orElse(false);
    return repo.findActiveDuplicate(sourceId, targetId, linkType, undirected)
        .<RuntimeException>map(LinkException::exists)
        .orElse(race);
  }

  /**
   * Ajuste entry.link_count des deux extrémités, dans l'ordre des identifiants pour que deux
   * écritures croisées ne se bloquent pas mutuellement.
   */
  private void adjustLinkCounts(Link link, int delta) {
    UUID first = link.sourceId();
    UUID second = link.targetId();
    if (first.compareTo(second) > 0) {
      UUID swap = first;
      first = second;
      second = swap;
    }
    entries.adjustLinkCount(first, delta);
    entries.adjustLinkCount(second, delta);
  }

  private static LinkAccepted accepted(Link link, UUID supersededLinkId) {
    return new LinkAccepted(
        link.tenantId(),
        link.id(),
        link.patientId(),
        link.sourceId(),
        link.targetId(),
        link.linkType(),
        link.provenance().code(),
        supersededLinkId);
  }

  private static String normalize(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
