# Document M (API) – Plan d'action · Version arabe, jalon M16, partie API

23 novembre 2026. Copie de la partie API de `psymate-web/docs/M-plan-version-arabe.md` (plan du 22 novembre 2026, mis à jour le 23 novembre pour le lancement), faite par M16-01. La partie web (décisions Q72 à Q81 du dépôt web, tâches M16-07 à M16-29, M16-33) reste dans le document du web, qui fait foi pour elle.

**Pour toute tâche M16, ce document fait foi.** G (section G12, Q96 à Q102) l'emporte sur lui ; lui l'emporte sur C (section C15) et D.

**Ce que l'utilisateur a tranché le 22 novembre 2026** (G Q71 du dépôt web, section G14 de ce dépôt-là ; M2.1 ci-dessous) : tout le produit en arabe ; chiffres latins et calendrier grégorien quel que soit le pays ; semaine commençant le lundi ; graphiques temporels laissés de gauche à droite ; police IBM Plex Sans Arabic, embarquée. **Ce que le plan propose et que l'utilisateur n'a pas relu au 23 novembre 2026** : l'arabe standard moderne, les décisions de M2.3 (inscrites en G Q96 à Q102 avec la mention « proposée par le plan M, non relue par l'utilisateur au 23 novembre 2026 »), le découpage en tâches, le second temps (M8). Qui traduit et qui valide reste ouvert (M9).

Numéros (vérifiés le 23 novembre 2026 sur `main` de psymate-api, 670a375) : jalon **M16** ; décisions **Q96 à Q102**, section **G12** ; plan **C15** ; migrations **`V16`** (M16-02) et **`V17`** (M16-03), corrigé par M16-01 (M2.4 point 9 ; dernière fusionnée : `V15__agenda_booking.sql`) ; contrat **`0.16.0`** (dernier tag : `contract-v0.15.0`). Q83 à Q95, G11 et C14 restent réservés à la facturation (M15, plan L, pas encore sur `main`) ; M15 prendra la migration (`V18`) et le contrat qui suivent ceux de M16.

## M1. Périmètre

Ce que le jalon doit permettre :

- Un praticien choisit l'arabe dans ses préférences : toute l'application passe en arabe, de droite à gauche, sous `/ar/`.
- Un visiteur lit les pages publiques et l'annuaire en arabe.
- Un administrateur travaille en arabe et saisit le libellé arabe des catalogues.
- Les catalogues (émotions, types d'entrée, types de lien, spécialités, approches, publics, modalités), les messages d'erreur et les e-mails arrivent en arabe.
- Un texte saisi en arabe s'affiche correctement dans les trois langues, et un texte saisi en français s'affiche correctement en arabe.

Hors première ouverture, proposé en second temps (M8) : résumé « En bref » rédigé en arabe, exports Markdown et PDF en arabe, recherche plein texte par racines, détection des dates dans la saisie libre en arabe. Tant qu'ils ne sont pas faits, un praticien en arabe a le résumé et l'export en français, une recherche mot à mot, et la saisie par les champs structurés.

Hors M16 : un dialecte ; une variante par pays ; le calendrier hégirien ; les chiffres arabo-indiens ; la traduction des données saisies ; les factures en arabe (M15 vise la France, la facture reste en français ; seuls les écrans du module sont traduits).


## M2. Décisions

### M2.1 Tranchées par l'utilisateur (inscrites dans le G du dépôt web : Q71, section G14)

Cinq points, le 22 novembre 2026 : (1) **tout le produit** est traduit, espace praticien, administration et pages publiques ; (2) **chiffres latins et calendrier grégorien**, quel que soit le pays ; (3) la semaine commence le **lundi** ; (4) les **graphiques temporels restent de gauche à droite** (courbe des ressentis, timeline par décennie) ; (5) police **IBM Plex Sans Arabic**, embarquée avec le site ; lettres latines et chiffres gardent la police latine du thème.

### M2.3 Décisions de psymate-api (inscrites en G Q96 à Q102, section G12, par M16-01)

Proposées par le plan à partir d'une lecture rapide de psymate-api (`main` = 4e798e7), puis confrontées au code par M16-01 sur `main` = 670a375. **G12 porte la version corrigée et fait foi** ; le tableau ci-dessous est celui du plan, les écarts constatés suivent.

| # | Question | Décision proposée |
| --- | --- | --- |
| Q96 | Langues servies | `fr`, `en`, `ar`. La langue d'une requête se résout en un seul endroit de `shared` à partir d'`Accept-Language` ; les résolutions locales (« anglais, sinon français ») des modules disparaissent. Français par défaut. Aucun changement de comportement pour `fr` et `en`. |
| Q97 | Préférence de langue | La préférence `locale` accepte `ar`. |
| Q98 | Catalogues | Colonnes `label_ar`, et `inverse_label_ar` pour les types de lien, nulles par défaut (migration `V16`). Les vues exposent `labelAr` ; les routes d'administration l'acceptent. Repli sur le français. Les catalogues globaux reçoivent leur libellé arabe, tiré du glossaire (M16-07). |
| Q99 | Messages | `messages_ar.properties` porte toutes les clés ; un test échoue si une clé manque dans une des trois langues. |
| Q100 | E-mails | Les e-mails partent dans la langue du destinataire, en arabe de droite à gauche. |
| Q101 | Dates et nombres rendus par le serveur | Chiffres latins et calendrier grégorien en arabe, comme à l'écran (Q71). |
| Q102 | Ce qui reste en français | Résumé « En bref », exports et recherche restent tels quels quand l'arabe est demandé (second temps, M8). Le repli est écrit dans le contrat, pas subi. |

Constats du plan (22 novembre 2026) : 453 clés dans `messages_fr.properties` et `messages_en.properties` ; seize classes lisent les messages ; quatre services d'envoi d'e-mail (`AuthMailer`, `VerificationMailer`, `AdminMailer`, `ContactMailer`) ; `label_fr` dans les tables `emotion`, `entry_type`, `link_type`, `directory_vocabulary` ; l'export asynchrone est fixé en français.

### M2.4 Écarts constatés par M16-01 (23 novembre 2026, `main` = 670a375)

1. **Quatre sources de langue** (Q96, Q97, Q100). (a) `Accept-Language` pour les réponses d'une requête. (b) La **colonne `practitioner.locale`** (`V01`, `CHECK (locale IN ('fr','en'))`), posée à l'inscription depuis `Accept-Language` (`AuthService`, via `AuthMessages.normalize`) et jamais modifiée ensuite : elle donne la langue des e-mails d'`AuthMailer` (`AuthService`), d'`AdminMailer` (`AdminVerificationService`, `AdminPractitionerService`, `AppealService`, `AdminAsyncMail`) et de `VerificationMailer` (`VerificationService`). (c) La **préférence `locale`** (`identity/preferences/Preferences.java`, `Set.of("fr", "en")`, et `PreferencesSchema`), lue par `directory/service/ContactRequestService.localeOf` (relais du formulaire de contact) et par `BookingRequestService.practitionerLocale` et `BookingExpiryJob.practitionerLocale` (e-mails de prise de rendez-vous au praticien). (d) Le champ `locale` du corps de `POST /api/public/booking/…/requests` (`agenda/dto/CreateBookingRequest.java`, `@Pattern(regexp = "fr|en")`), gardé dans `booking_request.locale` (`V15`, `CHECK (locale IN ('fr','en'))`), pour les e-mails au demandeur (G Q81). Sur `main`, la colonne (b) et la préférence (c) peuvent déjà différer : M16 ne change pas la source que lit chaque e-mail, la question reste ouverte (M9).
2. **Où la langue est résolue aujourd'hui** (Q96). `shared` existe déjà et est ouvert (`@ApplicationModule(type = OPEN)`) ; `shared.problem.ProblemMessages.localeOf` lit `Accept-Language` (premier intervalle). Les règles locales « anglais, sinon français », ou fixées en français, sont : `AgendaMessages`, `BookingMailer` (deux), `BookingNotifier`, `BookingRequestService.practitionerLocale`, `BookingExpiryJob.practitionerLocale`, `ClinicalMessages`, `EntryLinkAssembler`, `ExportTexts`, `PatientExportService`, `ExportJobRunner` (fixé en français), `DirectoryVocabularyAdminExceptionHandler`, `DirectoryVocabularyService` (noms des langues en français et en anglais), `ContactMailer`, `ContactRequestService`, `AdminMessages`, `AdminAsyncMail`, `AdminVerificationService`, `AuthController`, `AuthMessages`, `AuthService` (six appels de `Locale.forLanguageTag`), `PreferencesExceptionHandler`, `VerificationMessages`, `VerificationService`, `LinkExceptionHandler`, `LinkAssembler`, `LinkMessages`, `OverviewMessages`, `NarrativeComposer`, `SearchMessages`. `PatientProfileMessages` délègue déjà à `ProblemMessages.localeOf`, et `AppointmentForbiddenFieldsAdvice` à `AgendaMessages`. Deux lectures coexistent : `request.getLocale()` et `Locale.LanguageRange.parse`, toutes deux sur le premier intervalle ; elles donnent la même langue pour `fr` et `en`.
3. **Pas de repli automatique vers le français** (Q96, Q99). Les messages sont lus par dix-huit `ResourceBundleMessageSource` privées (basename `messages`, `fallbackToSystemLocale(false)`), sans fichier de base `messages.properties`. Une locale `ar` sans `messages_ar.properties`, ou une clé absente de ce fichier, ne retombe donc pas sur le français d'elle-même : le repli de M16-02 passe par la langue servie (`ar` → `fr` tant que M16-04 n'est pas fusionnée, et toujours pour le résumé et l'export, Q102).
4. **529 clés, pas 453** (Q99). M14 en a ajouté : 529 clés dans chacun des deux fichiers sur `main` = 670a375, dont 27 `overview.narrative.*` et 79 `clinical.export.*` (second temps, Q99, Q102).
5. **Cinq services d'envoi, tous en texte brut** (Q100). `AuthMailer`, `VerificationMailer`, `AdminMailer`, `ContactMailer` et `BookingMailer` (M14, appelé par `BookingRequestService`, `BookingNotifier`, `BookingExpiryJob` et `AppointmentService`). Chacun envoie un seul corps `text/plain` (`MimeMessageHelper(…, false, "UTF-8")`, `setText(text)`), assemblé à partir de clés de `messages_*.properties` (`mail.*` pour `AuthMailer`, `admin.mail.*`, `verification.mail.*`, `contact.mail.*`, `booking.mail.*`). Il n'existe **aucun** dossier `src/main/resources/templates` ni corps HTML. Q100 est corrigée : en arabe, l'e-mail part en `multipart/alternative`, avec le texte brut et un corps HTML minimal `<html lang="ar" dir="rtl">` produit par le code à partir des mêmes clés ; liens, adresses, codes et dates isolés en `dir="ltr"`. Les e-mails `fr` et `en` restent en texte brut seul, octet pour octet.
6. **Chiffres** (Q101). Avec la locale `ar`, `MessageFormat` et `NumberFormat` écrivent des chiffres arabo-indiens (JDK 21.0.12 : `{0}` = 1234 donne « ١٬٢٣٤ ») ; `DateTimeFormatter` reste en chiffres latins (`DecimalStyle.STANDARD`, chronologie ISO). Toutes les sources de messages appliquent `MessageFormat` à leurs paramètres, et neuf clés emploient `{n,number}` ou `{n,choice}`. La locale servie pour l'arabe est donc `ar-u-nu-latn` (« 1,234 »), qui garde la recherche de fichier `ar` puis racine. Dates mises en forme par le serveur : `BookingMailer` (motif `booking.mail.datePattern`), `AdminVerificationService` (`FormatStyle.MEDIUM`), `NarrativeComposer` (`FormatStyle.LONG`) et `ExportDates` (export, reste en français, Q102).
7. **Catalogues** (Q98). `label_fr` porte sur `entry_type`, `emotion`, `link_type` (avec `inverse_label_fr`) dans `V01`, seed dans `V02` ; `directory_vocabulary` dans `V04`, dont les seuls `kind` sont `specialty` (qui comprend les approches) et `audience`. Les **modalités** (`in_person`, `video`, `home`) et les **langues parlées** de l'annuaire ne sont pas en base : `DirectoryVocabularyService` les sert comme constantes (`labelFr`, `labelEn`) et par `Locale.getDisplayLanguage`, dans le record `directory/dto/Item.java` partagé avec spécialités et publics (`PublicDirectoryController`, `DirectoryVocabularyController`) ; M16-03 leur donne `labelAr`. `emotion.label_en` et `directory_vocabulary.label_en` sont nuls par défaut, `entry_type.label_en` et `link_type.label_en` obligatoires : `label_ar` est nul partout (Q98). Les émotions `custom` et les étiquettes (`tag`) d'un praticien gardent un seul libellé.
8. **Résumé « En bref » et export** (Q102). `NarrativeComposer` compose phrase par phrase dans la langue demandée et retombe sur le français pour une phrase absente ; l'export immédiat (`PatientExportService`, `ExportTexts.of`) suit la langue anglais-sinon-français ; l'export asynchrone (`ExportJobRunner`) et le rendu Markdown de `PatientExportService` sont fixés en français. Si le résumé ou l'export recevaient la langue servie `ar`, ils chercheraient des clés arabes (absentes, point 3) ou, si elles existaient, composeraient en arabe un PDF sans police arabe : M16-02 leur fait donc résoudre `ar` en `fr` explicitement, et leurs clés restent hors de `messages_ar.properties` (Q99). La recherche ne dépend d'aucune langue.
9. **Migrations** (Q97, Q98). Les contraintes `CHECK (locale IN ('fr','en'))` de `practitioner.locale` (`V01`) et `booking_request.locale` (`V15`) refuseraient `ar` : une inscription avec `Accept-Language: ar` finirait en 500. M16-02 prend donc **`V16`** (élargir ces deux contraintes), et M16-03 **`V17`** (libellés arabes), au lieu de `V16` pour les catalogues comme le prévoyait le plan. M15 (facturation) prendra la migration suivante.

## M4. Tâches, couloirs et parallélisme

### M4.1 API (`psymate-api`)

| ID | Titre | Couloir | Dépend de | Parallèle avec |
| --- | --- | --- | --- | --- |
| M16-01 | Décisions Q96 à Q102 et documentation ; copie de ce plan | docs | — | M16-07 |
| M16-02 | Langue de la requête résolue en un seul endroit ; préférence `locale` = `ar` ; dates et nombres en chiffres latins | shared + classes de messages des modules + identity (préférences) ; corrigé par M16-01 : + agenda (`CreateBookingRequest`), + migration `V16` (contraintes de `locale`) | M16-01 | — |
| M16-03 | Catalogues : `label_ar`, `inverse_label_ar`, vues, administration, libellés des catalogues globaux | vocabulary, directory, linking, migration `V16` (corrigé par M16-01 : `V17`, et les modalités et langues de `DirectoryVocabularyService`) | M16-02, glossaire (M16-07) | M16-04 |
| M16-04 | `messages_ar.properties` complet, test de parité des clés | ressources + test | M16-02, glossaire (M16-07) | M16-03 |
| M16-05 | E-mails en arabe, de droite à gauche | services d'envoi + templates (corrigé par M16-01 : il n'existe aucun modèle, M2.4 point 5) | M16-04 | — |
| M16-06 | Seed, contrat `0.16.0`, tag, suivi | seed + contrat | M16-03, M16-05 | — |

**Vagues.** M16-A : 01. M16-B : 02. M16-C : 03 ∥ 04. M16-D : 05. M16-E : 06.

**Agents.** Claude directement pour M16-01 ; `backend-module-dev` pour M16-02 à M16-05 ; `seed-perf` pour M16-06 ; `reviewer` avant chaque fusion ; puis `make log`.

### M4.3 Règles communes aux tâches API

1. **Lecture.** `CLAUDE.md`, puis ce plan : `../psymate-web/docs/M-plan-version-arabe.md` tant que `docs/M-plan-version-arabe-api.md` n'est pas sur `main`, ensuite la copie de l'API. Le chemin `../psymate-web` part de la racine du dépôt principal ; depuis un worktree, c'est `$(git rev-parse --git-common-dir)/../../psymate-web/docs/M-plan-version-arabe.md`. G l'emporte sur M, M sur C.
2. **Version du contrat.** Une tâche qui change `contract/openapi.yaml` regarde si le tag `contract-v<version courante>` existe (`git fetch --tags && git tag -l "contract-v$(version)"`). S'il existe (cas de `0.15.0`), elle passe à la mineure suivante (`0.16.0`) dans la même PR. S'il n'existe pas, une tâche M16 précédente a déjà ouvert la version : elle ne la change pas, ses champs s'ajoutent à la version en cours. M16-06 pose le tag `contract-v0.16.0`.
3. **Un seul `verify` à la fois sur le poste** (CLAUDE.md). Avant `./mvnw verify` ou `bash tools/verify-fast.sh`, prendre le verrou partagé des worktrees : `LOCK="$(git rev-parse --git-common-dir)/verify.lock"; until mkdir "$LOCK" 2>/dev/null; do sleep 30; done`, et le rendre à la fin, succès ou échec : `rmdir "$LOCK"`. Un verrou vieux de plus de 45 minutes est considéré comme abandonné et peut être retiré. Un test isolé (`./mvnw test -Dtest=…`) n'a pas besoin du verrou.
4. **Début et fin de tâche.** Branche neuve depuis `origin/main` à jour. Au début : `make log TASK=<ID> AGENT=<agent> STATUS=in-progress SUMMARY="…"`. À la fin : `./mvnw verify` vert, sous-agent `reviewer`, corrections des points bloquants, PR avec le template, `make log … STATUS=done … PR=<url>`, événements commités. La fusion suit M4.5.
5. **Jamais** : un nom de patient ou une adresse e-mail dans un journal ; une dépendance Maven non annoncée ; un appel externe ; un fichier hors du couloir de la tâche.

S'y ajoutent, pour M16 :

1. **Rien ne change pour le français et l'anglais.** Une réponse demandée en `fr` ou en `en` est identique avant et après la tâche ; les tests existants restent verts sans être modifiés.
2. **Aucun terme métier arabe hors glossaire.** Le glossaire est `../psymate-web/docs/design/arabe-glossaire.md` ; ses termes sont proposés par M16-07 et relus par une personne arabophone avant l'ouverture. Un terme manquant est signalé dans la PR, jamais inventé.
3. **Registre.** Arabe standard moderne ; formes sans accord de genre pour le praticien et pour la personne suivie ; « PsyMate », codes d'erreur, noms de champs et adresses restent en caractères latins.

### M4.5 Lancement (23 novembre 2026, en autonomie)

Consigne de l'utilisateur, le 23 novembre 2026 : `main` des deux dépôts mis à jour, chaque branche coupée de `main` à jour, tâches en Opus 5.5, en autonomie, fusion par l'orchestratrice dès que la PR est verte, Auto-fix activé sur chaque PR, et une vérification toutes les dix minutes des sessions, des worktrees et des PR pour passer à la tâche suivante.

1. **Une session par couloir**, modèle Opus 5.5, dans son worktree. Elle enchaîne les tâches de son couloir, une branche neuve depuis `origin/main` par tâche. Couloirs : API (M16-01 à M16-06) ; documentation et socle web (M16-07 à M16-09) ; puis une session ou un agent par feature (M16-10 à M16-24) et un couloir de traduction (M16-26 à M16-28) ; enfin M16-25 et M16-29.
2. **Fin d'une tâche** : vérifications vertes en local, sous-agent `reviewer`, PR ouverte avec le template, Auto-fix activé sur la PR, puis la session rend compte en une ligne. Elle enchaîne tout de suite si la tâche suivante ne dépend pas de la PR ouverte ; sinon elle attend le message de fusion.
3. **Fusion** : en squash, par la session orchestratrice, dès que la CI est verte et que `reviewer` n'a rien laissé de bloquant, depuis un dossier neutre (`gh pr merge <n> --squash --delete-branch --repo mrscams/<dépôt>`). L'orchestratrice relit le diff après coup et fait corriger dans la PR suivante.
4. **Vérification toutes les dix minutes** par l'orchestratrice : tâches finies, PR vertes non fusionnées, sessions ou worktrees arrêtés ou bloqués, tâche suivante à lancer.
5. **Ce qui n'attend plus l'utilisateur** : la relecture des décisions proposées (Q72 à Q81, Q96 à Q102), qui restent marquées « proposées, non relues » dans les deux G, et la validation du glossaire, dont les termes sont proposés par l'agent.
6. **Le seul arrêt qui attend l'utilisateur** : l'ouverture (M16-29). Elle exige la relecture du glossaire et des traductions par une personne arabophone. Tant qu'elle n'est pas faite, `/ar/` reste construit et testé, mais aucun sélecteur de langue ne le propose.
7. **Arrêt sur blocage réel seulement** : CI rouge non expliquée, conflit non trivial, infrastructure tombée, décision produit à prendre. L'orchestratrice le dit alors clairement.

Phrase de lancement d'une tâche, adaptée à ce dépôt par M16-01 : « Exécute la tâche M16-NN : ton prompt est le bloc M16-NN de `docs/M-plan-version-arabe-api.md` ; applique aussi M4.3 et M4.5 pour la fin de tâche. » (M4.4 concerne les tâches web et reste dans le document du web.)

## M5. Prompts API

Manœuvre : dans `psymate-api`, `git checkout main && git pull && claude --worktree <ID>`, coller le prompt. À la fin : sous-agent `reviewer`, lecture humaine (migration, diff de `contract/openapi.yaml`, un IT), `gh pr merge <n> --squash --delete-branch`.

### M16-01 — Décisions et documentation

```text
Tâche M16-01 — Inscrire le jalon M16 « Version arabe » dans la documentation de psymate-api (docs/, CLAUDE.md et .claude/agents seulement ; aucun code Java, aucune migration).

Contexte : lis ../psymate-web/docs/M-plan-version-arabe.md en entier (il fait foi) et ../psymate-web/docs/Etude-traduction-arabe.md (section C), docs/G-decisions.md (format des lignes du dernier jalon, table G5), le dernier plan de jalon copié du web (modèle), docs/C-plan.md, docs/D-orchestration-backend.md, CLAUDE.md, .claude/agents/backend-module-dev.md. Puis confronte M2.3 au code : comment chaque module résout la langue d'une requête, où la préférence locale est validée, quelles tables portent label_fr, comment les e-mails choisissent leur langue, où le serveur met en forme une date ou un nombre.

Objectif :
- Nouveau fichier docs/M-plan-version-arabe-api.md : copie de M1, M2.1, M2.3, M4.1, M4.3, M5 et de la partie API de M8 et M9, avec en tête la date, le renvoi au document du web pour la partie web, la phrase « Pour toute tâche M16, ce document fait foi » et la mention exacte de ce qui est tranché par l'utilisateur (M2.1) et de ce qui est proposé par le plan (M2.3).
- G : nouvelle section pour le jalon M16 (G12 si le numéro est libre) avec Q96 à Q102, une ligne par question, même style que le jalon précédent ; une ligne dans la table G5. Si la lecture du code contredit une proposition de M2.3, écris la version corrigée et signale l'écart dans la PR.
- C : jalon M16 (objectif, démontrable) et nouvelle section avec le tableau des tâches M16-01 à M16-06, critères repris de M5.
- D : lignes, vagues et couloirs de M4.1.
- CLAUDE.md et .claude/agents/backend-module-dev.md : une ligne de renvoi vers ce plan pour toute tâche M16, avec les trois règles ajoutées de M4.3.

Périmètre : docs/**, CLAUDE.md, .claude/agents/backend-module-dev.md. Rien d'autre.

Critères d'acceptation :
- Q96 à Q102 existent, avec la mention exacte de qui a tranché quoi ;
- chaque tâche M16-01 à M16-06 a sa ligne dans C et D avec les dépendances de M4.1 ;
- la PR liste les écarts constatés entre M2.3 et le code, ou dit qu'il n'y en a pas ;
- aucune source Java, aucune migration, aucun contrat modifié.

Interdits : toute modification de code, de migration ou de contrat ; réécrire docs/progress/index.html ou un événement existant ; inscrire une décision que M ne contient pas ; écrire qu'une décision de M2.3 a été « tranchée par l'utilisateur ».

Livrable : PR « M16-01 Version arabe : décisions et documentation », puis make log TASK=M16-01 AGENT=claude STATUS=done SUMMARY="…" PR=<url>. Les décisions Q96 à Q102 sont inscrites avec la mention « proposée par le plan M, non relue par l'utilisateur au 23 novembre 2026 ». Fusion : M4.5.
```

### M16-02 — Langue de la requête

```text
Tâche M16-02 — Résoudre la langue d'une requête en un seul endroit et accepter l'arabe (psymate-api). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (M2.3 Q96, Q97, Q101, M4.3). Repère toutes les résolutions locales de la langue (recherche de Locale.FRENCH, Locale.ENGLISH, "en".equals, request.getLocale) : classes de messages des modules (AgendaMessages, ClinicalMessages et leurs semblables), assembleurs de liens, services d'envoi d'e-mail, export. Repère où la préférence locale est validée.

Objectif :
- Un seul composant de shared rend la langue servie (fr, en, ar) à partir d'Accept-Language, français par défaut ou si la langue demandée n'est pas servie ; chaque module l'emploie à la place de sa propre règle.
- La préférence locale accepte ar ; le contrat le dit (énumération fr, en, ar).
- Les dates et nombres que le serveur met en forme en arabe sont en chiffres latins et en calendrier grégorien.
- Tant que messages_ar.properties n'existe pas (M16-04), une requête en arabe reçoit les messages français, sans erreur.

Périmètre : shared, les classes de messages et de résolution de langue des modules, la validation des préférences dans identity, contract/openapi.yaml (énumération de locale seulement), tests.

Critères d'acceptation :
- les réponses pour fr et en sont identiques avant et après : tests existants verts sans les modifier ;
- un test par module prouve qu'une requête avec Accept-Language: ar ne provoque aucune erreur et reçoit le repli français ;
- la préférence locale = ar est acceptée, toute autre valeur est refusée comme avant ;
- plus aucune résolution locale de la langue hors de shared : test d'architecture, ou la recherche et son résultat cités dans la PR ;
- un test prouve qu'une date mise en forme en arabe ne contient aucun chiffre arabo-indien.

Interdits : traduire quoi que ce soit ; changer un message existant ; dépendance Maven ; toucher aux exports, au résumé « En bref » ou à la recherche au-delà du remplacement de la résolution de langue.

Précisé par M16-01 (M2.4 points 1 à 3, 6, 8 et 9, G Q96, Q97, Q101, Q102) : le composant de shared convertit aussi une étiquette enregistrée (colonne practitioner.locale, préférence locale, booking_request.locale) en langue servie, et chaque e-mail garde la source qu'il lit aujourd'hui ; la locale servie pour l'arabe est ar-u-nu-latn (avec ar seule, MessageFormat écrit des chiffres arabo-indiens) et un test le prouve sur une date et sur un message qui contient un nombre ; la migration V16 élargit à ar les contraintes CHECK de practitioner.locale (V01) et booking_request.locale (V15), sinon une inscription en Accept-Language: ar finit en 500, et E4 et E3 la décrivent ; le champ locale de la demande de rendez-vous en ligne (agenda/dto/CreateBookingRequest.java, @Pattern fr|en) accepte ar, et le contrat le dit ; aucun fichier messages.properties de base n'existe et les sources sont en fallbackToSystemLocale(false) : le repli français d'une requête en arabe passe par la langue servie, jamais par l'absence de fichier ; le résumé « En bref » (NarrativeComposer) et l'export (ExportTexts, PatientExportService) résolvent ar en fr explicitement (Q102) ; partir de shared.problem.ProblemMessages.localeOf, qui lit déjà Accept-Language. Périmètre étendu en conséquence : + agenda (CreateBookingRequest, résolution de langue des services de prise de rendez-vous), + db/migration (V16), + docs/E-modele-donnees.md.

Livrable : PR « M16-02 Langue de la requête : fr, en, ar », puis make log TASK=M16-02 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

### M16-03 — Catalogues

```text
Tâche M16-03 — Donner un libellé arabe aux catalogues (psymate-api). Utilise l'agent backend-module-dev. Dépend de M16-02 et du glossaire (M16-07 fusionnée dans psymate-web).

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (Q98, M4.3), ../psymate-web/docs/design/arabe-glossaire.md (emploie ses termes tels quels : ils sont proposés et seront relus par une personne arabophone avant l'ouverture), les migrations qui créent label_fr et label_en (V01, V02, V04), les vues et les routes d'administration des catalogues (émotions, types d'entrée, types de lien, vocabulaire de l'annuaire), docs/E-modele-donnees.md.

Objectif :
- Migration V16 (ou le numéro libre suivant) : label_ar nul par défaut sur chaque catalogue qui porte label_fr, inverse_label_ar sur les types de lien ; les lignes des catalogues globaux reçoivent leur libellé arabe du glossaire.
- Les vues exposent labelAr (et inverseLabelAr) ; les routes d'administration l'acceptent, avec les mêmes règles que labelEn (vide = effacé, longueur maximale identique).
- Le verbe d'un lien rendu selon la langue suit la règle de repli : arabe, sinon français.
- Les émotions et étiquettes créées par un praticien ne changent pas : un seul libellé, celui qu'il a saisi.
- E reçoit les colonnes et la ligne de la migration.

Périmètre : vocabulary, directory, linking (ou le module qui porte les types de lien), db/migration, contract/openapi.yaml, docs/E-modele-donnees.md, tests.

Critères d'acceptation :
- la migration passe sur une base vide et sur une base au seed small ;
- un catalogue sans libellé arabe rend le français quand l'arabe est demandé ; avec un libellé arabe, il le rend ;
- les réponses pour fr et en sont inchangées, hormis les champs ajoutés ;
- le diff du contrat se limite aux champs labelAr et inverseLabelAr ;
- la PR liste les lignes de catalogue restées sans libellé arabe, faute de terme au glossaire.

Interdits : rendre label_ar obligatoire ; modifier une migration existante ; inventer une traduction hors glossaire ; dépendance Maven.

Précisé par M16-01 (M2.4 points 7 et 9, G Q98) : la migration est V17 (V16 est prise par M16-02) ; les modalités (in_person, video, home) et les langues parlées de l'annuaire, servies en constantes par directory/service/DirectoryVocabularyService.java dans le record directory/dto/Item.java, reçoivent aussi labelAr : libellé du glossaire pour les modalités, Locale.getDisplayLanguage en arabe pour les langues ; le diff du contrat se limite alors à labelAr et inverseLabelAr.

Livrable : PR « M16-03 Catalogues : libellés arabes », puis make log TASK=M16-03 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

### M16-04 — Messages en arabe

```text
Tâche M16-04 — messages_ar.properties (psymate-api). Utilise l'agent backend-module-dev. Dépend de M16-02 et du glossaire (M16-07 fusionnée dans psymate-web).

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (Q99, M4.3), ../psymate-web/docs/design/arabe-glossaire.md (termes et règles de registre), src/main/resources/messages_fr.properties (source) et messages_en.properties (pour lever une ambiguïté), et quelques appelants pour comprendre ce que portent les paramètres {0}, {1}.

Objectif :
- messages_ar.properties porte toutes les clés de messages_fr.properties, en arabe standard moderne, dans l'encodage qu'emploient déjà les deux autres fichiers.
- Registre de l'interface web : phrases courtes, formes sans accord de genre pour le praticien et pour la personne suivie, pas d'impératif genré.
- Les paramètres gardent leur sens et leur ordre logique ; un message qui dépend d'un nombre reste juste pour un, deux, quelques et beaucoup (reformule pour éviter l'accord quand c'est possible).
- Un test échoue si une clé manque ou est en trop dans une des trois langues.

Périmètre : src/main/resources/messages_ar.properties, un test de parité, la configuration de la source de messages si elle liste les langues.

Critères d'acceptation :
- le test de parité est vert, et échoue si on retire une clé d'un des trois fichiers (montre-le dans la PR) ;
- une erreur de validation demandée avec Accept-Language: ar revient en arabe (un IT) ;
- aucun message fr ou en modifié ;
- la PR liste les messages dont la traduction est incertaine, pour le relecteur arabophone.

Interdits : traduire un code d'erreur, un nom de champ, « PsyMate » ; employer un terme métier absent du glossaire ; dépendance Maven.

Précisé par M16-01 (G Q99, Q102) : les familles overview.narrative.* (27 clés) et clinical.export.* (79 clés) restent hors de messages_ar.properties (second temps, M16-30 et M16-31) ; le test de parité les nomme comme seules exceptions pour l'arabe et reste strict entre fr et en.

Livrable : PR « M16-04 Messages en arabe », puis make log TASK=M16-04 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

### M16-05 — E-mails en arabe

```text
Tâche M16-05 — E-mails en arabe, de droite à gauche (psymate-api). Utilise l'agent backend-module-dev. Dépend de M16-04.

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (Q100, M4.3), les services d'envoi (AuthMailer, VerificationMailer, AdminMailer, ContactMailer et tout autre apparu depuis, par exemple pour la prise de rendez-vous), la façon dont chacun assemble son texte (aucun dossier src/main/resources/templates : chaque service envoie un corps text/plain seul, bâti à partir des clés *.mail.* de messages_*.properties, M2.4 point 5), et comment chacun choisit la langue du destinataire (préférence du praticien, langue de la requête du visiteur).

Objectif :
- Chaque e-mail existe en arabe : objet, corps HTML, corps en texte brut.
- Le HTML arabe porte lang="ar" et dir="rtl" ; les liens, adresses, codes et dates chiffrées sont isolés du sens du texte, pour ne pas s'afficher à l'envers.
- Un e-mail au praticien suit sa préférence locale ; un e-mail à un visiteur suit la langue de sa requête ; français par défaut.
- Les dates et nombres des e-mails arabes sont en chiffres latins et en calendrier grégorien.

Périmètre : les services d'envoi, leurs modèles, messages_*.properties pour les clés d'e-mail, tests.

Critères d'acceptation :
- un test par e-mail vérifie, pour l'arabe, la langue du texte, lang et dir ;
- les e-mails fr et en sont identiques avant et après (tests existants verts sans les modifier) ;
- un e-mail arabe ouvert dans Mailpit s'affiche de droite à gauche, lien cliquable (capture dans la PR).

Interdits : journaliser une adresse ou un contenu d'e-mail ; changer le texte d'un e-mail fr ou en ; dépendance Maven.

Précisé par M16-01 (M2.4 point 5, G Q100) : cinq services d'envoi sur main = 670a375 (AuthMailer, VerificationMailer, AdminMailer, ContactMailer, BookingMailer, ce dernier appelé aussi par BookingNotifier et BookingExpiryJob). En arabe seulement, l'e-mail part en multipart/alternative : texte brut et corps HTML minimal produit par le code (aucun moteur de modèles ajouté) ; en fr et en, il reste en texte brut seul, octet pour octet.

Livrable : PR « M16-05 E-mails en arabe », puis make log TASK=M16-05 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

### M16-06 — Seed, contrat, suivi

```text
Tâche M16-06 — Clore la partie API du jalon M16 (psymate-api). Utilise l'agent seed-perf. Dépend de M16-03 et M16-05.

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md, M4.3 point 2 (règle de version du contrat), le seed existant et la tâche de clôture du jalon précédent (modèle).

Objectif :
- Le seed, par l'API seulement, donne à un praticien de démonstration la préférence locale = ar et, dans un de ses dossiers, quelques entrées dont le titre et le texte sont en arabe, plus une entrée qui mêle arabe et français ; rien n'est retiré du seed existant.
- La version du contrat suit la règle de M4.3 point 2 ; le tag contract-v0.16.0 est posé ; docs/api-reference est régénéré si le dépôt le fait.
- Le suivi est à jour.

Critères d'acceptation :
- make seed SIZE=small puis une seconde fois ne change rien ;
- le tag de contrat existe et porte locale = ar, labelAr et inverseLabelAr ;
- le praticien de démonstration en arabe reçoit ses catalogues en arabe (un IT ou une requête citée dans la PR) ;
- ./mvnw verify vert.

Interdits : écrire dans la base hors de l'API ; poser un tag qui existe déjà.

Précisé par M16-01 (G Q102) : le contrat dit, dans la description du résumé « En bref » et des routes d'export, que l'arabe demandé y reçoit le français, tant que M16-30 et M16-31 ne sont pas faites.

Livrable : PR « M16-06 Version arabe : seed et contrat », puis make log TASK=M16-06 AGENT=seed-perf STATUS=review SUMMARY="…" PR=<url>.
```

## M8. Second temps (proposé, à confirmer)

Ces tâches ne bloquent pas l'ouverture. Elles ne sont lancées que si l'utilisateur les confirme ; leurs prompts sont plus courts que les autres, parce que le code concerné n'a pas été lu en détail pour ce plan.

### M8.1 API

| ID | Titre | Couloir | Dépend de | Parallèle avec |
| --- | --- | --- | --- | --- |
| M16-30 | Résumé « En bref » rédigé en arabe | overview | M16-06 | M16-31, M16-32 |
| M16-31 | Exports Markdown et PDF en arabe | clinical (export) | M16-06 | M16-30, M16-32 |
| M16-32 | Recherche plein texte en arabe | search | M16-06 | M16-30, M16-31 |

#### M16-30 — Résumé « En bref » en arabe

```text
Tâche M16-30 — Rédiger le résumé « En bref » en arabe (psymate-api). Utilise l'agent backend-module-dev. Second temps du jalon M16 : ne commence que si l'utilisateur l'a confirmé.

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (Q102, M8, M4.3), ../psymate-web/docs/design/arabe-glossaire.md, le code qui rédige le résumé par règles (jusqu'à cinq phrases, chacune omise si ses données manquent, sans IA) et ses tests en français et en anglais.

Objectif : les mêmes phrases en arabe standard moderne, justes pour un, deux, quelques et beaucoup (séances, jours, entrées), sans accord de genre fautif pour la personne suivie (tournures neutres) ; chiffres latins ; le contrat dit que le résumé suit Accept-Language pour les trois langues.

Critères d'acceptation : un test par phrase et par forme de nombre (0, 1, 2, 3, 11, 100) ; les résumés fr et en sont identiques avant et après ; la PR donne dix résumés arabes produits sur le seed, pour le relecteur arabophone.

Interdits : appeler un service externe ou un modèle de langue ; changer les règles de sélection des phrases ; dépendance Maven.

Livrable : PR « M16-30 Résumé En bref en arabe », puis make log TASK=M16-30 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

#### M16-31 — Exports en arabe

```text
Tâche M16-31 — Exports Markdown et PDF en arabe (psymate-api). Utilise l'agent backend-module-dev. Second temps du jalon M16 : ne commence que si l'utilisateur l'a confirmé.

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (Q102, M8, M4.3), clinical/export (ExportTexts, ExportDates, HtmlRenderer, PdfRenderer, ExportJobRunner, PatientExportService). Constat du 22 novembre 2026, à revérifier : le PDF est produit par openhtmltopdf ; PdfRenderer charge les polices du système (Segoe UI, DejaVu) et aucune police arabe ; l'export asynchrone et un appel de PatientExportService sont fixés en français.

Objectif :
- Les textes de l'export existent en arabe ; l'export suit la langue demandée dans ses deux formes, immédiate et asynchrone.
- Le PDF arabe est lisible : police arabe embarquée dans l'application (IBM Plex Sans Arabic, licence OFL, la même qu'à l'écran), lettres liées, texte de droite à gauche, texte mêlé arabe et latin dans le bon ordre. Si openhtmltopdf a besoin d'un module pour la mise en forme bidirectionnelle, annonce la dépendance Maven dans la PR avant de l'ajouter et attends l'accord.
- Le contenu saisi garde son propre sens, paragraphe par paragraphe.
- Chiffres latins, calendrier grégorien.

Critères d'acceptation : un test extrait le texte du PDF arabe et y retrouve une phrase attendue dans l'ordre logique ; une capture du PDF arabe dans la PR ; les exports fr et en sont identiques avant et après, octet pour octet pour le Markdown ; l'export d'un dossier qui mêle arabe et français est lisible.

Interdits : dépendre d'une police installée sur le serveur ; ajouter une dépendance Maven sans accord ; journaliser un contenu d'export.

Livrable : PR « M16-31 Exports en arabe », puis make log TASK=M16-31 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

#### M16-32 — Recherche plein texte en arabe

```text
Tâche M16-32 — Recherche plein texte adaptée à l'arabe (psymate-api). Utilise l'agent backend-module-dev. Second temps du jalon M16 : ne commence que si l'utilisateur l'a confirmé.

Contexte : lis CLAUDE.md, docs/M-plan-version-arabe-api.md (Q102, M8, M4.3), le module search et ses migrations (V08 et suivantes), docs/E-modele-donnees.md. Constat du 22 novembre 2026, à revérifier : les index emploient les configurations french et simple avec unaccent et pg_trgm ; la version de PostgreSQL et la présence de la configuration arabic n'ont pas été vérifiées.

Objectif :
- Commence par mesurer : sur le seed enrichi d'entrées arabes (M16-06), ce que la recherche actuelle trouve et manque (formes de alif, ta marbuta, article collé, pluriels). Donne ces résultats dans la PR.
- Propose puis réalise la plus petite évolution qui corrige les manques : normalisation des variantes de lettres à l'indexation et à la requête, et configuration de recherche adaptée au contenu arabe, sans dégrader la recherche française.
- Une nouvelle migration, jamais une migration modifiée ; réindexation décrite et testée.

Critères d'acceptation : les requêtes arabes de la mesure trouvent leurs entrées après la tâche (tests) ; chaque test de recherche français existant reste vert sans être modifié ; le temps de réponse sur le seed de performance ne se dégrade pas (chiffres avant et après dans la PR).

Interdits : extension PostgreSQL nouvelle sans accord ; dépendance Maven ; changer le contrat de la recherche.

Livrable : PR « M16-32 Recherche en arabe », puis make log TASK=M16-32 AGENT=backend-module-dev STATUS=review SUMMARY="…" PR=<url>.
```

## M9. Ce qui reste ouvert (partie API)

- **Qui traduit, qui valide.** L'agent propose le glossaire (M16-07) et les traductions (M16-04, M16-26 à M16-28), fusionnés sans attendre. La relecture par une personne arabophone reste à organiser par l'utilisateur ; elle conditionne l'ouverture (M16-29) et rien d'autre. Si un traducteur professionnel est retenu, il corrige le fichier livré.
- **Décisions proposées** (Q72 à Q81, Q96 à Q102, dont l'arabe standard moderne) : inscrites comme proposées, non relues par l'utilisateur.
- **Second temps** (M8) : à confirmer, avec ce que voit le praticien en attendant.
- **Émotions créées par un praticien** : elles n'ont qu'un libellé, rangé dans `labelFr` quelle que soit la langue de saisie. Rien ne change ici ; à renommer un jour côté API si cela gêne.
- **Facturation (M15)** : son plan n'est pas sur `main` et réservait `V16` et le contrat `0.16.0`, que M16 prend (avec `V17`, M2.4 point 9). À corriger dans le plan L avant son lancement : M15 commencera à `V18`.
- **Langue des e-mails du compte** (constat de M16-01, M2.4 point 1) : la colonne `practitioner.locale`, posée à l'inscription, et la préférence `locale`, modifiable, peuvent différer sur `main` ; les e-mails du compte, de la vérification et de l'administration lisent la colonne. M16 n'y touche pas. Les synchroniser, ou remplacer l'une par l'autre, est une décision de l'utilisateur.
- **Effort** : 6 PR côté API et 23 côté web jusqu'à l'ouverture, 4 de plus pour le second temps. Non chiffré en jours.
