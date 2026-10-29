# Écran 15 · Agenda (synchronisé avec Google Agenda)

> **Hors V1 (décision G Q25, 29 octobre 2026)** : conservé pour plus tard, aucune tâche ni module `scheduling` dans le plan C ni dans l'orchestration D. Le DDL des tables est gardé en E6.2. Rien de cette fiche n'est à implémenter en V1.

> Spécification pour agents. Maquette : `15-agenda.html` / `.png`. Palette : Nordique. Module backend `scheduling` (à ajouter au document A le jour où l'agenda sera repris) ; front `features/agenda`. À placer après la V1.

## Objectif
Le praticien voit sa semaine, planifie et déplace ses séances, voit ses indisponibilités Google sans les copier, et ouvre le détail d'un rendez-vous **à droite** d'un clic — avec de quoi préparer la séance et la démarrer. L'application reste la source de vérité des séances ; Google Agenda est un miroir bidirectionnel limité à ce que le praticien accepte d'y mettre.

## Décision de confidentialité (à valider)
Google Agenda n'est pas un hébergeur de données de santé. Par défaut, l'événement copié dans Google s'intitule **« Séance · M. D. »** (initiales), porte la modalité et la durée, et **rien d'autre** (pas de nom complet, pas de motif, pas de note). Préférence `calendar.googleDetail` : `initials` (défaut) · `first_name` · `busy_only` (« Occupé »). La note de planification n'est jamais synchronisée.

## Bibliothèques
- **Interface** : FullCalendar cœur (MIT) via `@fullcalendar/angular` — plugins `daygrid`, `timegrid`, `list`, `interaction` (glisser-déposer, redimensionnement), `rrule` (récurrence), locale `fr`. Thème par surcharge CSS avec les tokens Nordique (`--fc-*`). Le panneau de détail, le rail gauche et la barre d'outils sont des composants maison (Material M3). Alternatives écartées : `angular-calendar` (moins complet), Schedule-X (trop jeune), Bryntum / Syncfusion / Kendo (payants).
- **Backend** : `google-api-services-calendar` + `google-auth-library-oauth2-http` (Spring Boot). Flux iCal via `biweekly` ou `ical4j`.

## Route & module
- Route : `/agenda` (vue semaine par défaut), `/agenda?view=day|week|month|list&date=`, `/agenda/rdv/:id` ouvre le détail.
- Module `scheduling` : dépend de `identity` et `clinical` (pour créer / rattacher une `entry_session`), `events`. Expose `AppointmentFacade`, événements `AppointmentPlanned`, `AppointmentRescheduled`, `AppointmentCancelled`, `AppointmentStarted`.
- Tables :
  - `appointment (id, practitioner_id, patient_id, starts_at, ends_at, modality in_person|video|phone, status planned|confirmed|cancelled|done, planning_note, session_entry_id NULL, google_event_id, google_etag, source app|google, created_at, updated_at)` ; index `(practitioner_id, starts_at)`.
  - `practitioner_integration (practitioner_id, provider 'google', refresh_token_enc, scope, target_calendar_id, watch_channel_id, watch_resource_id, watch_expires_at, sync_token, last_sync_at, last_error, detail_level, read_calendars jsonb)`.
- API : `GET /api/agenda?from&to` → séances + blocs « occupé » des agendas Google en lecture (`{start, end, calendar}`, sans titre) ; `POST /api/appointments`, `PATCH /api/appointments/{id}` (déplacer, modalité, note), `POST /api/appointments/{id}/cancel`, `POST /api/appointments/{id}/start` (crée l'`entry_session` et redirige vers le patient) ; `GET /api/me/integrations/google/connect` (OAuth), `POST …/disconnect`, `GET …/status` ; `POST /api/integrations/google/webhook` (notifications `events.watch`, vérifie `X-Goog-Channel-Token`) ; `GET /api/me/calendar.ics?token=` (flux secret, lecture seule).

## Synchronisation Google
1. **Connexion** : OAuth 2.0, scope `https://www.googleapis.com/auth/calendar.events` (+ `calendar.readonly` si le praticien coche des agendas en lecture) ; `refresh_token` chiffré en base ; création d'un agenda secondaire « PsyMate » dans le compte Google (`target_calendar_id`).
2. **App → Google** : à chaque `AppointmentPlanned/Rescheduled/Cancelled`, `events.insert/patch/delete` sur l'agenda « PsyMate », `extendedProperties.private.appointmentId`, titre selon `detail_level`, `etag` conservé ; échec → file de reprise (outbox) et bandeau « non synchronisé ».
3. **Google → App** : `events.watch` sur l'agenda « PsyMate » (canal renouvelé tous les 6 jours par tâche planifiée) ; à chaque notification, `events.list(syncToken)` → deltas ; déplacement ou suppression côté Google → `PATCH`/`cancel` de l'`appointment` avec `source = google` et trace « déplacé depuis Google le … » ; `410 Gone` sur le `syncToken` → resynchronisation complète.
4. **Agendas en lecture** (Pro, Perso) : `freebusy.query` sur la fenêtre affichée, rendu en hachuré « Occupé · Google Pro » sans titre ni détail ; jamais stocké.
5. **Conflits** : dernier `updated` gagne ; si les deux côtés ont changé en moins de 60 s, l'application gagne et renvoie l'événement.
6. **Validation Google OAuth** : scope sensible → vérification de l'application (politique de confidentialité, vidéo) ; sans validation, plafond 100 utilisateurs et écran « non vérifiée ».

## Structure de l'écran
1. **Barre d'application** (Patients · **Agenda**).
2. **Barre d'outils** : titre, *Aujourd'hui*, ‹ ›, plage affichée (« 26 octobre – 1er novembre 2026 · semaine 44 · 14 séances »), vues *Jour / Semaine / Mois / Liste* (`role="group"`, `aria-pressed`), état de synchronisation (vert « synchronisé · il y a n min », ambre « en attente », rouge « erreur, reconnecter »), bouton **+ Rendez-vous**.
3. **Rail gauche** (220 px) : mini-mois (aujourd'hui en noir, semaine affichée en teinte primaire), **Mes agendas** (cases : cabinet `#3E8E7E`, visio `#3A63C2`, contacts `#7F8EA8`), **Google Agenda** (connecté ; agendas en lecture avec vignette hachurée ; phrase sur le niveau de détail + lien *Régler*), légende (*à confirmer* pointillé, *indisponible* hachuré).
4. **Grille semaine** : 56 px d'heures + 7 colonnes, 8 h → 19 h, 52 px/heure (préférence `agenda.hours`), ligne de l'heure courante en `--primary` quand la semaine contient aujourd'hui, samedi travaillé en teinte, dimanche hachuré clair (non travaillé), demi-journées non travaillées hachurées clair. Événements : fond teinte + barre gauche 3 px couleur de modalité, nom, `HH:MM–HH:MM` mono ; *à confirmer* = fond blanc bordure pointillée ; sélectionné = fond plein couleur de modalité, texte blanc, halo teinte ; contact téléphonique = bandeau fin 14 px.
5. **Détail à droite** (320–360 px, `aside aria-label`) : bande supérieure couleur de modalité, étiquette « Séance planifiée · cabinet », bouton fermer ; patient (initiales, nom lié au dossier, âge, suivi depuis, drapeau) ; date et heures mono ; lieu, n° de séance, rythme ; **Préparer** (dernière séance, objectif, ce qui s'est passé depuis : ressentis, événements non commentés ; boutons *Vue d'ensemble*, *Timeline*) ; **Note de planification** (jamais envoyée à Google) ; **bloc Google** (ce qui est dans Google, historique « déplacé depuis Google ») ; rappel SMS (interrupteur, hors V1 : désactivé) ; pied : **Démarrer la séance** (primaire), *Déplacer*, *Dupliquer*, *Annuler* (rouge texte), aide clavier.

## Interactions
- Clic sur un événement → détail à droite sans changer de vue ; `Échap` ferme ; `↑ ↓` passent au rendez-vous précédent/suivant du jour ; double-clic → dossier patient.
- Glisser-déposer / redimensionner → `PATCH` immédiat + synchronisation Google ; confirmation inline si le déplacement crée un chevauchement avec un bloc Google « occupé » (« Chevauche Google Pro 14:00–16:00 — déplacer quand même ? »).
- **+ Rendez-vous** ou clic sur un créneau vide → panneau de création (patient par recherche, modalité, durée par défaut `sessions.defaultDurationMin`, récurrence simple « toutes les 1 / 2 / 4 semaines », note).
- **Démarrer la séance** → crée l'`entry_session` (durée pré-remplie), marque le rendez-vous `done`, ouvre la vue d'ensemble du patient avec la barre de saisie prête.
- Annuler → motif optionnel, événement supprimé de Google, rendez-vous conservé en `cancelled` (visible via filtre).
- Vue Liste : les 30 prochains jours, groupés par jour, même panneau de détail.

## Tokens
- Modalités : cabinet `#3E8E7E` / `#E2F1EE`, visio `#3A63C2` / `#E3EAF8`, contact `#7F8EA8` / `#EEF1F6` (mêmes couleurs que le type séance et la barre de saisie).
- Indisponible Google : `repeating-linear-gradient(135deg, #E4E8F0 0 3px, #FFFFFF 3px 6px)` + bordure `#D6DCE6`, texte `#5A6679`.
- Non travaillé : bandes `#F6F8FB` / `#FFFFFF` ; samedi travaillé : fond `#F7F9FC`, en-tête `#E3EAF8`.
- Sélection : fond plein modalité, halo 3 px de la teinte ; aujourd'hui dans le mini-mois : `#1E2633` / blanc.
- État de synchronisation : `--success`, `--warning`, `--danger` avec texte, jamais couleur seule.

## Accessibilité
- Chaque événement est un `<a href>` avec nom, heures et modalité dans le texte ; `aria-current="true"` sur le sélectionné.
- Blocs Google « occupé » : `role="img" aria-label` avec plage et agenda.
- Vue Liste = alternative complète à la grille pour lecteurs d'écran et mobile ; grille en `overflow-x: auto` sous 900 px.
- Glisser-déposer doublé par *Déplacer* (formulaire date/heure).

## Critères d'acceptation
- [ ] Créer un rendez-vous → événement « Séance · M. D. » visible dans l'agenda Google « PsyMate » en < 5 s ; `extendedProperties.private.appointmentId` renseigné.
- [ ] Déplacer l'événement dans Google → `appointment.starts_at` mis à jour en < 30 s (webhook), détail affiche « déplacé depuis Google ».
- [ ] `detail_level = busy_only` → titre Google « Occupé », aucune initiale.
- [ ] Agenda Pro en lecture : bloc hachuré 14:00–16:00 affiché, aucun titre Google stocké (test base).
- [ ] Révocation du token côté Google → état rouge « reconnecter », aucune perte de rendez-vous.
- [ ] Canal `watch` expiré → renouvelé par la tâche planifiée avant expiration (test avec horloge simulée).
- [ ] *Démarrer la séance* → `entry_session` créée avec `session_id` prêt, redirection vers `/patients/:id`.
- [ ] axe-core 0 violation ; parcours clavier : ouvrir un rendez-vous, le fermer, démarrer la séance.

## Hors périmètre
Prise de rendez-vous en ligne par les patients, rappels SMS/e-mail, salle d'attente visio, multi-praticiens (cabinet de groupe), Outlook / iCloud (le flux iCal couvre la lecture).
