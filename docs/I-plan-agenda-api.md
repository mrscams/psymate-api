# Document I – Plan d'action · Agenda (API), jalon M11

15 novembre 2026. Plan de réalisation de l'agenda côté API (`psymate-api`), module **`agenda`**. L'interface Angular (fiches 15, 15b) est un jalon web séparé ; la synchronisation Google Agenda est un jalon ultérieur (M12, non planifié ici). Ce document prépare la levée de la décision G Q25 ; tant que M11-01 n'est pas fusionnée, G Q25 reste la règle.

Sources : `docs/design/15-agenda.md`, `docs/design/15b-nouveau-rendez-vous.md` (spécifications), `psymate-web/docs/design/15-agenda.{html,png}` et `15b-nouveau-rendez-vous.{html,png}` (maquettes de référence, palette Nordique), `docs/E-modele-donnees.md` E6.2 (DDL d'origine), `docs/D-orchestration-backend.md` (couloirs, vagues, liste de contrôle D8), `docs/C-plan.md` C4 (gabarit de prompt).

## I1. Périmètre et décisions à inscrire dans G (M11-01)

Ce que l'API doit permettre, lu dans les maquettes : voir sa semaine (grille 8 h → 19 h, vues Jour / Semaine / Mois / Liste 30 jours, mini-mois), créer un rendez-vous depuis un créneau (patient, date, début, durée, modalité, répétition toutes les 1 / 2 / 4 semaines × N, note de planification, case « À confirmer »), voir le détail d'un rendez-vous (patient, horaires, lieu, rythme, note), le déplacer (glisser-déposer ou formulaire), le dupliquer (côté web : ouverture du panneau prérempli, aucune API), l'annuler, voir le voisinage d'un créneau (« suivi de Théo B. à 11:30, pas de chevauchement »), voir les conflits avant de créer.

Hors M11 : tout ce qui est Google (OAuth, agenda secondaire « PsyMate », `events.watch`, freebusy Google, blocs hachurés « Occupé · Google Pro », préférence `calendar.googleDetail`, `practitioner_integration`, colonnes `google_event_id`, `google_etag`, `source`), le flux iCal secret, le rappel SMS (déjà « hors V1 » dans la fiche), le bloc « Préparer » (le web le compose avec `GET /api/patients/{id}/overview` et la timeline existantes), le bouton du pied de panneau (« Démarrer la séance » ou « Démarrer 360 » : à trancher côté web, voir M11-08).

Décisions proposées (numéros à poser à la suite de Q49 dans G) :

| # | Question | Proposition |
| --- | --- | --- |
| Q50 | Nom du module et place dans l'architecture | Module **`agenda`** (et non `scheduling`), package `com.psymate.agenda`, dépendances autorisées `identity :: api`, `patients :: api`, `clinical :: api`, `events :: api`, `shared` (dépendances révisées par G Q61, M11-11 : `clinical :: api` retiré, I9). Structure par rôle dès le premier fichier (G Q33 : `api / controller / dto / service / repository / model / config`), `ROLE_FOLDER_MODULES += agenda`. JPA (module CRUD, G Q32) ; fenêtres et chevauchements en JPQL (aucun SQL natif nécessaire). `directory` et `identity.admin` n'en dépendent pas. Remplace la mention « module scheduling » de A3, A5, A11, CLAUDE.md, F. |
| Q51 | Agenda local d'abord, Google ensuite | M11 livre un agenda **sans** intégration externe. La migration `V12__agenda.sql` crée `appointment` **sans** les colonnes Google ni `source` ; `practitioner_integration` n'est pas créée. Le jalon Google (M12) ajoutera sa propre migration (`ALTER TABLE appointment ADD …`, `CREATE TABLE practitioner_integration`). E6.2 devient E3.14 « Agenda » avec le DDL de I2 ; le DDL Google reste en E6.2 pour M12. |
| Q52 | Statuts d'un rendez-vous | `planned` (défaut), `tentative` (case « À confirmer » de la fiche 15b, rendu pointillé dans la fiche 15), `cancelled`, `done`. Le statut `confirmed` de E6.2 disparaît : « confirmer » fait passer `tentative → planned` (`POST /api/appointments/{id}/confirm`). `done` est réservé au rattachement d'une séance (M11-08) ; aucune transition manuelle vers `done` en M11. |
| Q53 | Aucune suppression physique | Un rendez-vous ne se supprime jamais : annulation = `status = cancelled`, `cancelled_at`, `cancel_reason` facultatif. L'« Annuler » du toast après création d'une série (fiche 15b) est une annulation de série (`POST /api/appointments/series/{seriesId}/cancel`), pas une suppression. `appointment` n'entre pas dans la liste E « Suppressions physiques autorisées ». |
| Q54 | Chevauchements | Un chevauchement avec un autre rendez-vous du praticien **n'empêche jamais** la création ni le déplacement (fiches 15 et 15b) : il est signalé (`conflicts[]` dans la réponse de création / modification, et `GET /api/agenda/freebusy`). Les rendez-vous `cancelled` ne comptent pas. |
| Q55 | Séries | Répétition hebdomadaire d'un pas de 1, 2 ou 4 semaines, par nombre d'occurrences ou jusqu'à une date, **26 occurrences au plus** (`422 TOO_MANY_OCCURRENCES` au-delà). Chaque occurrence est une ligne `appointment` indépendante (même `series_id`), déplaçable et annulable seule ; aucune règle de récurrence stockée. Aucun décalage automatique sur jour férié. |
| Q56 | Défauts de création | Durée et modalité par défaut : `patient.follow_up.durationMin` / `.modality` s'ils existent, sinon préférence `sessions.defaultDurationMin` (60) / `in_person`. L'API les **expose** (`GET /api/patients/{id}/appointments` → `defaults`), le web préremplit ; `durationMin` et `modality` restent obligatoires dans `POST /api/appointments`. Rythme affiché (« toutes les 1 à 2 semaines ») : `patient.follow_up.rhythm`, texte libre, jamais interprété par l'API. |
| Q57 | Préférences de l'agenda | Clés plates (ADR-11) dans `practitioner.preferences` : `agenda.dayStartHour` (défaut 8), `agenda.dayEndHour` (défaut 19), `agenda.workingDays` (défaut `[1,2,3,4,5,6]`, lundi = 1 ; samedi travaillé et dimanche non travaillé comme la maquette), `agenda.defaultView` (`week`, parmi `day | week | month | list`). Lecture et écriture par `PATCH /api/me/preferences` existant. La fiche 15 nommait `agenda.hours` : remplacée par les deux bornes. |
| Q58 | Portée et accès | Un praticien ne voit et ne modifie que ses rendez-vous ; le rendez-vous d'un autre praticien répond `404 APPOINTMENT_NOT_FOUND` (pas de 403, pour ne rien révéler). Le patient doit appartenir au praticien (`PatientOwnershipGuard`, 403 / 404 des patients) et ne pas être archivé (`422 PATIENT_ARCHIVED`). Un compte suspendu n'accède pas à l'agenda (chaîne de sécurité existante). |
| Q59 | Événements de domaine | `AppointmentPlanned`, `AppointmentRescheduled`, `AppointmentCancelled`, `AppointmentConfirmed` publiés par `DomainEventPublisher` dès M11, sans abonné en V1 : ce sont les hooks de M12 (outbox vers Google). Payload : `appointmentId`, `practitionerId`, `patientId`, `seriesId`, `startsAt`, `endsAt`, `status`. |

## I2. Modèle de données (migration `V12__agenda.sql`, à reporter dans E3.14 et E4)

```sql
-- Agenda (fiches 15, 15b) — rendez-vous planifiés, sans synchronisation externe (G Q51)
CREATE TABLE appointment (
  id                uuid PRIMARY KEY,
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  practitioner_id   uuid NOT NULL REFERENCES practitioner(id),
  patient_id        uuid NOT NULL REFERENCES patient(id),
  series_id         uuid,                               -- commun aux occurrences d'une répétition (G Q55)
  starts_at         timestamptz NOT NULL,
  ends_at           timestamptz NOT NULL,
  modality          text NOT NULL CHECK (modality IN ('in_person','video','phone')),
  status            text NOT NULL DEFAULT 'planned' CHECK (status IN ('planned','tentative','cancelled','done')),
  planning_note     text,                               -- visible seulement ici ; jamais synchronisée (fiche 15)
  cancel_reason     text,
  cancelled_at      timestamptz,
  session_entry_id  uuid REFERENCES entry(id),          -- séance rattachée (M11-08), NULL sinon
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_appointment_order CHECK (ends_at > starts_at),
  CONSTRAINT ck_appointment_cancel CHECK ((status = 'cancelled') = (cancelled_at IS NOT NULL))
);
CREATE INDEX ix_appointment_practitioner_time ON appointment(practitioner_id, starts_at);
CREATE INDEX ix_appointment_patient ON appointment(patient_id, starts_at DESC);
CREATE INDEX ix_appointment_series ON appointment(series_id) WHERE series_id IS NOT NULL;
```

Propriétaire : module `agenda` (`TableOwnershipTests.TABLE_OWNERS += appointment → agenda`). `session_entry_id` référence `entry` par clé étrangère SQL mais **jamais** par association JPA (UUID nu, règle ArchUnit). E4 : `V12__agenda.sql` · M11-02 (agenda) · vague M11-A · « E3.14 `appointment` » · dépend de V01, V05.

## I3. Contrat d'API (version `0.12.x`, tag unique `contract-v0.12.1` posé par M11-09 ; `0.13.0` avec les rendez-vous invités, M11-10, I8)

Toutes les routes exigent une session praticien. Erreurs RFC 9457, `errors[{field, code, message}]`, codes repris des autres modules (`REQUIRED`, `INVALID_FORMAT`, `INVALID_VALUE`, `OUT_OF_RANGE`, `UNKNOWN_FIELD`) plus ceux de l'agenda (`PATIENT_ARCHIVED`, `TOO_MANY_OCCURRENCES`, `WINDOW_TOO_LARGE`, `APPOINTMENT_NOT_FOUND`, `APPOINTMENT_NOT_ACTIVE`, `SERIES_NOT_FOUND`). Dates UTC ISO 8601 ; le web convertit dans le fuseau du praticien.

| Route | Tâche | Rôle | Réponse |
| --- | --- | --- | --- |
| `POST /api/appointments` | M11-05 (une occurrence), M11-07 (`recurrence`) | Créer depuis un créneau. Corps `{ patientId, startsAt, durationMin (5–600), modality, planningNote? (≤ 2 000), tentative? (défaut false), recurrence?: { everyWeeks: 1, 2 ou 4, count? (≤ 26) ou until? } }` ; exactement un de `count` / `until` si `recurrence` est présent. M11-10 : `patientId` facultatif, nouveau `guest?: { firstName? (≤ 100), lastName (≤ 100), phone? (≤ 30) }`, exactement un des deux (aucun → `422 REQUIRED` sur `patientId` ; les deux → `422 INVALID_VALUE` sur `guest`). | `201`, `Location: /api/appointments/{id}` (première occurrence), corps `{ appointments: [AppointmentDto…], conflicts: [ConflictDto…] }` |
| `GET /api/appointments/{id}` | M11-05 | Détail du panneau droit. | `AppointmentDto` |
| `PATCH /api/appointments/{id}` | M11-05 | Déplacer, changer la durée, la modalité ou la note (fusion partielle, `null` efface la note). Refusé si `cancelled` ou `done` (`409 APPOINTMENT_NOT_ACTIVE`). M11-10 : `guest?` corrige l'identité d'un invité (`lastName` remplacé, `firstName` / `phone` absents gardés, vides effacés) ; `422 INVALID_VALUE` sur `guest` si le rendez-vous vise un patient ou si l'invité est purgé. | `{ appointment, conflicts }` |
| `POST /api/appointments/{id}/attach-patient` | M11-10 | Corps `{ patientId, includeSameGuest?: false }`. Rattache un rendez-vous invité à un patient du praticien non archivé (403 / 404 / `422 PATIENT_ARCHIVED` comme la création) ; `409 APPOINTMENT_NOT_ACTIVE` s'il vise déjà un patient ou si l'invité est purgé. Avec `includeSameGuest`, tous les rendez-vous invités non purgés du praticien de même identité (casse et espaces ignorés). Événement `AppointmentPatientAttached` par rendez-vous. | `{ appointments }` (`AttachedAppointmentsDto`) |
| `POST /api/appointments/{id}/cancel` | M11-05 | Corps `{ reason? }`. Idempotent sur un rendez-vous déjà annulé (`200`). Refusé si `done`. | `AppointmentDto` |
| `POST /api/appointments/{id}/confirm` | M11-05 | `tentative → planned` ; `409` sinon. | `AppointmentDto` |
| `POST /api/appointments/series/{seriesId}/cancel` | M11-07 | Annule toutes les occurrences encore actives de la série (`planned`, `tentative`). | `{ appointments }` |
| `GET /api/agenda?from&to&includeCancelled=false` | M11-06 | Fenêtre de la grille et de la vue Liste : `to - from ≤ 62 jours` (`422 WINDOW_TOO_LARGE`), tri `startsAt`. Chevauchement de fenêtre (`startsAt < to AND endsAt > from`). | `{ from, to, appointments: [AppointmentDto…], busy: [] }` — `busy` vide en M11, réservé aux blocs Google (M12) |
| `GET /api/agenda/freebusy?from&to&excludeAppointmentId?&tz?` | M11-06 | Conflits d'un créneau avant création ou déplacement (débounce côté web) ; renvoie aussi le rendez-vous précédent et le suivant du même jour (« suivi de Théo B. à 11:30 »). | `{ conflicts: [ConflictDto…], previous?: AppointmentDto, next?: AppointmentDto }` |
| `GET /api/patients/{id}/appointments?from?&limit=20` | M11-06 | Rendez-vous d'un patient à partir de `from` (défaut : maintenant), actifs seulement, plus les défauts de création. | `{ appointments, defaults: { durationMin, modality, rhythm? } }` |
| `POST /api/appointments/{id}/start` | M11-08 (conditionnelle) | Crée l'`entry_session` du patient, rattache `session_entry_id`, `status = done`. | `{ appointment, sessionId }` |

`AppointmentDto` : `{ id, patient: { id, displayName, birthDate, archived }, seriesId?, startsAt, endsAt, durationMin, modality, status, planningNote?, cancelReason?, cancelledAt?, sessionEntryId?, createdAt, updatedAt }`. `ConflictDto` : `{ appointmentId, startsAt, endsAt, patientDisplayName }` (M11-07 y ajoute `occurrenceStartsAt`). M11-10 : `AppointmentDto.patient` devient nullable et `AppointmentDto.guest` (`GuestDto { firstName, lastName, phone, purgedAt }`, nullable) apparaît ; pour un invité, `ConflictDto.patientDisplayName` vaut « Prénom Nom », ou « Invité purgé » après la purge. Un fichier par DTO, MapStruct pour `AppointmentMapper` (entité → DTO) ; la composition patient + rendez-vous est un `AppointmentAssembler` (G Q46).

## I4. Tâches, couloirs et parallélisme

| ID | Titre | Couloir | Dépend de | Parallèle avec |
| --- | --- | --- | --- | --- |
| M11-01 | Lever G Q25, inscrire Q50–Q59 dans G, A, C, D, E, CLAUDE.md, F ; agents en Opus | docs (+ `.claude/agents`) | — | M11-02, M11-03, M11-04 |
| M11-02 | Module `agenda` : squelette, `V12__agenda.sql`, entité, repository, gardes | agenda | — (ce document fait foi en attendant M11-01) | M11-01, M11-03, M11-04 |
| M11-03 | `PatientFacade.followUp` (durée, modalité, rythme du suivi) | patients (api) | — | M11-01, M11-02, M11-04 |
| M11-04 | Préférences `agenda.*` (Q57) | identity (preferences) | — | M11-01, M11-02, M11-03 |
| M11-05 | Cycle de vie d'un rendez-vous : créer, lire, déplacer, annuler, confirmer, événements | agenda (fichiers `Appointment*`) | M11-02 | M11-06 (fichiers disjoints, voir ci-dessous) |
| M11-06 | Lectures : fenêtre `GET /api/agenda`, `freebusy`, rendez-vous d'un patient | agenda (fichiers `Agenda*`) | M11-02, M11-03 | M11-05 |
| M11-07 | Séries : `recurrence`, `series_id`, annulation de série | agenda | M11-05, M11-06 | — |
| M11-08 | *(conditionnelle)* Rattacher une séance : `SessionFacade` dans `clinical.api`, puis `POST /{id}/start` (**Remplacé par G Q61** : agenda ne dépend plus de clinical ; si M11-08 revient, passer par un événement ou deux appels du web.) | clinical (api) puis agenda | M11-05 ; décision web sur le bouton du panneau | — |
| M11-09 | Seed `make seed` (rendez-vous sur 8 semaines), contrat tagué, suivi | racine (`tools/seed`) | M11-07 | — |
| M11-10 | Rendez-vous invité (G Q60) : `V13__agenda_guest.sql`, `guest` à la création et au PATCH, `attach-patient`, purge à 6 mois, contrat `0.13.0` (I8) | agenda (+ docs) | M11-09 | — |
| M11-11 | Export des rendez-vous (G Q61) : `AppointmentExportFacade` (agenda.api), `rendez-vous.md` dans l'export du compte, section « Rendez-vous » dans l'export d'un dossier ; dépendances `agenda` sans `clinical :: api`, `clinical` + `agenda :: api` (I9) | agenda (api) + clinical.export (+ docs) | M11-10 | — |

**Vagues.** M11-A : 01, 02, 03, 04 (quatre terminaux). M11-B : 05 et 06 dès que 02 (et 03 pour 06) sont fusionnées. M11-C : 07. M11-D : 09, et 08 si retenue (son couloir `clinical` n'entre en conflit avec rien d'autre de M11, mais il tourne seul dans `clinical`).

**Exception à D5 pour M11-05 ‖ M11-06.** Même couloir, mais fichiers disjoints par construction : M11-02 crée tout ce qui est partagé (entité, repository, `AgendaExceptionHandler`, `AgendaMessages`, `package-info`, enregistrements ArchUnit). M11-05 ne crée que des fichiers préfixés `Appointment` (`AppointmentController`, `AppointmentService`, `CreateAppointmentRequest`, `UpdateAppointmentRequest`, `CancelAppointmentRequest`, `AppointmentDto`, `AppointmentMapper`, `AppointmentAssembler`, `AppointmentCreatedDto`, `AppointmentUpdatedDto`, `ConflictDto`, événements `api/Appointment*`). M11-06 ne crée que `AgendaController`, `AgendaQueryService`, `AgendaWindowDto`, `FreeBusyDto`, `PatientAppointmentsDto`, `AppointmentDefaultsDto`, et des méthodes de lecture ajoutées à `AppointmentRepository` (seul fichier commun : la seconde PR fusionnée rebase). `AppointmentDto`, `AppointmentMapper`, `AppointmentAssembler` et `ConflictDto` appartiennent à M11-05 ; M11-06 les crée **provisoirement identiques** si M11-05 n'est pas encore fusionnée et le signale dans sa PR, la seconde PR fusionnée garde une seule version. `messages_fr.properties` / `messages_en.properties` : chacun ajoute ses clés sous un commentaire `# agenda (M11-0x)`. Contrat : les deux visent `app.contract.version = 0.12.0` ; la seconde fusionnée relance `make contract`.

**Agents.** Réalisation : `backend-module-dev` (M11-02 à M11-08), `seed-perf` (M11-09), Claude directement pour M11-01 (docs). Revue : `reviewer` avant chaque fusion, puis `make log`. Les trois fichiers `.claude/agents/*.md` portent encore `model: sonnet` : M11-01 les passe à `model: opus` (Opus 5.5), conformément à la pratique du projet.

**Liste de contrôle D8** inchangée : dépendances fusionnées, CI de `main` verte, couloir libre (hors l'exception ci-dessus), branche `M11-0x-<sujet>` depuis `main` à jour, `./mvnw verify` vert, revue, `make log`.

## I5. Prompts

Manœuvre : `git checkout main && git pull && claude --worktree <ID>`, coller le prompt. À la fin : `Lance le sous-agent reviewer sur cette branche et corrige ce qu'il marque bloquant.`, lecture humaine (migration, diff `contract/openapi.yaml`, un IT), `gh pr merge <n> --squash --delete-branch`.

### M11-01 — Lever G Q25 et inscrire l'agenda dans la documentation

```text
Tâche M11-01 — Lever G Q25 et inscrire le jalon M11 Agenda (API) dans la documentation (dépôt psymate-api, docs/ et .claude/agents/ seulement ; aucun code Java, aucune migration).

Contexte : lis docs/I-plan-agenda-api.md en entier (il fait foi pour cette tâche), docs/G-decisions.md (Q25, Q32, Q33, Q46 et le format des lignes Q), docs/A-architecture.md A3, A5, A11 et la ligne 192 (« aucun contexte scheduling »), docs/C-plan.md C1, C7, C8 (format des tables de tâches M9/M10) et C9 (règles communes d'un jalon de contrat), docs/D-orchestration-backend.md D1, D5, D8, docs/E-modele-donnees.md E1 (tableau des tables par module), E4, E6.2, E7 O5, docs/F-runbook-agents-backend.md (lignes 59, 125, 1462 et le lexique), CLAUDE.md ligne 30, docs/couverture-ecrans.md (mention des fiches 15, 15b).

Objectif :
- G : Q25 reste dans le tableau mais son texte devient « Révisée le 15 novembre 2026 par Q50–Q59 : agenda local en M11 (module agenda), synchronisation Google en M12 » ; ajoute Q50 à Q59 en copiant le tableau I1 de docs/I-plan-agenda-api.md (une ligne par question, même style que Q46–Q49, datées du 15 novembre 2026, « tranchées par l'utilisateur »).
- A : A3 et A5 ajoutent le module agenda (dépendances Q50, façade exposée : aucune en M11, événements Q59) ; la phrase « aucun contexte scheduling » et A11 sont réécrites ; A4 reçoit une courte sous-section « Agenda » renvoyant à I3 pour le contrat.
- C : C1 ajoute le jalon M11 (objectif, démontrable, effort indicatif 6 j) ; nouvelle section C11 « Agenda (API), module agenda » avec le tableau des tâches M11-01 à M11-09 au format de C8/C9 (colonnes ID, Titre, Dépôt / module, Lire, Quoi construire, Critères d'acceptation, Tests, Dépend de), les critères repris mot pour mot de I5 ; C7 précise que l'agenda n'est plus hors V1.
- D : D1 ajoute les lignes M11-0x (couloirs de I4), D5 ajoute le couloir agenda et l'exception M11-05 ‖ M11-06 telle que rédigée en I4, D3 ajoute les vagues M11-A à M11-D.
- E : E1 ajoute appointment → agenda ; nouvelle section E3.14 « Agenda » avec le DDL exact de I2 ; E4 ajoute la ligne V12__agenda.sql ; E6.2 ne garde que practitioner_integration et les colonnes Google (google_event_id, google_etag, source) sous le titre « Synchronisation Google — M12, hors M11 » ; E7 O5 est mis à jour.
- CLAUDE.md : la ligne « L'agenda Google (fiches 15, 15b) est hors V1 (G, Q25) : aucun module scheduling. » devient « L'agenda est le module agenda (G Q50) ; la synchronisation Google est hors V1 (M12) : aucun appel externe, aucune colonne Google. » ; la liste des modules de la règle « Un module Spring Modulith par contexte » ajoute agenda.
- F : les trois mentions sont alignées (lignes 59, 125, 1462) ; aucun nouveau lot n'est rédigé dans F (les prompts vivent dans I5).
- .claude/agents/backend-module-dev.md, reviewer.md, seed-perf.md : model: sonnet → model: opus. L'agent backend-module-dev ajoute à sa liste de lecture « docs/I-plan-agenda-api.md pour toute tâche M11 ».
- docs/design/15-agenda.md et 15b-nouveau-rendez-vous.md : le bandeau « Hors V1 » devient « API en M11 (module agenda, docs/I-plan-agenda-api.md) ; web et Google : jalons ultérieurs » ; aucune autre modification des fiches.

Périmètre : docs/**, CLAUDE.md, .claude/agents/*.md. Rien d'autre.

Critères d'acceptation :
- grep -rn "scheduling" docs CLAUDE.md .claude ne renvoie plus que des mentions historiques explicitement datées (« ancien nom scheduling ») ;
- chaque tâche M11-0x de I4 a sa ligne dans C11 et D1 avec les mêmes dépendances ;
- le DDL de E3.14 est identique octet pour octet au bloc SQL de I2 ;
- G Q50–Q59 existent et Q25 renvoie vers elles ;
- les trois agents sont en model: opus.

Tests : aucun test Java ; ./mvnw verify doit rester vert (aucune source touchée).

Interdits : toute modification de code, de migration, de contrat ; toute réécriture de docs/progress/index.html ou d'un événement existant ; toute modification du contenu des maquettes.

Livrable : PR « M11-01 Agenda : décisions et documentation », puis make log TASK=M11-01 AGENT=claude STATUS=done SUMMARY="…" PR=<url>.
```

### M11-02 — Module `agenda` : squelette, migration V12, entité et gardes

```text
Tâche M11-02 — Module agenda : squelette, migration V12__agenda.sql, entité Appointment, repository, gardes d'architecture (dépôt psymate-api, module agenda). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md (I1 Q50 à Q53, I2 en entier, I4 « Exception à D5 »), docs/G-decisions.md Q32, Q33, Q35, Q38, docs/A-architecture.md A5 (dossiers par rôle), docs/E-modele-donnees.md E2 (conventions SQL), docs/etudes/jpa-conversion-checklist.md. Regarde comment directory (module JPA récent et petit) est construit : src/main/java/com/psymate/directory/package-info.java, model/, repository/, controller/DirectoryExceptionHandler.java ; et les gardes src/test/java/com/psymate/{ModularityTests,ArchitectureTests,RoleFolderTests,TableOwnershipTests,FlywayMigrationIT}.java. Si M11-01 n'est pas encore fusionnée, docs/I-plan-agenda-api.md fait foi sur G Q25 pour cette tâche : dis-le dans la PR.

Objectif :
- src/main/resources/db/migration/V12__agenda.sql : copie exacte du bloc SQL de I2 (aucune colonne Google, aucune table practitioner_integration).
- package com.psymate.agenda avec package-info.java : @ApplicationModule(allowedDependencies = {"identity :: api", "patients :: api", "clinical :: api", "events :: api", "shared"}). (Dépendance clinical :: api révisée par G Q61 : retirée par M11-11.)
- model/Appointment.java : entité JPA de la table appointment (UUID nus pour tenant_id, practitioner_id, patient_id, series_id, session_entry_id ; aucune association JPA ; enums AppointmentModality {in_person, video, phone} et AppointmentStatus {planned, tentative, cancelled, done} persistés en texte ; updated_at mis à jour par l'entité) ; model/AppointmentException.java (sous-classe de ProblemException du module shared, codes APPOINTMENT_NOT_FOUND 404, APPOINTMENT_NOT_ACTIVE 409, PATIENT_ARCHIVED 422, TOO_MANY_OCCURRENCES 422, WINDOW_TOO_LARGE 422, SERIES_NOT_FOUND 404).
- repository/AppointmentRepository.java : JpaRepository<Appointment, UUID> avec findByIdAndPractitionerId ; rien de plus (M11-05 et M11-06 ajoutent leurs méthodes).
- controller/AgendaExceptionHandler.java et controller/AgendaMessages.java sur le modèle de clinical (RFC 9457, errors[] par champ, messages i18n) ; clés dans messages_fr.properties (et messages_en.properties) sous « # agenda (M11-02) ».
- Gardes : ModularityTests connaît le module ; RoleFolderTests.ROLE_FOLDER_MODULES += "agenda" ; TableOwnershipTests.TABLE_OWNERS += appointment → agenda ; FlywayMigrationIT et le schéma attendu incluent V12 ; ArchitectureTests vérifie que le module n'a aucune association JPA vers une autre table et n'expose aucune entité.
- Test AgendaPersistenceIT (Testcontainers pgvector/pgvector:pg16, base réelle) : insérer, relire, modifier un Appointment ; les contraintes ck_appointment_order et ck_appointment_cancel sont vérifiées (insertion invalide → exception) ; ddl-auto=validate passe.

Périmètre : src/main/java/com/psymate/agenda/**, src/main/resources/db/migration/V12__agenda.sql, src/main/resources/messages_*.properties (ajout seulement), src/test/java/com/psymate/agenda/**, et uniquement les lignes d'enregistrement dans les cinq classes de gardes citées.

Critères d'acceptation :
- ./mvnw verify vert avec le module agenda déclaré et vide de tout contrôleur ;
- V12 s'applique sur une base vierge et le schéma attendu la liste ;
- une dépendance interdite (agenda → clinical interne, ou directory → agenda) fait échouer ApplicationModules.verify() : démontre-le dans la PR puis retire-la ;
- aucune entité dans un DTO ni un contrôleur (il n'y en a pas encore) ; aucune association JPA inter-modules ;
- contrat OpenAPI inchangé (ContractIT vert sans make contract).

Interdits : tout endpoint ; toute dépendance Maven ; toute colonne absente de I2 ; toute modification de E ou de G (c'est M11-01) ; toute migration existante.

Livrable : PR « M11-02 Module agenda : squelette et V12 », ./mvnw verify vert, make log.
```

### M11-03 — `PatientFacade.followUp`

```text
Tâche M11-03 — Exposer le suivi d'un patient aux autres modules : PatientFacade.followUp (dépôt psymate-api, module patients, package api et son implémentation). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md I1 Q56, docs/E-modele-donnees.md E3.4 (colonne patient.follow_up jsonb : { since, rhythm, modality, durationMin, fee, prescriber }), src/main/java/com/psymate/patients/api/PatientFacade.java et son implémentation dans patients/service, src/main/java/com/psymate/patients/dto/PatientDto.java (comment follow_up est exposé aujourd'hui), src/test/java/com/psymate/patients/** (tests de la façade).

Objectif : dans patients.api, un record FollowUpView(LocalDate since, String rhythm, String modality, Integer durationMin) et une méthode Optional<FollowUpView> followUp(UUID patientId) sur PatientFacade : lecture seule, sans contrôle d'accès (l'appelant passe d'abord par PatientOwnershipGuard, comme displayName et birthDate), champs absents du JSON → null, patient inconnu → Optional.empty(). fee et prescriber ne sont jamais exposés (données financières et tiers : hors besoin de l'agenda). modality est rendue telle quelle (texte libre côté patient) ; durationMin seulement si c'est un entier entre 1 et 600, sinon null.

Périmètre : src/main/java/com/psymate/patients/api/FollowUpView.java (nouveau), PatientFacade.java, son implémentation, tests de patients. Aucun autre module.

Critères d'acceptation :
- un patient avec follow_up complet → les quatre champs ; follow_up vide ({}) → Optional présent avec quatre null ; patient inexistant → vide ;
- fee et prescriber absents de FollowUpView ;
- durationMin = "soixante" ou 900 → null, sans exception ;
- contrat OpenAPI inchangé.

Tests : PatientFacadeIT (ou extension du test de façade existant) couvrant les quatre cas ci-dessus, sur base réelle.

Interdits : exposer l'entité, modifier PatientDto ou un endpoint, toucher au module agenda.

Livrable : PR « M11-03 PatientFacade.followUp », ./mvnw verify vert, make log.
```

### M11-04 — Préférences `agenda.*`

```text
Tâche M11-04 — Préférences de l'agenda (dépôt psymate-api, module identity, sous-paquet preferences). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md I1 Q57, docs/A-architecture.md ADR-11 (clés plates), src/main/java/com/psymate/identity/preferences/Preferences.java et PreferencesSchema.java (comment sessions.defaultDurationMin est déclaré, validé et documenté dans le contrat), les tests de preferences dans src/test/java/com/psymate/identity/**.

Objectif : quatre clés plates ajoutées au schéma, avec défauts et validation : agenda.dayStartHour (entier 0–23, défaut 8), agenda.dayEndHour (entier 1–24, défaut 19, strictement supérieur à dayStartHour : 422 OUT_OF_RANGE sur agenda.dayEndHour sinon), agenda.workingDays (liste d'entiers 1–7 sans doublon, lundi = 1, défaut [1,2,3,4,5,6]), agenda.defaultView (day, week, month ou list, défaut week). Elles passent par PATCH /api/me/preferences et GET /api/me/preferences existants, apparaissent dans PractitionerFacade.preferencesOf (valeurs effectives avec défauts), et dans le contrat OpenAPI (schéma des préférences).

Périmètre : src/main/java/com/psymate/identity/preferences/**, ses tests, contract/openapi.yaml (make contract), app.contract.version → 0.11.1.

Critères d'acceptation :
- GET sans écriture préalable renvoie les quatre défauts ;
- PATCH { "agenda.dayEndHour": 7 } avec dayStartHour à 8 → 422 avec errors[0].field = "agenda.dayEndHour", code OUT_OF_RANGE ;
- PATCH { "agenda.workingDays": [1,1,8] } → 422 ; [6,7] → 200 et relu tel quel ;
- PATCH { "agenda.defaultView": "year" } → 422 INVALID_VALUE ;
- preferencesOf contient les clés agenda.* avec défauts ;
- contrat regénéré, version bumpée.

Tests : extension de PreferencesIT (ou équivalent existant) pour chaque critère.

Interdits : nouvelle table ou colonne ; toute clé calendar.* ou Google ; toucher au module agenda.

Livrable : PR « M11-04 Préférences agenda.* », ./mvnw verify vert, make log.
```

### M11-05 — Cycle de vie d'un rendez-vous

```text
Tâche M11-05 — Cycle de vie d'un rendez-vous : créer, lire, déplacer, annuler, confirmer, événements (dépôt psymate-api, module agenda, fichiers Appointment* seulement). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md (I1 Q52 à Q54, Q58, Q59 ; I3 lignes POST /api/appointments sans recurrence, GET /{id}, PATCH /{id}, POST /{id}/cancel, POST /{id}/confirm ; I4 « Exception à D5 » : la liste exacte des fichiers que tu as le droit de créer), docs/design/15-agenda.md « Détail à droite » et « Interactions », docs/design/15b-nouveau-rendez-vous.md « Route & module » et « Structure » (ce que le panneau envoie), docs/G-decisions.md Q46 (Mapper / Assembler), Q33, Q35. Code existant à imiter : src/main/java/com/psymate/clinical/controller/SessionController.java (annotations OpenAPI, 201 + Location, 409), clinical/controller/FieldProblems.java (codes d'erreur par champ), clinical/controller/ForbiddenFieldsAdvice.java, patients/api/PatientOwnershipGuard.java, PatientFacade.java, identity/api/CurrentPractitioner.java, events/api/DomainEventPublisher.java et un événement existant (clinical/api/EntryCreated.java). Le module agenda existe déjà (M11-02) : entité Appointment, AppointmentRepository, AgendaExceptionHandler, AgendaMessages, AppointmentException.

Objectif :
- api/AppointmentPlanned, AppointmentRescheduled, AppointmentCancelled, AppointmentConfirmed : records DomainEvent (payload Q59), publiés après commit par le service.
- dto/CreateAppointmentRequest { patientId, startsAt, durationMin, modality, planningNote?, tentative? } (Jakarta Validation : REQUIRED, OUT_OF_RANGE 5–600, INVALID_VALUE sur modality, planningNote ≤ 2000 ; recurrence est REFUSÉE dans cette tâche avec UNKNOWN_FIELD : M11-07 l'ajoutera), dto/UpdateAppointmentRequest { startsAt?, durationMin?, modality?, planningNote? } (fusion partielle, null explicite efface la note : distingue absent et null), dto/CancelAppointmentRequest { reason? ≤ 500 }, dto/AppointmentDto, dto/ConflictDto, dto/AppointmentCreatedDto { appointments, conflicts }, dto/AppointmentUpdatedDto { appointment, conflicts }, dto/AppointmentMapper (MapStruct, entité → AppointmentDto sans le bloc patient), dto/AppointmentAssembler (ajoute patient { id, displayName, birthDate, archived } via PatientFacade et PatientRef).
- service/AppointmentService : création (tenant_id et practitioner_id depuis CurrentPractitioner ; PatientOwnershipGuard.requireOwned ; patient archivé → 422 PATIENT_ARCHIVED ; ends_at = starts_at + durationMin ; status tentative si tentative = true sinon planned ; conflits = rendez-vous actifs du praticien qui chevauchent, jamais bloquants ; PatientFacade.touchClinicalRev n'est PAS appelé : un rendez-vous n'est pas une écriture clinique) ; lecture ; modification (409 APPOINTMENT_NOT_ACTIVE si cancelled ou done ; AppointmentRescheduled seulement si startsAt ou durationMin change) ; annulation (idempotente, cancelled_at, cancel_reason ; 409 si done) ; confirmation (tentative → planned, 409 sinon). Tout rendez-vous d'un autre praticien → 404 APPOINTMENT_NOT_FOUND.
- controller/AppointmentController : les cinq routes de I3, annotations OpenAPI complètes en français (summary, description, réponses, Location), champs refusés en écriture avec UNKNOWN_FIELD : authorType, submissionId, status, practitionerId, tenantId, sessionEntryId.
- Méthodes ajoutées à AppointmentRepository : les chevauchements actifs d'un praticien sur [from, to[ (JPQL, excluant un id), rien d'autre.
- make contract, app.contract.version = 0.12.0 (si M11-06 est déjà fusionnée avec 0.12.0, garde 0.12.0 et regénère).

Périmètre : src/main/java/com/psymate/agenda/{api,controller,dto,service}/Appointment*.java et ConflictDto.java, AppointmentRepository.java (ajout de méthodes), messages_*.properties (ajout sous « # agenda (M11-05) »), src/test/java/com/psymate/agenda/AppointmentIT.java et tests unitaires du service, contract/openapi.yaml. N'ouvre pas AgendaController ni AgendaQueryService (M11-06).

Critères d'acceptation :
- POST valide → 201, Location /api/appointments/{id}, corps { appointments: [1], conflicts: [] }, ligne en base avec tenant_id et practitioner_id du praticien courant, événement AppointmentPlanned dans event_publication ;
- POST sur un patient d'un autre praticien → 403 PATIENT_FORBIDDEN ; patient archivé → 422 PATIENT_ARCHIVED ; durationMin 3 → 422 OUT_OF_RANGE ; modality "email" → 422 INVALID_VALUE ; recurrence présente → 422 UNKNOWN_FIELD ; status présent → 422 UNKNOWN_FIELD ;
- POST chevauchant un rendez-vous existant du même praticien → 201 avec conflicts[0].appointmentId = l'existant ; un rendez-vous cancelled ne crée pas de conflit ;
- GET /{id} d'un autre praticien → 404 ; du praticien → AppointmentDto complet avec patient.displayName ;
- PATCH { startsAt } → 200, ends_at recalculé avec la durée inchangée, AppointmentRescheduled publié ; PATCH { planningNote: null } efface la note sans événement Rescheduled ; PATCH sur cancelled → 409 APPOINTMENT_NOT_ACTIVE ;
- cancel → status cancelled, cancelled_at renseigné, deuxième cancel → 200 inchangé ; cancel sur done (ligne préparée en SQL dans le test) → 409 ;
- confirm sur tentative → planned ; sur planned → 409 ;
- aucune ligne appointment n'est jamais supprimée (test : compte avant / après la suite) ;
- contrat regénéré, version 0.12.0, ContractIT vert.

Tests : AppointmentIT (Testcontainers, un cas par critère, deux praticiens), AppointmentServiceTest (unitaire : calcul de ends_at, transitions de statut, détection de chevauchement aux bornes : un rendez-vous qui finit à 10:00 ne chevauche pas un qui commence à 10:00).

Interdits : dépendance Maven ; suppression physique ; appel à clinical autre que la lecture (aucune création de séance ici) ; toute colonne ou table nouvelle ; toucher aux fichiers de M11-06 ; modifier V12.

Livrable : PR « M11-05 Rendez-vous : cycle de vie », ./mvnw verify vert, make log.
```

### M11-06 — Lectures : fenêtre, freebusy, rendez-vous d'un patient

```text
Tâche M11-06 — Lectures de l'agenda : fenêtre GET /api/agenda, GET /api/agenda/freebusy, GET /api/patients/{id}/appointments (dépôt psymate-api, module agenda, fichiers Agenda* et dto de lecture seulement). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md (I1 Q54, Q56, Q58 ; I3 les trois lignes GET ; I4 « Exception à D5 » : liste des fichiers autorisés et règle sur AppointmentDto / AppointmentMapper / AppointmentAssembler / ConflictDto), docs/design/15-agenda.md « Structure de l'écran » (barre d'outils : plage affichée, vues Jour / Semaine / Mois / Liste 30 jours ; grille) et docs/design/15b-nouveau-rendez-vous.md « Structure » points 2 et 3 (« dernière séance », « suivi de Théo B. à 11:30, pas de chevauchement », durée et modalité par défaut). Code à imiter : src/main/java/com/psymate/clinical/controller/TimelineController.java (fenêtre, validation des bornes, annotations OpenAPI), patients/api/PatientFacade.java (followUp, M11-03), PatientOwnershipGuard, identity/api/PractitionerFacade.java (preferencesOf pour sessions.defaultDurationMin), identity/api/CurrentPractitioner.java. Le module agenda existe (M11-02). Si M11-05 n'est pas encore fusionnée, crée AppointmentDto, AppointmentMapper, AppointmentAssembler, ConflictDto exactement selon I3 et dis-le dans la PR ; sinon réutilise-les sans les modifier.

Objectif :
- controller/AgendaController : GET /api/agenda?from&to&includeCancelled (from et to obligatoires, ISO 8601 UTC, from < to, to - from ≤ 62 jours sinon 422 WINDOW_TOO_LARGE sur le champ to ; rendez-vous du praticien courant tels que startsAt < to et endsAt > from, triés par startsAt ; cancelled exclus sauf includeCancelled=true ; réponse { from, to, appointments, busy: [] } avec busy toujours vide et documenté « réservé aux indisponibilités externes ») ; GET /api/agenda/freebusy?from&to&excludeAppointmentId&tz (conflits actifs chevauchant [from, to[ ; previous = dernier rendez-vous actif qui se termine au plus tard à from, next = premier qui commence au plus tôt à to, tous deux du même jour calendaire que from dans le fuseau tz, défaut Europe/Paris) ; GET /api/patients/{id}/appointments?from&limit (PatientOwnershipGuard ; actifs dont endsAt > from, défaut maintenant, triés par startsAt, limit 1–100 défaut 20 ; defaults = { durationMin: followUp.durationMin sinon préférence sessions.defaultDurationMin sinon 60, modality: followUp.modality si dans {in_person, video, phone} sinon in_person, rhythm: followUp.rhythm }).
- dto/AgendaWindowDto, dto/FreeBusyDto, dto/PatientAppointmentsDto, dto/AppointmentDefaultsDto (un fichier chacun).
- service/AgendaQueryService (lecture seule, @Transactional(readOnly = true)).
- Méthodes JPQL ajoutées à AppointmentRepository pour ces trois lectures (aucun SQL natif).
- make contract, app.contract.version = 0.12.0.

Périmètre : src/main/java/com/psymate/agenda/controller/AgendaController.java, service/AgendaQueryService.java, dto/{AgendaWindowDto,FreeBusyDto,PatientAppointmentsDto,AppointmentDefaultsDto}.java, AppointmentRepository.java (ajout de méthodes), messages_*.properties (« # agenda (M11-06) »), src/test/java/com/psymate/agenda/AgendaQueryIT.java, contract/openapi.yaml. Pas de fichier Appointment* hors la règle ci-dessus.

Critères d'acceptation :
- fenêtre d'une semaine contenant 3 rendez-vous dont 1 annulé → 2 rendez-vous, 3 avec includeCancelled=true ; un rendez-vous à cheval sur from est inclus ; un rendez-vous d'un autre praticien n'apparaît jamais ;
- to - from = 63 jours → 422 WINDOW_TOO_LARGE ; from ≥ to → 422 ; from absent → 422 REQUIRED ;
- freebusy sur un créneau libre entre un rendez-vous 09:00–09:50 et un 11:30–12:20 → conflicts vide, previous = celui de 09:00, next = celui de 11:30 ; sur 09:30–10:20 → conflicts[0] = celui de 09:00 ; excludeAppointmentId retire le rendez-vous déplacé de ses propres conflits ;
- rendez-vous d'un patient : patient d'un autre praticien → 403 ; patient avec follow_up { durationMin: 50, modality: "video", rhythm: "toutes les 2 semaines" } → defaults { 50, video, "toutes les 2 semaines" } ; follow_up vide et préférence sessions.defaultDurationMin = 45 → defaults { 45, in_person, null } ;
- busy est toujours [] ; aucune lecture d'une table hors appointment (TableOwnershipTests vert) ;
- contrat regénéré, version 0.12.0.

Tests : AgendaQueryIT (Testcontainers, deux praticiens, jeux de données posés par l'API de M11-05 si fusionnée, sinon par insertion SQL dans le test).

Interdits : écriture en base ; SQL natif ; dépendance Maven ; fichiers de M11-05 (hors la règle de création provisoire) ; modification de V12.

Livrable : PR « M11-06 Agenda : lectures fenêtrées et freebusy », ./mvnw verify vert, make log.
```

### M11-07 — Séries de rendez-vous

```text
Tâche M11-07 — Séries : répétition à la création, series_id, annulation de série (dépôt psymate-api, module agenda). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md (I1 Q53, Q55 ; I3 POST /api/appointments avec recurrence et POST /api/appointments/series/{seriesId}/cancel), docs/design/15b-nouveau-rendez-vous.md « Répéter », « Interactions » (maximum 26 occurrences, toast « Annuler » = annulation de la série, avertissement « L'occurrence du 5 décembre chevauche… »), « Critères d'acceptation » (lignes série). M11-05 et M11-06 sont fusionnées : AppointmentController, AppointmentService, CreateAppointmentRequest, AppointmentCreatedDto, AgendaQueryService existent.

Objectif :
- dto/RecurrenceRequest { everyWeeks (1, 2 ou 4, INVALID_VALUE sinon), count? (1–26), until? (date ISO, ≥ startsAt) } : exactement un des deux, sinon 422 sur le champ recurrence (code REQUIRED si aucun, INVALID_VALUE si les deux). CreateAppointmentRequest accepte recurrence (retire le refus UNKNOWN_FIELD posé par M11-05) et un paramètre tz optionnel (défaut Europe/Paris).
- AppointmentService.create : avec recurrence, calcule les occurrences (startsAt + k × everyWeeks semaines, même heure locale dans le fuseau tz : un passage à l'heure d'hiver ne décale pas l'heure affichée), s'arrête à count ou à until inclus ; plus de 26 → 422 TOO_MANY_OCCURRENCES sur recurrence, rien n'est créé ; toutes les occurrences dans une seule transaction, même series_id (UUID), même statut, même note ; conflicts[] agrège les conflits de chaque occurrence (ConflictDto gagne occurrenceStartsAt) ; un AppointmentPlanned par occurrence ; Location = première occurrence.
- POST /api/appointments/series/{seriesId}/cancel { reason? } : annule les occurrences planned et tentative de la série du praticien courant (cancelled et done inchangées), 404 SERIES_NOT_FOUND si aucune occurrence n'est au praticien ; un AppointmentCancelled par occurrence annulée ; réponse { appointments } (toutes les occurrences de la série, état final).
- PATCH et cancel unitaires restent par occurrence (aucune propagation à la série, Q55).
- make contract, app.contract.version = 0.12.1.

Périmètre : src/main/java/com/psymate/agenda/**, ses tests, messages_*.properties (« # agenda (M11-07) »), contract/openapi.yaml.

Critères d'acceptation :
- recurrence { everyWeeks: 1, count: 4 } → 4 lignes, même series_id, 4 AppointmentPlanned, Location = première ; everyWeeks: 2, until = startsAt + 7 semaines → 4 occurrences (semaines 0, 2, 4, 6) ;
- count: 27 → 422 TOO_MANY_OCCURRENCES et zéro ligne créée ; until trop lointain avec everyWeeks: 1 (plus de 26 occurrences) → idem ;
- count et until ensemble → 422 ; aucun des deux → 422 ; everyWeeks: 3 → 422 INVALID_VALUE ;
- une occurrence chevauchant un rendez-vous existant → 201, conflicts contient cette occurrence seulement ;
- série traversant le dernier dimanche d'octobre (fuseau Europe/Paris) : toutes les occurrences à la même heure locale ;
- cancel de série : 4 occurrences dont 1 déjà cancelled et 1 déplacée → 3 cancelled au total, la déplacée annulée aussi (elle garde son series_id) ; série d'un autre praticien → 404 ; aucune suppression physique ;
- GET /api/agenda renvoie seriesId sur chaque occurrence ;
- contrat regénéré, version 0.12.1.

Tests : AppointmentSeriesIT (un cas par critère) ; RecurrenceCalculatorTest unitaire (bornes, until inclusif, changement d'heure, 26 exactement accepté).

Interdits : stockage d'une règle de récurrence ; modification d'occurrences passées lors d'un PATCH ; suppression physique ; dépendance Maven ; modification de V12 (series_id existe déjà).

Livrable : PR « M11-07 Séries de rendez-vous », ./mvnw verify vert, make log.
```

### M11-08 — *(conditionnelle)* Rattacher une séance à un rendez-vous

À ne lancer qu'une fois le bouton du panneau tranché côté web. Si le bouton retenu ouvre simplement le dossier (« Démarrer 360 » = navigation), **cette tâche n'existe pas** et `session_entry_id` reste NULL en V1. Si le bouton doit créer la séance, deux PR dans l'ordre :

```text
Remplacé par G Q61 : agenda ne dépend plus de clinical ; si M11-08 revient, passer par un événement ou deux appels du web.

Tâche M11-08a — SessionFacade dans clinical.api (dépôt psymate-api, module clinical, package api et service). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md I3 (ligne POST /api/appointments/{id}/start), docs/G-decisions.md Q33, Q36, docs/A-architecture.md A4.2 (séances), src/main/java/com/psymate/clinical/controller/SessionController.java et le service de séance qu'il appelle (démarrage : une seule séance ouverte par patient, 409 sinon ; la séance d'accueil ne compte pas), src/main/java/com/psymate/clinical/api/{EntryFacade,SessionView}.java.

Objectif : interface clinical.api.SessionFacade avec UUID startSession(UUID patientId, String modality, Integer durationMin) : mêmes règles et mêmes exceptions que POST /api/patients/{id}/sessions/start (garde de propriété incluse, 409 si une séance est ouverte), durée pré-remplie (entry_session.duration_min) sans clôturer la séance, publie EntryCreated comme le contrôleur ; implémentation service/SessionFacadeImpl déléguant au service existant, sans dupliquer la logique. Aucun endpoint nouveau.

Périmètre : src/main/java/com/psymate/clinical/api/SessionFacade.java, service/SessionFacadeImpl.java, tests clinical. Contrat inchangé.

Critères d'acceptation : startSession crée une entry de type session avec entry_session.modality et duration_min ; deuxième appel sur le même patient → l'exception 409 du module ; patient d'un autre praticien → 403 ; EntryCreated publié ; ContractIT vert sans make contract.

Tests : SessionFacadeIT.

Interdits : modifier SessionController ; toute référence au module agenda ; dépendance Maven.

Livrable : PR « M11-08a clinical.api.SessionFacade », ./mvnw verify vert, make log.
```

```text
Remplacé par G Q61 : agenda ne dépend plus de clinical ; si M11-08 revient, passer par un événement ou deux appels du web.

Tâche M11-08b — POST /api/appointments/{id}/start (dépôt psymate-api, module agenda). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md I1 Q52, I3 (ligne start), docs/design/15-agenda.md « Interactions » (« Démarrer la séance → crée l'entry_session, marque le rendez-vous done »), src/main/java/com/psymate/clinical/api/SessionFacade.java (M11-08a), agenda/service/AppointmentService.java.

Objectif : POST /api/appointments/{id}/start → dans une transaction : rendez-vous planned ou tentative du praticien (404 / 409 APPOINTMENT_NOT_ACTIVE sinon), SessionFacade.startSession(patientId, modality, durationMin), session_entry_id renseigné, status = done, événement AppointmentStarted (nouveau record dans api/), réponse 200 { appointment, sessionId }. La modalité du rendez-vous devient celle de la séance (in_person, video, phone). Si clinical refuse (séance déjà ouverte), l'exception 409 remonte telle quelle et le rendez-vous reste inchangé. make contract, app.contract.version = 0.12.2 (et le tag de M11-09 devient contract-v0.12.2).

Périmètre : module agenda, messages, tests, contrat.

Critères d'acceptation : start → entry_session créée avec la durée du rendez-vous, appointment.status = done et session_entry_id = l'entrée ; second start → 409 ; start alors qu'une séance est déjà ouverte → 409 et rendez-vous toujours planned (rollback) ; start d'un autre praticien → 404 ; PATCH après start → 409.

Tests : AppointmentStartIT.

Interdits : écrire dans les tables de clinical ; dépendance Maven.

Livrable : PR « M11-08b Démarrer une séance depuis un rendez-vous », ./mvnw verify vert, make log.
```

### M11-09 — Seed, tag de contrat, suivi

```text
Tâche M11-09 — Seed de l'agenda, tag de contrat, suivi (dépôt psymate-api, tools/seed et racine). Utilise l'agent seed-perf.

Contexte : lis CLAUDE.md, docs/I-plan-agenda-api.md I2, I3, docs/design/15-agenda.md « Structure de l'écran » (la semaine de la maquette : 14 séances, cabinet / visio / téléphone, un rendez-vous « à confirmer », samedi travaillé), docs/C-plan.md C3 « Données de démonstration », tools/seed/** (comment le seed small / medium pose patients et séances), docs/D-orchestration-backend.md D6-A4 (tag de contrat après fusion d'un lot).

Objectif : le seed small et medium posent des rendez-vous pour chaque praticien de démonstration : 8 semaines autour d'aujourd'hui (4 passées, 4 à venir), 10 à 16 par semaine, répartis lundi–samedi entre 8 h et 19 h (Europe/Paris), 70 % cabinet, 20 % visio, 10 % téléphone, 1 rendez-vous tentative par semaine, 1 annulé par semaine, quelques séries hebdomadaires (series_id commun), aucun rendez-vous pour un patient archivé ; creation par l'API (POST /api/appointments, series par recurrence, annulation par /cancel : le seed n'ecrit jamais en SQL, E5.5) ; ck_appointment_cancel est garanti par l'API. make db-reset && make seed passe ; make seed est idempotent comme avant.

Puis, une fois cette PR fusionnée et toutes les PR M11 vertes sur main : git tag contract-v0.12.1 (ou 0.12.2 si M11-08b est fusionnée) sur main et git push --tags ; make log TASK=M11-09 … puis une entrée make log TASK=PROJET STATUS=done SUMMARY="Jalon M11 Agenda (API) fusionné ; web et Google à venir".

Périmètre : tools/seed/**, Makefile si une cible change, docs/progress/events (via make log seulement).

Critères d'acceptation : après make seed, SELECT count(*) FROM appointment > 0 pour chaque praticien seedé ; au moins une série de 4 occurrences ; au moins un cancelled avec cancelled_at ; aucun appointment hors [now - 5 semaines, now + 5 semaines] ; GET /api/agenda sur la semaine courante du praticien de démonstration renvoie entre 10 et 16 rendez-vous ; le tag est posé sur le commit de main qui contient la version indiquée dans application.yml.

Tests : SeedAgendaPerfIT (profil perf, lance par make perf) qui exécute le seed small sur une base vierge et vérifie les comptes ci-dessus.

Interdits : modifier le code du module agenda ; dépendance Maven ; données nominatives réelles.

Livrable : PR « M11-09 Seed agenda et tag de contrat », ./mvnw verify vert, tag, make log.
```

## I6. Revue (prompt du reviewer, identique pour chaque tâche)

```text
Lance le sous-agent reviewer sur cette branche pour la tâche <ID>. En plus de sa liste habituelle, vérifie : (1) que le module agenda n'importe que identity.api, patients.api, clinical.api, events.api et shared ; (2) que les critères de docs/I-plan-agenda-api.md I5 <ID> ont chacun un test nommé ; (3) qu'aucune colonne, table, dépendance Maven, appel externe ou clé Google n'apparaît ; (4) qu'aucun DELETE ne touche appointment ; (5) que app.contract.version et contract/openapi.yaml sont cohérents avec I3 ; (6) pour M11-05 et M11-06, que la PR ne touche que les fichiers autorisés par I4 « Exception à D5 ». Corrige ce qu'il marque bloquant, relance-le, puis make log.
```

## I7. Ce qui reste ouvert après M11

- Bouton du pied de panneau (« Démarrer la séance » ou « Démarrer 360 ») : décision web ; conditionne M11-08.
- Jalon web « Agenda » (fiches 15, 15b en Angular, FullCalendar) : à planifier dans H une fois `contract-v0.12.x` tagué.
- Jalon M12 Google : OAuth, agenda secondaire, `events.watch`, freebusy, préférence `calendar.googleDetail` (valeurs à réconcilier entre les fiches 15 et 15b : `initials | first_name | busy_only` ou `initials | none | full`), `practitioner_integration`, colonnes `google_*` et `source` sur `appointment`, validation OAuth par Google, question HDS.
- Flux iCal secret en lecture (`GET /api/me/calendar.ics?token=`) : dépendance Maven (`biweekly` ou `ical4j`), à décider avec M12 ou avant.
- Vue Mois : le contrat couvre une fenêtre de 62 jours, suffisante pour une grille mensuelle avec débords ; aucune agrégation côté API prévue.

## I8. M11-10 Rendez-vous invité (ajouté le 15 novembre 2026, G Q60)

**Décision (G Q60, révise Q58).** Un rendez-vous peut viser soit un patient du praticien, soit un **invité** : une personne décrite seulement par nom, prénom et téléphone, sans fiche patient. L'invité ne vit que dans ses lignes `appointment` : aucune ligne `patient`, aucune table de contacts, il n'apparaît ni dans la liste des patients, ni dans la recherche, ni dans la vue d'ensemble. Ses données ont les mêmes règles d'accès que celles d'un patient (le praticien propriétaire seul ; 404 pour les autres). Il est **purgé** automatiquement : 6 mois après la fin du rendez-vous (ou après son annulation), ses colonnes `guest_*` passent à `NULL` et `guest_purged_at` est renseigné ; le rendez-vous reste. C'est la seule « suppression » de l'agenda (E9 : mise à `NULL` de colonnes, jamais de `DELETE`). Le jour venu, le praticien crée la fiche patient puis **rattache** le rendez-vous (et, s'il le veut, tous les rendez-vous du même invité) à ce patient.

**Modèle** (`V13__agenda_guest.sql`, E3.14, E4 ; V12 reste telle quelle) :

```sql
ALTER TABLE appointment ALTER COLUMN patient_id DROP NOT NULL;
ALTER TABLE appointment
  ADD COLUMN guest_first_name text,
  ADD COLUMN guest_last_name  text,
  ADD COLUMN guest_phone      text,
  ADD COLUMN guest_purged_at  timestamptz,
  ADD CONSTRAINT ck_appointment_subject CHECK (
    (patient_id IS NOT NULL AND guest_first_name IS NULL AND guest_last_name IS NULL AND guest_phone IS NULL AND guest_purged_at IS NULL)
    OR (patient_id IS NULL AND (guest_last_name IS NOT NULL OR guest_purged_at IS NOT NULL))
  );
CREATE INDEX ix_appointment_guest_purge ON appointment(ends_at) WHERE patient_id IS NULL AND guest_purged_at IS NULL;
```

Entité `Appointment` : `guestFirstName`, `guestLastName`, `guestPhone`, `guestPurgedAt` ; `patientId` nullable (UUID nus, aucune association JPA).

**Contrat (`0.13.0`).**
- `CreateAppointmentRequest` : `patientId` facultatif ; `guest { firstName? (≤ 100), lastName (requis, ≤ 100), phone? (≤ 30 ; chiffres, espaces, `+`, `.`, `-`, parenthèses, au moins un chiffre, sinon `INVALID_FORMAT` ; chaîne vide = aucun) }` (`GuestRequest`). Exactement un de `patientId` / `guest` : aucun → `422 REQUIRED` sur `patientId` ; les deux → `422 INVALID_VALUE` sur `guest`. Les séries recopient les colonnes `guest_*` sur chaque occurrence.
- `AppointmentDto` : `patient` nullable ; `guest { firstName, lastName, phone, purgedAt }` nullable (`GuestDto`) ; après purge, `firstName`, `lastName`, `phone` null et `purgedAt` renseigné. `ConflictDto.patientDisplayName` (donc `FreeBusyDto.conflicts`) : « Prénom Nom » pour un invité (le nom seul sans prénom), « Invité purgé » après purge.
- `PATCH /api/appointments/{id}` : `guest` facultatif ; `lastName` requis et remplacé, `firstName` / `phone` absents ou null gardés, chaîne vide effacée ; `422 INVALID_VALUE` sur `guest` si le rendez-vous vise un patient ou si l'invité est purgé.
- `POST /api/appointments/{id}/attach-patient { patientId, includeSameGuest?: false }` : garde de propriété, patient non archivé (mêmes erreurs que la création) ; `409 APPOINTMENT_NOT_ACTIVE` si le rendez-vous vise déjà un patient ou si l'invité est purgé ; le rendez-vous reçoit `patient_id`, ses colonnes `guest_*` passent à `NULL` ; avec `includeSameGuest = true`, tous les autres rendez-vous invités non purgés du praticien de mêmes nom, prénom et téléphone (casse et espaces ignorés), quel que soit leur statut, sont rattachés aussi. Réponse `AttachedAppointmentsDto { appointments }`. Événement `AppointmentPatientAttached` (payload Q59, `patientId` = le patient rattaché), un par rendez-vous.
- `GET /api/agenda`, `GET /api/agenda/freebusy` : forme inchangée, les rendez-vous invités y figurent. `GET /api/patients/{id}/appointments` : inchangé, jamais d'invité.
- `RecurrenceRequest.count` porte `maximum = 26` dans le schéma (sans `@Max`, pour garder `TOO_MANY_OCCURRENCES`). `everyWeeks` ne reçoit pas d'`enum` : springdoc émet `allowableValues = {"1","2","4"}` en chaînes (`'1'`) sur un champ `integer`, schéma qu'aucune valeur entière ne satisfait ; les valeurs admises restent dans la description.

**Purge.** `service/GuestPurgeJob`, quotidienne (planificateur privé comme les autres purges, cron `app.agenda.guest-purge.cron`, défaut `0 30 3 * * *` UTC ; rétention `app.agenda.guest-purge.retention`, défaut `P180D`) : un seul `UPDATE` JPQL sur les rendez-vous `patient_id IS NULL AND guest_purged_at IS NULL` dont `ends_at` ou `cancelled_at` est antérieur à `now - retention` ; journalise le nombre de lignes, jamais les noms ; aucun événement ; `run()` appelable par les tests.

**Critères d'acceptation** (`AppointmentGuestIT`, `GuestPurgeJobIT`, `GuestRulesTest`) :
- POST avec `guest { lastName: "Benali", firstName: "Sara", phone: "+33 6 12 34 56 78" }` sans `patientId` → 201, `patient_id` NULL et `guest_*` renseignées, `patient` null et `guest` rempli, `AppointmentPlanned` ;
- sans `patientId` ni `guest` → 422 REQUIRED sur `patientId` ; les deux → 422 INVALID_VALUE sur `guest` ; `guest` sans `lastName` → 422 REQUIRED sur `guest.lastName` ; `phone` "abc" → 422 INVALID_FORMAT sur `guest.phone` ;
- série invitée (everyWeeks 1, count 3) → 3 lignes invitées, même `series_id`, mêmes `guest_*` ;
- `GET /api/agenda` renvoie le rendez-vous invité avec `guest` ; freebusy en conflit → `patientDisplayName = "Sara Benali"` ; `GET /api/patients/{id}/appointments` ne renvoie jamais d'invité ;
- PATCH `{ guest: { lastName: "Ben Ali" } }` corrige le nom ; PATCH `guest` sur un rendez-vous patient → 422 INVALID_VALUE ;
- attach-patient → 200, `patient_id` renseigné, `guest_*` NULL, `AppointmentPatientAttached` ; `includeSameGuest` rattache les rendez-vous du même invité (casse et espaces différents) et pas ceux d'un autre invité ; déjà patient → 409 ; patient d'un autre praticien → 403 ; patient archivé → 422 PATIENT_ARCHIVED ;
- rendez-vous d'un autre praticien : 404 pour attach, PATCH, GET ;
- purge : invité terminé il y a 7 mois → `guest_*` NULL, `guest_purged_at` posé, ligne conservée, `guest.purgedAt` dans le DTO ; terminé il y a 5 mois → intact ; rendez-vous patient de 7 mois → intact ; annulé il y a 7 mois → purgé ; attach / PATCH `guest` sur un invité purgé → 409 / 422 ;
- aucune ligne `appointment` supprimée ; `TableOwnershipTests`, `ArchitectureTests`, `FlywayMigrationIT` (V13) verts ;
- contrat regénéré, `0.13.0`, `ContractIT`, `AppointmentIT`, `AgendaQueryIT`, `AppointmentSeriesIT` verts.

**Interdits.** Toute dépendance Maven ; toute colonne ou table hors V13 ; tout `DELETE` sur `appointment` ; toute lecture hors `appointment` ; tout import hors `identity.api`, `patients.api`, `clinical.api`, `events.api`, `shared`.

## I9. M11-11 Export des rendez-vous (ajouté le 15 novembre 2026, G Q61)

**Décision (G Q61, complète Q18, révise les dépendances de Q50).** Les rendez-vous font partie des données exportées. L'export du compte (zip, Q18) contient `rendez-vous.md` ; l'export d'un dossier patient (`GET /api/patients/{id}/export`, Markdown et PDF ; le HTML est l'étape interne du PDF) reçoit une section « Rendez-vous » après les séances et « Sans séance ». Pour éviter un cycle de modules, `agenda` cesse de dépendre de `clinical :: api` (déclarée par Q50, jamais utilisée) et `clinical` gagne `agenda :: api`. La tâche conditionnelle M11-08, si elle revient, passera par un événement ou par deux appels du web.

