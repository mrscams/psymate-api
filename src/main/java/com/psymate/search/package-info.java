/**
 * Recherche dans le dossier d'un patient (M4-02, A6, écran 7).
 *
 * <p>Le module est une projection de lecture : le {@code search_vector} de chaque entrée est
 * maintenu par trigger (V08) et par l'abonné {@code SearchIndexListener} ; les requêtes lisent
 * directement les tables {@code entry}, {@code entry_version}, {@code entry_feeling}, {@code
 * entry_tag}, {@code tag}, {@code emotion} et {@code entry_type} en SQL (la fonction SQL de V08 lit
 * aussi {@code entry_medication}), ce que A5 autorise pour ce module de projection. Il ne référence
 * aucune classe interne d'un autre module : seules les classes d'événements {@code
 * clinical.api.EntryCreated} et {@code EntryCorrected} sont importées.
 */
@ApplicationModule(
    allowedDependencies = {
      "events :: api",
      "patients :: api",
      "identity :: api",
      "clinical :: api",
      "shared"
    })
package com.psymate.search;

import org.springframework.modulith.ApplicationModule;
