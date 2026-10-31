package com.psymate.search;

import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Tient le {@code search_vector} à jour quand les données hors de la version courante changent
 * (E3.9) : les tags et l'émotion d'une entrée sont écrits après sa version, et une correction de
 * tags seule ne crée pas de version. Les triggers de V08 couvrent les cas où le titre ou le corps
 * changent ; ces abonnés rappellent {@code entry_search_vector_refresh}, qui recalcule le vecteur
 * depuis l'état courant de la base.
 *
 * <p>Idempotence (events.api.EventDeduplicator) : l'effet est un UPDATE déterministe, sans état
 * accumulé, donc un rejeu ou une double livraison produit le même résultat ; pas de déduplication.
 * Si l'entrée n'existe plus ou n'a pas de version courante (rejeu tardif), la requête ne touche
 * aucune ligne, sans exception.
 */
@Component
class SearchIndexListener {

  private final JdbcClient jdbc;

  SearchIndexListener(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @ApplicationModuleListener(id = "search.entry-created")
  void on(EntryCreated event) {
    refresh(event.getEntryId());
  }

  @ApplicationModuleListener(id = "search.entry-corrected")
  void on(EntryCorrected event) {
    refresh(event.getEntryId());
  }

  private void refresh(UUID entryId) {
    jdbc.sql("select entry_search_vector_refresh(:id)").param("id", entryId).query().listOfRows();
  }
}
