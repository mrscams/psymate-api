# Document J – Plan d'action · Profil 360 (API), jalon M13

18 novembre 2026. Plan de réalisation du Profil 360 côté API (`psymate-api`), **dans le module `patients`** (aucun nouveau module). Le Profil 360 est le quatrième onglet du dossier patient (après Vue d'ensemble, Timeline, Graphe) : ce qui décrit la personne elle-même, indépendamment des séances. L'écran Angular est un jalon web séparé. Tant que M13-01 n'est pas fusionnée, ce document fait foi pour les tâches M13.

Sources : maquette validée par l'utilisateur les 16 et 17 novembre 2026 (onglet « Profil 360 », menu de quatre rubriques, palette Nordique ; à verser dans `docs/design` par le jalon web), `docs/B-ux.md` (onglets du dossier, panneau latéral de 480 px), `docs/E-modele-donnees.md` E2 (conventions SQL) et E3.4 (tables du module `patients`), `docs/I-plan-agenda-api.md` (modèle de ce document), `docs/C-plan.md` C4 (gabarit de prompt).

## J1. Périmètre et décisions à inscrire dans G (M13-01)

Ce que l'API doit permettre, lu dans la maquette : ouvrir l'onglet et voir d'un coup toutes les informations du patient, rangées en quatre rubriques (Histoire et souvenirs sur un fil chronologique, Famille et entourage, Santé et antécédents, Valeurs et projets) ; ajouter une information depuis le bouton « Ajouter au profil » ou le bouton « Ajouter » d'une rubrique, dans le panneau latéral droit ; corriger une information ; la retirer de la vue.

Hors M13 : toute modification de la zone de saisie d'une séance, du module `clinical` ou d'une route existante ; la provenance d'une information (aucun lien vers une séance ou une entrée) ; la « promotion » d'une phrase de note vers le profil ; une vue de synthèse (formulation) ; les événements de domaine ; l'indexation dans la recherche du dossier ; la vue d'ensemble ; l'export (voir J7) ; tout code d'IA.

Décisions proposées (numéros à poser à la suite de Q61 dans G, nouvelle section G9) :

| # | Question | Proposition |
| --- | --- | --- |
| Q62 | Où vit le Profil 360 | **Dans le module `patients`**, sans nouveau module : c'est de l'information sur la personne, comme `patient.context` (fiche 16). Une table de détail préfixée par la table mère (E2), `patient_profile_item`, et des routes imbriquées sous le patient comme les drapeaux et les tags. Aucune dépendance nouvelle pour `patients`, aucune façade ni événement ajouté. Une écriture de profil ne crée pas de `patient_version`, ne publie pas `PatientUpdated` et n'incrémente pas `clinical_rev` (ni la vue d'ensemble ni la timeline ne lisent le profil). Le mot « profile » seul reste celui du profil public de l'annuaire (`practitioner_profile`, `/api/me/profile`) : ici, tout est préfixé `patient_profile` / `PatientProfile`. Si le Profil 360 grossit (liens vers des séances, synthèses), il pourra être extrait dans un module à part. |
| Q63 | La séance reste intouchée | Une information de profil n'est pas une entrée : elle n'appartient à aucune séance, ne crée ni `entry` ni `link`, ne porte aucune provenance. Aucun type d'entrée, aucune commande de saisie, aucune route de `clinical` n'est ajouté ou modifié. La règle « toute entrée appartient à une séance » est inchangée. |
| Q64 | Quatre rubriques, liste fermée | `history` (Histoire et souvenirs), `family` (Famille et entourage), `health` (Santé et antécédents), `values_goals` (Valeurs et projets). Liste portée par le code (énumération Java et contrainte `CHECK`), sans table de référence ni administration. Les libellés affichés sont des textes du web. La rubrique « Ressources et fragilités » des premières maquettes est abandonnée. |
| Q65 | Une information = trois saisies | La rubrique, le texte, et la période pour `history` seulement. Le texte est un `title` obligatoire (1 à 200 caractères après suppression des espaces de bord) et un `detail` facultatif (2 000 caractères au plus), tous deux en colonnes `text` (E2, jamais de `jsonb`). Aucun autre champ structuré (ni rôle d'une personne, ni statut d'un traitement) : l'outil range ce que le praticien écrit, il n'interprète rien. |
| Q66 | Période : un libellé libre et une année de tri | `period_label` (texte libre, 60 caractères au plus : « Enfance », « Vers 2000 », « 2002 – 2009 ») est affiché tel quel et jamais interprété. `period_year` (entier facultatif, 1900 à 2100) ne sert qu'à trier le fil chronologique. Pas de type date ni d'intervalle : un souvenir est rarement daté au jour près. Les deux colonnes sont `NULL` hors `history` (contrainte `ck_patient_profile_item_period`, et `422 INVALID_VALUE` côté API). |
| Q67 | Modification sur place, sans historique | Corriger une information réécrit la ligne et met `updated_at` à jour ; il n'existe pas de table de versions. Écart assumé avec `entry_version` et `patient_version` : le profil est une synthèse vivante tenue par le praticien, la trace clinique datée reste dans les séances. Tranchée par l'utilisateur le 18 novembre 2026. |
| Q68 | Aucune suppression physique | Retirer une information = `archived_at` (E2), par `DELETE` logique comme l'archivage d'un patient (`204`, idempotent). `patient_profile_item` n'entre pas dans E9. Une information archivée n'est plus renvoyée par la lecture ; il n'y a ni restauration ni liste des archives en M13 (J7). |
| Q69 | Portée et accès | Toutes les routes passent par `PatientOwnershipGuard` (403 / 404 des patients). Une information qui n'appartient pas au patient de l'URL répond `404 PROFILE_ITEM_NOT_FOUND`. Patient archivé : lecture permise, écritures refusées comme les autres écritures du patient (`409 PATIENT_ARCHIVED`, le statut de `PatientService`, et non le 422 de l'agenda). Un compte suspendu n'y accède pas (chaîne de sécurité existante). |
| Q70 | Lecture en une fois, sans pagination | L'onglet affiche tout le profil : `GET /api/patients/{id}/profile-items` renvoie toutes les informations actives, sans curseur. Exception à la pagination par curseur, bornée par un plafond de **500 informations actives par patient** (`422 PROFILE_ITEM_LIMIT_REACHED` à la création au-delà). |

## J2. Modèle de données (migration `V14__patient_profile.sql`, à reporter dans E3.4 et E4)

```sql
-- Profil 360 (M13) : informations durables sur la personne, hors séance (G Q62 à Q70)
CREATE TABLE patient_profile_item (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  patient_id    uuid NOT NULL REFERENCES patient(id),
  category      text NOT NULL CHECK (category IN ('history','family','health','values_goals')),
  title         text NOT NULL,                         -- l'information elle-même
  detail        text,                                  -- seconde ligne facultative (précision, citation)
  period_label  text,                                  -- 'history' seulement : affiché tel quel, jamais interprété (G Q66)
  period_year   smallint CHECK (period_year BETWEEN 1900 AND 2100),  -- 'history' seulement : tri du fil chronologique
  created_by    uuid NOT NULL REFERENCES practitioner(id),
  archived_at   timestamptz,                           -- retrait de la vue, jamais de DELETE SQL (G Q68)
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_patient_profile_item_period CHECK (category = 'history' OR (period_label IS NULL AND period_year IS NULL))
);
CREATE INDEX ix_patient_profile_item_patient ON patient_profile_item(patient_id) WHERE archived_at IS NULL;
```

Propriétaire : module `patients` (`TableOwnershipTests.TABLE_OWNERS += patient_profile_item → patients`). L'appartenance au praticien se lit par le patient (`patient.owner_id`) : pas de colonne `practitioner_id` dupliquée. Les longueurs (`title`, `detail`, `period_label`) sont validées par l'API, pas par le SQL. E4 : `V14__patient_profile.sql` · M13-02 (patients) · vague M13-A · « E3.4 `patient_profile_item` » · dépend de V01.

## J3. Contrat d'API (version `0.14.0`, tag `contract-v0.14.0` posé par M13-03)

Toutes les routes exigent une session praticien propriétaire du patient. Erreurs RFC 9457, `errors[{field, code, message}]`, codes repris du module (`REQUIRED`, `INVALID_VALUE`, `OUT_OF_RANGE` pour une longueur ou une borne dépassée, `UNKNOWN_FIELD`, `PATIENT_FORBIDDEN`, `PATIENT_NOT_FOUND`, `PATIENT_ARCHIVED`) plus trois nouveaux : `PROFILE_ITEM_NOT_FOUND` 404, `PROFILE_ITEM_ARCHIVED` 409, `PROFILE_ITEM_LIMIT_REACHED` 422. Dates UTC ISO 8601.

| Route | Rôle | Réponse |
| --- | --- | --- |
| `GET /api/patients/{id}/profile-items` | Tout le profil pour l'onglet, informations actives seulement. Tri : rubrique dans l'ordre `history`, `family`, `health`, `values_goals` ; dans `history`, `periodYear` croissant (absents en dernier) puis `createdAt` ; ailleurs, `createdAt` croissant. | `PatientProfileDto { patientId, items: [PatientProfileItemDto…] }` |
| `POST /api/patients/{id}/profile-items` | Ajouter une information (panneau latéral). Corps `{ category, title (1–200), detail? (≤ 2 000), periodLabel? (≤ 60), periodYear? (1900–2100) }`. `periodLabel` ou `periodYear` avec une rubrique autre que `history` → `422 INVALID_VALUE` sur le champ. Patient archivé → `409 PATIENT_ARCHIVED`. Plafond de Q70 → `422 PROFILE_ITEM_LIMIT_REACHED`. | `201`, `Location: /api/patients/{id}/profile-items/{itemId}`, `PatientProfileItemDto` |
| `PATCH /api/patients/{id}/profile-items/{itemId}` | Corriger (fusion partielle). `null` efface `detail`, `periodLabel` ou `periodYear` ; `title` ne peut pas devenir vide. La rubrique peut changer : en quittant `history`, la période est effacée par le serveur. Information archivée → `409 PROFILE_ITEM_ARCHIVED`. | `PatientProfileItemDto` |
| `DELETE /api/patients/{id}/profile-items/{itemId}` | Retirer de la vue : archivage logique, rien n'est supprimé. Idempotent sur une information déjà archivée. | `204`, sans corps |

`PatientProfileItemDto` : `{ id, category, title, detail?, periodLabel?, periodYear?, createdAt, updatedAt }`. Un fichier par DTO (`PatientProfileDto`, `PatientProfileItemDto`, `CreatePatientProfileItemRequest`, `UpdatePatientProfileItemRequest`), MapStruct pour `PatientProfileItemMapper` (entité → DTO, dans `dto/`). Jamais d'entité dans un DTO ni dans un contrôleur.

## J4. Tâches, couloirs et parallélisme

| ID | Titre | Couloir | Dépend de | Parallèle avec |
| --- | --- | --- | --- | --- |
| M13-01 | Inscrire Q62–Q70 et le jalon M13 dans G, C, D, E, CLAUDE.md, F | docs (+ `.claude/agents`) | — | M13-02 |
| M13-02 | Profil 360 dans `patients` : `V14__patient_profile.sql`, entité, les quatre routes, contrat `0.14.0` | patients | — (ce document fait foi en attendant M13-01) | M13-01 |
| M13-03 | Seed du profil, tag `contract-v0.14.0`, suivi | racine (`tools/seed`) | M13-02 | — |

**Vagues.** M13-A : 01 ∥ 02. M13-B : 03. Effort indicatif : 1,5 jour.

**Agents.** Réalisation : `backend-module-dev` (M13-02), `seed-perf` (M13-03), Claude directement pour M13-01 (docs). Revue : `reviewer` avant chaque fusion avec le prompt J6, puis `make log`.

**Liste de contrôle D8** inchangée : dépendances fusionnées, CI de `main` verte, couloir `patients` libre, branche `M13-0x-<sujet>` depuis `main` à jour, `./mvnw verify` vert, revue, `make log`.

## J5. Prompts

Manœuvre : `git checkout main && git pull && claude --worktree <ID>`, coller le prompt. À la fin : `Lance le sous-agent reviewer sur cette branche et corrige ce qu'il marque bloquant.`, lecture humaine (migration, diff `contract/openapi.yaml`, un IT), `gh pr merge <n> --squash --delete-branch`.

### M13-01 — Inscrire le Profil 360 dans la documentation

```text
Tâche M13-01 — Inscrire le jalon M13 Profil 360 (API) dans la documentation (dépôt psymate-api, docs/, CLAUDE.md et .claude/agents/ seulement ; aucun code Java, aucune migration).

Contexte : lis docs/J-plan-profil-360-api.md en entier (il fait foi pour cette tâche), docs/G-decisions.md (G8 et le format des lignes Q50–Q61), docs/C-plan.md C1 et C11, docs/D-orchestration-backend.md D1, D3, D5, docs/E-modele-donnees.md E1, E3.4, E4, E9, docs/F-runbook-agents-backend.md (les mentions du jalon M11, pour le même renvoi), CLAUDE.md (règles d'architecture).

Objectif :
- G : nouvelle section « G9. Profil 360 (API) : jalon M13, dans le module patients (18 novembre 2026) » avec Q62 à Q70 copiées du tableau J1 (une ligne par question, même style que Q50–Q59, « tranchée par l'utilisateur le 18 novembre 2026, M13-01 ») ; G5 reçoit la ligne des documents mis à jour.
- C : C1 ajoute le jalon M13 (objectif, démontrable, effort indicatif 1,5 j) ; nouvelle section C12 « Profil 360 (API), module patients » avec le tableau des tâches M13-01 à M13-03 au format de C11, critères repris mot pour mot de J5.
- D : D1 ajoute les lignes M13-0x (couloirs de J4), D3 les vagues M13-A et M13-B ; D5 n'ajoute aucun couloir (M13-02 occupe le couloir patients).
- E : E1 ajoute patient_profile_item à la ligne « Patients » ; E3.4 reçoit le DDL exact de J2 à la suite de patient_access, sous le titre « Profil 360 (M13, G Q62–Q70) » ; E2 « Versions » n'est pas modifié (pas de table de versions, Q67) ; E4 ajoute la ligne V14__patient_profile.sql ; E9 n'est pas modifié (Q68).
- A : le paragraphe du module patients mentionne le Profil 360 et renvoie à J3 pour le contrat ; aucun module n'est ajouté.
- CLAUDE.md : nouvelle règle d'architecture « Le Profil 360 vit dans le module patients (G Q62) : une information de profil n'est pas une entrée, n'appartient à aucune séance et se modifie sur place (G Q63, Q67). Pour toute tâche M13, docs/J-plan-profil-360-api.md fait foi (J2 DDL, J3 contrat). » ; la liste des modules n'est pas modifiée.
- F : même renvoi que pour l'agenda (prompts dans J5, aucun lot rédigé dans F).
- .claude/agents/backend-module-dev.md : la liste de lecture ajoute « docs/J-plan-profil-360-api.md pour toute tâche M13 ».

Périmètre : docs/**, CLAUDE.md, .claude/agents/backend-module-dev.md. Rien d'autre.

Critères d'acceptation :
- G Q62–Q70 existent, datées du 18 novembre 2026 ;
- chaque tâche M13-0x de J4 a sa ligne dans C12 et D1 avec les mêmes dépendances ;
- le DDL ajouté en E3.4 est identique octet pour octet au bloc SQL de J2 ;
- aucun document ne parle d'un module profile360 ni d'un onzième module.

Tests : aucun test Java ; ./mvnw verify doit rester vert (aucune source touchée).

Interdits : toute modification de code, de migration, de contrat ; toute réécriture de docs/progress/index.html ou d'un événement existant.

Livrable : PR « M13-01 Profil 360 : décisions et documentation », puis make log TASK=M13-01 AGENT=claude STATUS=done SUMMARY="…" PR=<url>.
```

### M13-02 — Profil 360 dans `patients` : table, quatre routes, contrat

```text
Tâche M13-02 — Profil 360 dans le module patients : migration V14__patient_profile.sql, entité PatientProfileItem, les quatre routes (lire tout le profil, ajouter, corriger, archiver une information), contrat 0.14.0 (dépôt psymate-api, module patients). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/J-plan-profil-360-api.md (J1, J2 et J3 en entier), docs/G-decisions.md Q32, Q33, Q35, Q38, docs/E-modele-donnees.md E2 et E3.4. Regarde comment le module patients traite ses sous-ressources : controller/PatientController.java (drapeaux et tags imbriqués sous /api/patients/{id}, DELETE logique d'un patient en 204, annotations OpenAPI), controller/PatientExceptionHandler.java, model/PatientException.java et PatientFieldCode.java, model/PatientFlag.java et repository/PatientFlagRepository.java (entité de détail avec UUID nus), service/PatientService.java (garde de propriété, refus des écritures sur un patient archivé, fusion partielle d'un PATCH) ; tests : src/test/java/com/psymate/patients/** (PatientTestSupport, PatientServiceFixtures) et les gardes src/test/java/com/psymate/{TableOwnershipTests,FlywayMigrationIT,ArchitectureTests}.java. Si M13-01 n'est pas encore fusionnée, docs/J-plan-profil-360-api.md fait foi : dis-le dans la PR.

Objectif :
- src/main/resources/db/migration/V14__patient_profile.sql : copie exacte du bloc SQL de J2.
- model/PatientProfileItem.java : entité JPA de patient_profile_item (UUID nus pour tenant_id, patient_id, created_by ; updated_at mis à jour par l'entité) ; model/PatientProfileCategory.java {history, family, health, values_goals} persistée en texte.
- repository/PatientProfileItemRepository.java : informations actives d'un patient, compte des actives, lecture par id et patient ; méthodes dérivées ou JPQL, aucun SQL natif.
- service/PatientProfileService.java : garde de propriété du patient avant toute lecture ou écriture ; patient archivé → même refus que les autres écritures du patient (409 PATIENT_ARCHIVED), lecture permise ; information absente ou d'un autre patient → 404 PROFILE_ITEM_NOT_FOUND ; période avec une rubrique autre que history → 422 INVALID_VALUE sur le champ fautif ; changement de rubrique qui quitte history → période effacée ; plafond de 500 informations actives par patient → 422 PROFILE_ITEM_LIMIT_REACHED ; PATCH d'une information archivée → 409 PROFILE_ITEM_ARCHIVED ; archivage idempotent. Modification sur place : aucune version, updated_at seulement (Q67). Aucune ligne patient_version, aucun événement PatientUpdated, aucun appel à touchClinicalRev (Q62). Tri de la lecture : celui de J3.
- controller/PatientProfileController.java : GET et POST /api/patients/{id}/profile-items ; PATCH et DELETE /api/patients/{id}/profile-items/{itemId}. Réponses, statuts et codes d'erreur exactement ceux de J3 ; les erreurs passent par PatientExceptionHandler existant.
- dto/ : PatientProfileDto, PatientProfileItemDto, CreatePatientProfileItemRequest, UpdatePatientProfileItemRequest (Jakarta Validation ; title 1–200 après trim, detail ≤ 2 000, periodLabel ≤ 60, periodYear 1900–2100 ; champ inconnu → UNKNOWN_FIELD comme les autres requêtes du module), PatientProfileItemMapper (MapStruct, entité → DTO).
- Gardes : TableOwnershipTests.TABLE_OWNERS += patient_profile_item → patients ; FlywayMigrationIT et le schéma attendu incluent V14.
- Contrat : app.contract.version = 0.14.0, make contract, contract/openapi.yaml commité.
- messages_fr.properties (et messages_en.properties) : clés ajoutées sous « # patients, Profil 360 (M13-02) ».

Périmètre : src/main/java/com/psymate/patients/** (fichiers nouveaux préfixés PatientProfile ; dans les fichiers existants, seulement les ajouts nécessaires aux codes d'erreur), src/main/resources/db/migration/V14__patient_profile.sql, src/main/resources/messages_*.properties (ajout seulement), src/test/java/com/psymate/patients/**, les lignes d'enregistrement des gardes citées, contract/openapi.yaml, la propriété de version du contrat.

Critères d'acceptation :
- V14 s'applique sur une base vierge, le schéma attendu la liste, ddl-auto=validate passe ;
- les quatre routes répondent comme J3 ; une information créée dans history avec periodLabel « Enfance » et periodYear 1998 se lit entre une information de 1991 et une de 2002 ; une information sans periodYear arrive après les datées ;
- periodLabel ou periodYear sur family, health ou values_goals → 422 INVALID_VALUE (et la contrainte SQL ck_patient_profile_item_period refuse la même chose en base) ; title vide ou absent → 422 REQUIRED ; title, detail ou periodLabel trop long, periodYear hors bornes → 422 OUT_OF_RANGE ; category inconnue → 422 INVALID_VALUE ;
- le patient d'un autre praticien → 403 PATIENT_FORBIDDEN, patient inconnu → 404 PATIENT_NOT_FOUND (corps identique aux autres routes, PatientAccessProblemIT étendu) ; l'itemId d'un autre patient → 404 PROFILE_ITEM_NOT_FOUND ;
- une information archivée disparaît de la lecture, reste en base (aucun DELETE SQL), et l'archivage rejoué répond 204 ;
- après un PATCH, une seule ligne existe pour l'information et updated_at a avancé ; patient.current_version, patient_version et patient.clinical_rev n'ont pas bougé ;
- aucune ligne n'est écrite dans entry, entry_version ni link ; les routes existantes de /api/patients sont inchangées ; contract/openapi.yaml ne change que par ajout (ContractDiff) ;
- ./mvnw verify vert.

Tests : PatientProfilePersistenceIT (contraintes SQL) et PatientProfileControllerIT (Testcontainers pgvector/pgvector:pg16, base réelle, jamais de mock) couvrant chaque route, chaque code d'erreur de J3, le tri, le plafond (abaissé par propriété de test), le patient archivé, l'isolation entre deux praticiens ; tests unitaires du service pour la règle de période et le changement de rubrique.

Interdits : toute modification de clinical, agenda, search, overview ; toute modification du comportement des routes existantes de patients, de PatientFacade ou de PatientOwnershipGuard ; tout événement de domaine ; toute table ou colonne absente de J2 ; toute dépendance Maven ; toute pagination ; toute suppression physique ; toute modification de E ou de G (c'est M13-01).

Livrable : PR « M13-02 Profil 360 : table et routes dans patients », template rempli (critères cochés un par un), ./mvnw verify vert, make log.
```

### M13-03 — Seed, tag de contrat, suivi

```text
Tâche M13-03 — Seed du Profil 360, tag du contrat 0.14.0, suivi (dépôt psymate-api, tools/seed). Utilise l'agent seed-perf.

Contexte : lis CLAUDE.md, docs/J-plan-profil-360-api.md (J2, J3), tools/seed/README.md (Scenario, DemoStory, tailles small / medium / demo) et la façon dont le seed de l'agenda (M11-09) appelle l'API, docs/C-plan.md C3 « Données de démonstration ».

Objectif :
- Seed small et medium : pour chaque patient non archivé, 0 à 25 informations de profil créées par POST /api/patients/{id}/profile-items (jamais par SQL), réparties sur les quatre rubriques ; dans history, un mélange de périodes avec année, de libellés sans année (« Enfance ») et d'informations sans période ; environ un patient sur cinq sans aucune information (état vide de l'onglet). Données fictives en français, aucune donnée réelle. Le plan reste produit par le générateur déterministe (Scenario) et vérifié par Scenario.validate (période seulement dans history, longueurs, plafond).
- Seed demo : le patient « DEMO · Camille R. » reçoit un profil écrit à la main, cohérent avec DemoStory (son histoire, ses proches, ses antécédents, ses projets), d'une taille proche de la maquette : une dizaine d'informations d'histoire, cinq ou six personnes, quatre informations de santé, quatre de valeurs et projets.
- Tag contract-v0.14.0 posé sur le commit de main qui contient contract/openapi.yaml en 0.14.0.
- make log pour M13-03 et clôture du jalon dans le suivi.

Périmètre : tools/seed/**, fichier d'événement du suivi. Rien dans src/.

Critères d'acceptation :
- make db-reset puis make seed SIZE=small réussit et GET /api/patients/{id}/profile-items renvoie des informations triées pour un patient du seed ;
- make seed SIZE=demo donne à Camille R. un profil dans les quatre rubriques, dont au moins une information d'histoire sans année ;
- le tag contract-v0.14.0 existe et pointe un commit où ContractIT est vert.

Interdits : toute écriture SQL directe dans patient_profile_item ; toute modification du module patients ou du contrat.

Livrable : PR « M13-03 Profil 360 : seed et tag de contrat », make log.
```

## J6. Revue (prompt du reviewer, identique pour chaque tâche)

```text
Relis le diff de cette branche contre main pour la tâche M13-0x, sans rien modifier. Vérifie, dans l'ordre : le périmètre de fichiers du prompt (dans patients, seulement des fichiers PatientProfile* et les ajouts de codes d'erreur) ; le DDL identique octet pour octet à J2 ; les routes, statuts et codes d'erreur identiques à J3 ; aucune dépendance nouvelle du module patients ; aucune route existante de /api/patients modifiée ; aucune entité dans un DTO ou un contrôleur ; aucune version, aucun DELETE SQL, aucun événement, aucune écriture dans patient, patient_version, entry ou link ; chaque critère d'acceptation couvert par un test qui échouerait sans le code ; contrat modifié par ajout seulement, avec le bump de version. Classe chaque remarque en bloquant ou non bloquant.
```

## J7. Ce qui reste ouvert après M13

- **Export.** Le profil n'entre ni dans l'export du compte ni dans celui d'un dossier (G Q18, Q61). À trancher avant la bêta ; l'export lit déjà `patients :: api`, il suffira d'une lecture ajoutée à `PatientFacade`.
- **Restauration.** Aucune route pour annuler un archivage ni pour lister les informations archivées. À ajouter si l'écran propose « Annuler » après un retrait (modèle : `POST /api/patients/{id}/restore`).
- **Affichage des rubriques.** La maquette montre une colonne de gauche pour Santé et Valeurs (« En cours », « Projet ») et un nom distinct du rôle pour Famille. Le modèle n'a que `title` et `detail` (Q65) : le jalon web affiche ces deux champs, ou une décision ajoute un champ.
- **Recouvrement avec la fiche patient.** `patient.context.history` (fiche 16) reste un texte libre de la fiche ; le Profil 360 ne le lit pas et ne le remplace pas. À harmoniser côté web si les deux se côtoient.
- **Recherche du dossier.** Les informations de profil ne sont pas indexées.
- **Promotion depuis une note.** Reprendre une phrase d'une note en lecture pour en faire une information demanderait une colonne de provenance (UUID nu vers `entry`) : nouvelle migration, nouvelle décision.
- **Trame de premier entretien.** Pur enchaînement de `POST` côté web, sans séance créée : aucune API de plus.
- **Événements et IA (V2).** Aucun événement publié en M13. Le texte est déjà en colonnes `text` (E2) ; des événements et une table d'embeddings réservée pourront être ajoutés par migration sans toucher à `patient_profile_item`.
- **Jalon web.** Onglet « Profil 360 » et panneau latéral « Ajouter au profil », à planifier une fois `contract-v0.14.0` tagué ; fiche design à créer à partir de la maquette validée.
