package com.psymate.clinical.application;

import com.psymate.clinical.persistence.RelabelRepository;
import com.psymate.vocabulary.api.EmotionUsagePort;
import org.springframework.stereotype.Component;

/**
 * Implémentation par clinical du port {@link EmotionUsagePort} de vocabulary (M9-03) : vocabulary
 * compte les ressentis d'une émotion au moment d'une fusion sans lire {@code entry_feeling}. Lit
 * directement le repository, dans la transaction de l'appelant : passer par un service de clinical
 * qui dépend de patients, lui-même client de vocabulary, formerait un cycle de beans.
 */
@Component
class EmotionUsageAdapter implements EmotionUsagePort {

  private final RelabelRepository repo;

  EmotionUsageAdapter(RelabelRepository repo) {
    this.repo = repo;
  }

  @Override
  public int feelingsWith(String emotionCode) {
    return repo.countFeelings(emotionCode);
  }
}