**Façade (`agenda.api`).** `AppointmentExportFacade` : `List<AppointmentExportView> allOfPractitioner(UUID practitionerId)` et `List<AppointmentExportView> ofPatient(UUID patientId)`, triés par `startsAt` puis `id`, tous statuts. `AppointmentExportView` : `id, patientId (nullable), subjectDisplayName (nom du patient par PatientFacade, « Prénom Nom » de l'invité, le nom seul sans prénom, ou « Invité purgé »), guestPhone (nullable, null si purgé), startsAt, endsAt, durationMin, modality, status (valeurs SQL), seriesId (nullable), planningNote, cancelReason, cancelledAt`. Sans contrôle d'accès : l'appelant, `clinical.export`, agit déjà pour le praticien propriétaire (`ofPatient` après `PatientOwnershipGuard` ou pour un patient de `PatientFacade.ownedBy`). Implémentation `agenda.service.AppointmentExportFacadeImpl` : JPQL de `AppointmentRepository` (`findAllForExport`, `findAllOfPatientForExport`), transaction en lecture seule. Aucun DTO web, aucun endpoint, aucune migration.

**Contenu des exports.**
- `rendez-vous.md` (zip du compte, juste après `patients.md`) : titre « Vos rendez-vous », date de l'export, « Dates et heures en UTC. », puis un tableau Markdown, une ligne par rendez-vous du praticien (patients et invités, tous statuts, invités purgés compris) : Date, Heures, Durée, Modalité, Statut (libellé : Prévu, À confirmer, Annulé, Réalisé), Personne, Téléphone (de l'invité, vide après la purge), Série (oui / non), Note de planification (telle quelle : ce sont les mots du praticien), Motif d'annulation, Annulé le. Sans rendez-vous : pas de tableau, la ligne « Aucun rendez-vous. ».
- Dossier patient : section `## Rendez-vous` (Markdown) ou `<h2>Rendez-vous</h2>` + tableau (HTML, donc PDF), mêmes colonnes sans Personne ni Téléphone, après les séances et « Sans séance » ; omise si le patient n'a aucun rendez-vous.
- Format de `ExportDates` (`dd/MM/yyyy`, `HH:mm`). Dates et heures en UTC : choix de cohérence avec l'export existant (en-tête « Dates et heures en UTC »), retenu par l'orchestrateur et non tranché par l'utilisateur ; le passage en heure locale du praticien, s'il est demandé, concernera tout l'export en une fois. Libellés `clinical.export.appointments.*` (`messages_fr.properties` sous `# export (M11-11)`, `messages_en.properties`). Texte libre échappé (barre verticale, sauts de ligne) : il ne casse pas le tableau.

