/**
 * Vue d'ensemble d'un patient (M4-03, A6, écran 2).
 *
 * <p>Le module est une projection de lecture : {@code patient_overview} (V09) garde une ligne JSONB
 * par patient, recalculée par les abonnés {@code OverviewListener} puis, si elle est en retard sur
 * {@code patient.clinical_rev}, à la volée par le contrôleur (A7, séquence 3). Pour calculer, le
 * projecteur lit directement en SQL les tables {@code entry}, {@code entry_version}, {@code
 * entry_feeling}, {@code entry_medication}, {@code entry_session}, {@code emotion}, {@code link},
 * {@code patient} et {@code patient_flag}, ce que A5 autorise pour ce module de projection ; il ne
 * référence aucune classe interne d'un autre module. Seules les classes d'événements ({@code
 * clinical.api.Entry*}, {@code linking.api.LinkAccepted}, {@code patients.api.PatientUpdated}) et
 * la garde de propriété ({@code patients.api}) sont importées.
 */
@ApplicationModule(
    allowedDependencies = {"patients :: api", "clinical :: api", "linking :: api", "shared"})
package com.psymate.overview;

import org.springframework.modulith.ApplicationModule;
