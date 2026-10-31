package com.psymate.clinical.application;

import com.psymate.clinical.domain.ClinicalError;
import com.psymate.clinical.domain.ClinicalException;
import com.psymate.clinical.domain.Entry;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.domain.EntryVersion;
import com.psymate.clinical.domain.FeelingDetail;
import com.psymate.clinical.persistence.EntryRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.TagFacade;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture d'une entrée complète et de son historique, pour l'API REST. Le patient doit appartenir
 * au praticien courant (garde de propriété : 403), l'entrée doit exister (404).
 */
@Service
@Transactional(readOnly = true)
public class EntryQueryService {

  private final EntryRepository repo;
  private final PatientOwnershipGuard guard;
  private final TagFacade tags;
  private final EmotionCatalog emotions;

  EntryQueryService(
      EntryRepository repo, PatientOwnershipGuard guard, TagFacade tags, EmotionCatalog emotions) {
    this.repo = repo;
    this.guard = guard;
    this.tags = tags;
    this.emotions = emotions;
  }

  /**
   * L'entrée avec sa version courante, son détail et ses tags (archivée ou non).
   *
   * @throws ClinicalException ENTRY_NOT_FOUND (404) ; la garde de propriété lève 403/404
   */
  public EntryDetails get(UUID entryId) {
    Entry entry = owned(entryId);
    EntryVersion version = repo.currentVersion(entryId).orElseThrow();
    FeelingDetail feeling =
        entry.type() == EntryType.FEELING ? repo.feeling(entryId).orElse(null) : null;
    return new EntryDetails(
        entry,
        version,
        repo.recordedAt(entryId).orElse(entry.createdAt()),
        feeling,
        feeling == null ? null : familyOf(feeling.emotionCode()),
        entry.type() == EntryType.MEDICATION ? repo.medication(entryId).orElse(null) : null,
        entry.type() == EntryType.SESSION ? repo.session(entryId).orElse(null) : null,
        tagLabels(entryId));
  }

  /** Toutes les versions de l'entrée, de la plus récente à la plus ancienne. */
  public EntryHistory history(UUID entryId) {
    Entry entry = owned(entryId);
    return new EntryHistory(entryId, entry.authorType(), repo.versions(entryId));
  }

  private Entry owned(UUID entryId) {
    CurrentPractitioner.get()
        .orElseThrow(() -> new ClinicalException(ClinicalError.UNAUTHENTICATED, "Non authentifié"));
    Entry entry =
        repo.find(entryId)
            .orElseThrow(
                () -> new ClinicalException(ClinicalError.ENTRY_NOT_FOUND, "Entrée introuvable"));
    guard.requireOwned(entry.patientId());
    return entry;
  }

  private String familyOf(String emotionCode) {
    return emotions.activeEmotions().stream()
        .filter(e -> e.code().equals(emotionCode))
        .map(EmotionView::family)
        .findFirst()
        .orElse(null);
  }

  private List<String> tagLabels(UUID entryId) {
    List<UUID> ids = repo.tagIds(entryId);
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> labels = tags.labelsByIds(ids);
    return labels.values().stream().sorted(Comparator.comparing(String::toLowerCase)).toList();
  }
}