**Critères d'acceptation** (`AccountExportAppointmentsIT`, `PatientExportAppointmentsIT`, `AppointmentRowsTest`) :
- un praticien avec un rendez-vous patient, un rendez-vous invité (Sara Benali, +33 6 12 34 56 78), un invité purgé et un rendez-vous annulé → `rendez-vous.md` du zip a quatre lignes, la personne correcte pour chacun (« Invité purgé » et aucun téléphone pour le purgé), le statut annulé avec motif et date ; les rendez-vous d'un second praticien n'y apparaissent pas ;
- un praticien sans rendez-vous → `rendez-vous.md` présent avec le seul en-tête et la ligne « Aucun rendez-vous. » ;
- l'export Markdown d'un patient avec deux rendez-vous (dont un annulé) contient la section « Rendez-vous » avec les deux, après les séances ; un patient sans rendez-vous n'a pas la section ; les rendez-vous d'un autre patient du même praticien n'y figurent pas ; HTML et PDF se génèrent (section présente dans l'HTML) ;
- `ModularityTests` vert avec `agenda` sans `clinical :: api` et `clinical` avec `agenda :: api` ; `grep -rn "com.psymate.clinical" src/main/java/com/psymate/agenda` vide ;
- `TableOwnershipTests` vert : `clinical.export` ne lit pas `appointment` en SQL ;
- `ContractIT` vert, contrat inchangé ; tous les tests d'export existants verts.

**Interdits.** Lecture SQL de `appointment` hors du module agenda ; endpoint nouveau ; dépendance Maven ; migration ; suppression ; modification de V12 ou V13 ; export de rendez-vous d'un autre praticien.
