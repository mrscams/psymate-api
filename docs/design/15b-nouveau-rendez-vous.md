# Écran 15b · Panneau « Nouveau rendez-vous » (agenda)

> **Hors V1 (décision G Q25, 29 octobre 2026)** : conservé pour plus tard, aucune tâche ni module `scheduling`. Rien de cette fiche n'est à implémenter en V1.

> Spécification pour agents. Maquette : `15b-nouveau-rendez-vous.html` / `.png`. Palette : Nordique. Module `scheduling` (`POST /api/appointments`, `practitioner_integration`), complète `15-agenda.md`.

## Objectif
Créer un ou plusieurs rendez-vous depuis un créneau cliqué dans l'agenda, en montrant **exactement** ce qui partira vers Google Agenda (et ce qui n'y partira jamais).

## Route & module
- Ouverture : clic sur un créneau vide (semaine/jour), bouton « + Rendez-vous », ou depuis la fiche patient (« Planifier une séance »). État dans l'URL : `/agenda?nouveau=2026-11-07T10:00`.
- API : `POST /api/appointments` `{ patientId, startsAt, durationMin, modality, recurrence?: { every: 1|2|4, count? | until? }, planningNote?, toConfirm }` → `201` avec la liste des occurrences créées (une ligne `appointment` par occurrence, liées par `series_id`).
- Synchronisation : chaque occurrence → un événement Google dans le calendrier secondaire « PsyMate » avec `extendedProperties.private.appointmentId` ; résumé selon la préférence `calendar.googleDetail` (`initials` par défaut → « Séance · M. D. », `none` → « Séance », `full` → nom complet, déconseillé). `planningNote` **n'est jamais** envoyée.
- Conflits : le client interroge `GET /api/agenda/freebusy?from&to` (agrégé Google + rendez-vous locaux) ; un chevauchement n'empêche pas la création, il est signalé.

## Structure
Panneau droit 440 px (`role="dialog"`) sur l'agenda semaine en arrière-plan :
1. En-tête « Nouveau rendez-vous » + « Créneau cliqué : samedi 7 novembre, 10:00 » + fermer.
2. **Patient** : carte (initiales, nom, âge, dernière séance, rythme, modalité habituelle) + « Changer » (recherche parmi les patients actifs). Ligne d'aide calculée : « Le créneau respecte le rythme habituel (7 jours après le 31 oct.) » ou avertissement si trop tôt/tard.
3. **Créneau** : date, début, durée (segments 30 / 45 / 50 / 60 / 90 min, défaut = durée de la fiche patient sinon préférence `sessions.defaultDurationMin`, 60) ; ligne « Se termine à 10:50 · suivi de Théo B. à 11:30, pas de chevauchement ».
4. **Modalité** : Cabinet / Visio / Téléphone (défaut = fiche patient) + aide (adresse du cabinet ; en visio, le lien est ajouté à l'événement Google si collé).
5. **Répéter** : Ne pas répéter / toutes les semaines / 2 semaines / 4 semaines ; puis « 4 fois (jusqu'au …) / 8 fois / jusqu'à une date… ». Mention : chaque occurrence est un rendez-vous distinct, déplaçable seul.
6. **Note de planification** (jamais envoyée à Google).
7. **Bloc « Sera copié dans Google Agenda “PsyMate” »** : aperçu de l'événement tel qu'il apparaîtra (titre, date, heure, modalité, nombre d'occurrences) + niveau de détail + lien vers les préférences. Masqué si aucune intégration Google active (remplacé par « Non synchronisé · Connecter Google Agenda »).
8. **Avertissement de conflit** (ambre) : « L'occurrence du 5 décembre chevauche un bloc “Occupé · Google Pro” (09:00–12:00). Elle sera créée quand même ; vous pourrez la déplacer. »
9. Pied : bouton principal dont le libellé compte les occurrences (« Planifier 4 rendez-vous »), case « À confirmer avec la patiente » (`toConfirm` → statut `tentative`, affiché hachuré dans l'agenda), « Annuler Échap ».

## Interactions
- Changer la durée ou l'heure recalcule la fin, le voisinage (rendez-vous précédent/suivant) et les conflits (débounce 300 ms).
- Changer le patient réinitialise durée et modalité à ses valeurs par défaut, sauf si le praticien les a déjà modifiées dans ce panneau.
- Récurrence « jusqu'à une date » → date picker ; maximum 26 occurrences par série (`422` au-delà).
- Après création : fermeture, les occurrences apparaissent dans l'agenda avec une animation, toast « 4 rendez-vous planifiés · Annuler » (annulation = suppression de la série dans les 10 s, événements Google compris).
- Création hors ligne ou Google indisponible : rendez-vous créés localement, badge « Sync en attente » ; reprise par le job de synchronisation.

## Tokens
- Bloc Google : fond `#EEF1F6`, bordure `#D6DCE6`, icône calendrier `#3A63C2`.
- Avertissement : fond `#FBF1DC`, texte `#7A5A12`, icône triangle.
- Segments de durée / modalité actifs : `#E3EAF8` / `#2C4C99`.

## Accessibilité
- Dialog modal, focus initial sur « Changer » (patient) si prérempli, sinon sur la recherche de patient.
- Segments = `role="group"` + `aria-pressed` ; récurrence = `<select>` natifs.
- Le bloc « Sera copié dans Google » et l'avertissement de conflit sont en `aria-live="polite"`.

## Critères d'acceptation
- [ ] Récurrence toutes les semaines × 4 → 4 lignes `appointment` avec le même `series_id`, 4 événements Google distincts.
- [ ] `planningNote` renseignée → absente du payload Google (test d'intégration sur le mock de l'API Google).
- [ ] Préférence `calendar.googleDetail = none` → résumé « Séance » sans initiales dans l'aperçu et dans l'événement créé.
- [ ] Chevauchement avec un bloc freebusy → avertissement affiché, création néanmoins possible ; aucun avertissement si libre.
- [ ] Case « À confirmer » → statut `tentative`, rendu hachuré dans l'agenda, événement Google avec `status = tentative`.
- [ ] Annuler dans le toast → série supprimée localement et sur Google.

## Hors périmètre
Rappels SMS/e-mail au patient, prise de rendez-vous en ligne par le patient, salle d'attente visio intégrée.
