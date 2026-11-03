# Document F – Mode opératoire des agents · Backend `psymate-api`

28 octobre 2026 · @Hayes · Version 1.2 (29 octobre 2026 : questions tranchées, noms PsyMate ; 2 novembre 2026 : lot 10, compléments du contrat pour la V1 web) · Ce document est fait pour être **suivi**, pas lu. Partie 1 : les décisions prises sur les questions ouvertes (détail dans `docs/G-decisions.md`). Partie 2 : ce qu'il faut créer une fois avant le premier agent. Partie 3 : les lots, dans l'ordre, avec pour chaque tâche l'explication en clair, l'agent à lancer, les commandes à taper et le prompt exact à coller.

Les prompts renvoient aux documents copiés dans `docs/` du dépôt : `docs/A-architecture.md` (étude d'architecture), `docs/B-ux.md` (expérience utilisateur), `docs/C-plan.md` (plan et tâches), `docs/D-orchestration-backend.md` (ordre et dépendances), `docs/E-modele-donnees.md` (base de données), `docs/G-decisions.md` (décisions sur les questions ouvertes), `docs/design/*.md` (fiches par écran). Quand C et E se contredisent sur la base de données, E l'emporte (E8). Quand G contredit un autre document, G l'emporte.

---

## Lexique : décoder les codes que vous verrez dans les prompts

| Code | Signification |
| --- | --- |
| **A**, **B**, **C**, **D**, **E**, **G** | Les documents de référence : A = architecture, B = UX, C = plan, D = orchestration backend, E = modèle de données, G = décisions. « A4.3 » = document A, section 4.3. |
| **A10** | Section de A ajoutée le 25 octobre : préférences par compte, vérification des praticiens (dossier professionnel), administration. |
| **A11** | Section de A sur le « compagnon patient » (future app mobile) : rien n'est construit, mais des colonnes et tables vides sont réservées. |
| **M0 … M7** | Les huit jalons produit du plan C (M8, migration JPA, et M9, restructuration, sont des séries techniques décrites en C8) : M0 socle technique, M1 patients et entrées, M2 liens et graphe, M3 timeline, M4 recherche et vue d'ensemble, M5 annuaire public, M6 capture rapide et finitions, M7 durcissement avant la bêta. |
| **M1-03**, **M4-01a**… | Une tâche : jalon, tiret, numéro. La ligne correspondante est dans C2, C6 ou C8 (document C). `M4-01a` est une tâche ajoutée par D (D6-A1). |
| **V0 … V9** | Les « vagues » de D3 : un groupe de tâches dont toutes les dépendances sont fusionnées, donc lançables ensemble. |
| **Couloir** | Un module Spring (identity, patients, clinical…). Un seul agent à la fois par couloir. |
| **UC-01 … UC-19** | Cas d'usage (A2, A10) : un scénario utilisateur numéroté. |
| **F1 … F33** | Exigences fonctionnelles (A2, A10). |
| **ADR-01 … ADR-13** | Décisions d'architecture consignées (A9, A10, A11). |
| **Q1 … Q30** | Les questions ouvertes, tranchées dans G. |
| **V01 … V10** | Migrations Flyway (E4) : les fichiers SQL qui créent la base, dans l'ordre. |
| **Spring Modulith** | La bibliothèque qui découpe le monolithe en modules étanches et vérifie les frontières par test. |
| **Outbox / `event_publication`** | La table où chaque événement métier (« entrée créée », « lien accepté ») est enregistré avant d'être consommé par la recherche, la vue d'ensemble, et plus tard l'IA. |
| **Worktree** | Une copie de travail séparée du dépôt git, avec sa propre branche. Chaque agent travaille dans la sienne : ils ne se gênent pas. |
| **Contrat `contract-vX.Y.Z`** | Le fichier OpenAPI généré par le backend, tagué dans git. Le frontend ne consomme que des tags. |

---

# Partie 1 — Décisions prises (29 octobre 2026)

Toutes les questions ouvertes de la version 1.0 sont tranchées. Le détail et les justifications sont dans `docs/G-decisions.md` ; voici ce qui change concrètement pour les agents. Chaque prompt de la Partie 3 cite la décision qu'il applique.

| # | Décision appliquée dans les prompts |
| --- | --- |
| Q1 | Produit **PsyMate**, package **`com.psymate`**, domaine **psymate.com**, `app.name = PsyMate`. |
| Q2 | Dépôts privés sur le compte GitHub **`mrscams`** : `psymate-api` (backend), `psymate-web` (frontend, Angular). Pas de licence. |
| Q3 | Boot 4.1 / Modulith 2.1.2 / Java 21 ; l'agent M0-01 lit la doc officielle pour les starters. |
| Q4 | Expéditeur dev `test@gmail.com` (Mailpit), prod `noreply@psymate.com` ; fournisseur SMTP choisi avec Q16. |
| Q5 | Premier admin `hushan@gmail.com` via `make admin EMAIL=…` (M7-08), mot de passe affiché une fois dans le terminal. |
| Q6 | Session 12 h, 30 jours avec « rester connecté ». |
| Q7 | Tags, versions et drapeaux de patient : oui. |
| Q8 | Fusion d'émotions custom en V1 ; promotion en V2. |
| Q9 | Rôle `psymate_app` créé par l'init du conteneur ; Flyway fait les REVOKE. Base et utilisateur `psymate`. |
| Q10 | Pas d'atelier pilote ; défauts de E5 livrés tels quels. |
| Q11 | 29 émotions de E5.2 livrées telles quelles (`shame` en `disgust`, `calm` en `trust`, libellés conservés). |
| Q12, Q13 | Matrice des liens et couleurs des familles : conservées. |
| Q14 | Toutes les professions : diplôme **et** pièce d'identité obligatoires ; ADELI/RPPS recommandé. |
| Q15 | Spécialités et publics : liste fermée, modifiable par l'admin, pas de saisie libre. |
| Q16, Q17 | Étude hébergement HDS plus tard ; M7-06 prépare la passation ; stockage des justificatifs en bêta décidé alors. |
| Q18 | Export du compte refusé : oui, `export_job` ajouté à M6-05. |
| Q19, Q20 | Mineurs : rien en V1. Numéros d'urgence en pied de page : non. |
| Q21 | Inscription ouverte ; pas de code d'invitation. |
| Q22 | Patient DEMO généré par M7-01, relu ensuite. |
| Q23 | Français d'abord ; structure i18n conservée, fichiers EN peuvent être en retard. |
| Q24 | Bucket4j, valeurs actuelles. |
| Q25 | Agenda Google hors V1 ; pas de jalon Agenda (ancien M8 ; M8 désigne désormais la migration JPA, C8). |
| Q26 | Signalements de l'annuaire : V1.1. |
| Q27, Q28 | **M1-14b** créée (comptes, suspension, journal, recours). |
| Q29, Q30 | `security_event` + vue `admin_journal_v` ; `clinical_rev` incrémenté par l'application. |
| — | Préférence `sessions.defaultDurationMin` = 60 par défaut. |

---

# Partie 2 — Préparation (une seule fois, avant le premier agent)

Durée : une heure. Tout se fait à la main, sans agent.

## 2.1 Prérequis sur votre machine

```bash
git --version          # ≥ 2.40
gh --version           # GitHub CLI, connecté : gh auth status
java -version          # Temurin 21
docker --version       # Docker Desktop lancé
claude --version       # Claude Code
```

## 2.2 Cloner le dépôt et y copier la documentation

Le dépôt `mrscams/psymate-api` existe déjà (privé, vide, créé le 29 octobre).

```bash
gh repo clone mrscams/psymate-api
cd psymate-api
mkdir -p docs/design .claude/agents
# Copier la documentation du projet (dossier claude/) dans docs/ :
#   docs/00-contexte.md, A-architecture.md, B-ux.md, C-plan.md, D-orchestration-backend.md,
#   E-modele-donnees.md, F-runbook-agents-backend.md, G-decisions.md, Etude-couleurs-Nordique.md, design/*.md
```

## 2.3 Créer `CLAUDE.md` à la racine (copier tel quel)

> Version d'origine, pour l'historique. Le fichier `CLAUDE.md` du dépôt fait foi : il a évolué depuis (règle de grâce de 1 h 15, G Q31 ; accès aux données, G Q32 ; structure par rôle, lecture des projections, suppressions autorisées et SQL natif, G Q33 à Q38).

```markdown
# psymate-api

## Ce que c'est
API de PsyMate, suivi clinique pour psychologues, psychiatres et coachs. Monolithe modulaire Spring Boot 4.1 (Spring Framework 7, Jackson 3) / Spring Modulith 2.1.2 / Java 21 / PostgreSQL 16. Package racine : com.psymate.
Documents de référence dans docs/ : A-architecture.md, B-ux.md, C-plan.md, D-orchestration-backend.md, E-modele-donnees.md, G-decisions.md, design/*.md.
Pour la base de données, docs/E-modele-donnees.md fait autorité sur A et C. Pour toute question tranchée, docs/G-decisions.md fait autorité sur tous les autres.

## Commandes (utilise uniquement celles-ci)
- make up / make down : Postgres + Mailpit
- ./mvnw verify : build + tests + vérification des modules (doit être vert avant toute PR)
- ./mvnw test -Dtest=NomDuTest : un test
- ./mvnw spring-boot:run : API sur :8080
- make seed : données fictives
- make contract : regénère contract/openapi.yaml
- make db-reset : recrée la base locale depuis zéro

## Architecture (règles non négociables)
- Un module Spring Modulith par contexte : identity, patients, vocabulary, clinical, linking, search, overview, events, directory. Packages réservés et vides : ai, companion.
- Un module ne lit jamais les tables ni les classes internes d'un autre ; il passe par la façade du package `api` ou par un événement.
- Dépendances autorisées : voir package-info.java de chaque module (ApplicationModules.verify() les vérifie).
- directory ne dépend que d'identity et events, et ne touche jamais aux données patient. identity.admin ne dépend d'aucun module clinique.
- Toute entrée appartient à une séance ; toute correction crée une version (sauf règle de grâce 5 min) ; rien n'est supprimé physiquement.
- Les liens sont des lignes de `link` avec provenance et statut ; jamais de clé étrangère "lien" dans `entry`.
- Une migration Flyway fusionnée ne se modifie jamais : on en ajoute une. Les numéros sont réservés dans docs/E-modele-donnees.md E4.
- L'IA V2 n'existe pas en V1 : ne pas écrire de code IA, mais ne pas casser les hooks (outbox, tables vides).
- Le compagnon patient n'existe pas en V1 (A11) : `entry.author_type` vaut toujours 'practitioner', `entry.submission_id` toujours NULL, `patient_device` et `patient_submission` restent vides, le package `companion` reste vide. Ne jamais les supprimer, ne jamais les remplir, ne jamais exposer `authorType` en écriture.
- L'agenda Google (fiches 15, 15b) est hors V1 (G, Q25) : aucun module scheduling.

## Versions
Spring Boot 4.1.x, Spring Modulith 2.1.2, Java 21 : ne jamais rétrograder, ne jamais ajouter une dépendance de la ligne Boot 3 / Jackson 2 (`com.fasterxml.jackson.*` est Jackson 2 ; Jackson 3 vit sous `tools.jackson.*`). Les noms de starters sont ceux de la ligne 4 : vérifie dans la documentation officielle, pas de mémoire. Toute version est figée dans pom.xml, jamais une plage.

## Conventions
REST JSON camelCase, erreurs RFC 9457 (application/problem+json) avec errors[] par champ, pagination par curseur, dates UTC ISO 8601, MapStruct pour les DTO, Jakarta Validation.
Textes utilisateur et e-mails : français d'abord (messages_fr.properties obligatoire, messages_en.properties si tu peux, jamais bloquant). Nom du produit : constante i18n app.name = PsyMate.
Tests : unitaires pour le domaine, *IT.java Testcontainers (image pgvector/pgvector:pg16) pour chaque endpoint, jamais de mock de la base.
Nommage : EntryService, EntryRepository, EntryController, EntryDto / CreateEntryRequest ; événements au passé (EntryCreated).

## Ne fais jamais
- Ajouter une dépendance Maven sans le dire dans la PR.
- Désactiver un test, baisser la couverture ou supprimer une règle ArchUnit.
- Écrire un secret, une URL de prod, ou appeler un service externe.
- Modifier le contrat OpenAPI sans bump de version.
- Travailler hors du module indiqué dans ta tâche.

## Fin de tâche
./mvnw verify vert, contrat à jour, PR ouverte avec le template rempli (ID de la tâche, critères cochés un par un, tests ajoutés, dépendances Maven ajoutées listées).
```

## 2.4 Créer `.claude/settings.json`

```json
{
  "permissions": {
    "allow": [
      "Bash(./mvnw *)", "Bash(make *)", "Bash(docker compose *)",
      "Bash(git status*)", "Bash(git diff *)", "Bash(git log *)", "Bash(git add *)",
      "Bash(git commit *)", "Bash(git push *)", "Bash(gh pr *)"
    ],
    "deny": ["Bash(rm -rf *)", "Bash(git push --force *)", "Read(.env*)"]
  },
  "hooks": {
    "PostToolUse": [
      { "matcher": "Edit|Write", "hooks": [ { "type": "command", "command": "./mvnw spotless:apply -q 2>/dev/null || true" } ] }
    ],
    "Stop": [
      { "hooks": [ { "type": "command", "command": "test -f pom.xml && (./mvnw verify -q || (echo 'Tests rouges : corrige avant de terminer' >&2; exit 2)) || true" } ] }
    ]
  }
}
```

Le hook `Stop` empêche un agent de s'arrêter sur des tests rouges. Le `test -f pom.xml` évite qu'il tourne avant que M0-01 ait créé le projet.

## 2.5 Créer les trois fichiers d'agents dans `.claude/agents/`

`.claude/agents/backend-module-dev.md` :

```markdown
---
name: backend-module-dev
description: Implémente UNE tâche du plan C dans UN module Spring de psymate-api. À utiliser pour toute tâche « api / <module> ».
tools: Read, Write, Edit, Glob, Grep, Bash
model: sonnet
---
Tu implémentes exactement la tâche donnée, dans le module donné, et rien d'autre.

Avant d'écrire une ligne :
1. Lis CLAUDE.md.
2. Lis la ligne de ta tâche dans docs/C-plan.md (sections C2, C6 ou C8) et les sections de docs/A-architecture.md ou de la revue (docs/etudes/revue-architecture-2026-11-01.md, tâches M9) citées dans sa colonne « Lire ».
3. Lis docs/D-orchestration-backend.md D1 et D5 pour savoir ce qui existe déjà sur main et ce que tu n'as pas le droit de toucher.
4. Si ta tâche crée ou modifie une table, lis docs/E-modele-donnees.md : le DDL est déjà écrit, copie-le, ne le réinvente pas.
5. Si ton prompt cite une décision « Qn », lis sa ligne dans docs/G-decisions.md et applique-la même si A, C ou E disent autre chose.

Pendant :
- Ne touche qu'aux fichiers de ton module (src/main/java/com/psymate/<module>/**, ses tests, et la migration que E4 t'attribue).
- Si tu as besoin d'une classe d'un autre module, passe par son package `api`. Si elle n'existe pas, arrête-toi et dis-le dans ton rapport au lieu de l'inventer.
- Chaque critère d'acceptation de ta tâche devient un test.

À la fin : ./mvnw verify vert, puis une PR via `gh pr create` avec le template : ID de la tâche, critères cochés un par un, tests ajoutés, dépendances Maven ajoutées (ou « aucune »), décisions prises quand la documentation était ambiguë.
```

`.claude/agents/reviewer.md` :

```markdown
---
name: reviewer
description: Relit le diff d'une tâche sans rien modifier. À lancer avant chaque fusion.
tools: Read, Glob, Grep, Bash
model: sonnet
---
Tu relis le diff de la branche courante par rapport à main (`git diff main...HEAD`). Tu ne modifies aucun fichier.

Vérifie, dans cet ordre, et réponds par une liste « bloquant / à corriger / remarque » :
1. Frontières : aucun import d'un package interne d'un autre module (seulement `com.psymate.<module>.api`). `identity.admin` et `directory` n'importent rien de patients, clinical, linking, overview, search.
2. Critères : reprends la ligne de la tâche dans docs/C-plan.md ; pour chaque critère d'acceptation, nomme le test qui le démontre, ou écris « non couvert ».
3. Base : si une migration est présente, compare-la au DDL de docs/E-modele-donnees.md ; signale toute colonne ajoutée, retirée ou renommée, et vérifie que le numéro de migration est celui de E4.
4. Réserves : aucune suppression ni écriture de `author_type`, `submission_id`, `patient_device`, `patient_submission`, `entry_embedding`, `entry_chunk`, `ai_feedback`, `patient_access` ; `authorType` absent des DTO de création/modification.
5. Versions : aucun import `com.fasterxml.jackson.*`, aucun starter de la ligne Boot 3, aucune nouvelle dépendance non annoncée dans la PR.
6. Secrets, URL de prod, couleurs ou textes en dur.
7. Le contrat OpenAPI : si l'API a changé, `contract/openapi.yaml` a été regénéré et la version bumpée.
8. Décisions : si la tâche cite une décision de docs/G-decisions.md, vérifie qu'elle est appliquée (package com.psymate, rôle psymate_app, liste fermée de l'annuaire, etc.).
```

`.claude/agents/seed-perf.md` (utilisé à partir de M7) :

```markdown
---
name: seed-perf
description: Génère ou étend le seed de données fictives et lance les scénarios de performance de A6.
tools: Read, Write, Edit, Glob, Grep, Bash
model: sonnet
---
Tu travailles uniquement dans tools/seed/ et src/test/.../perf/. Tu lis docs/C-plan.md C3 « Données de démonstration » et docs/E-modele-donnees.md E5.5.
Toute donnée générée respecte E : chaque entrée a un session_id, author_type = 'practitioner', les liens respectent la matrice E5.3.
Tu mesures les latences cibles de docs/A-architecture.md A6 et tu les rapportes dans un tableau.
```

## 2.6 Fichiers git

```bash
printf '.claude/worktrees/\n.env\n.env.*\ntarget/\n' >> .gitignore
printf '.env\n.env.local\n' > .worktreeinclude
git add -A && git commit -m "docs, CLAUDE.md, agents, settings" && git push -u origin main
gh repo edit --default-branch main
```

Protégez `main` dans GitHub (Settings → Branches → require a pull request). Puis lancez `claude` une fois à la racine pour accepter la boîte de confiance du dossier, et quittez.

## 2.7 Comment vous lancez un agent (toujours la même manœuvre)

```bash
cd ~/psymate-api
git checkout main && git pull
claude --worktree <ID-de-la-tâche>      # exemple : claude --worktree M0-03
```

Puis collez le prompt du lot. L'agent travaille dans `.claude/worktrees/<ID>/` sur la branche `worktree-<ID>` et ouvre une PR à la fin.

**Pour lancer plusieurs agents en parallèle** : ouvrez un terminal par agent, et dans chacun tapez la commande ci-dessus avec son ID. Les worktrees sont indépendants ; vous pouvez les lancer dans la minute, sans attendre.

**Quand un agent a fini** (il vous dit qu'une PR est ouverte) :

1. Dans le même terminal, tapez : `Lance le sous-agent reviewer sur cette branche et corrige ce qu'il marque bloquant.`
2. Lisez vous-même trois choses dans la PR sur GitHub : la migration SQL s'il y en a une, le diff de `contract/openapi.yaml`, et un test d'intégration (il raconte ce que fait la fonctionnalité).
3. Fusionnez en squash : `gh pr merge <numéro> --squash --delete-branch`.
4. Quittez l'agent (`/exit`). Il propose de supprimer le worktree : acceptez.
5. Si le lot prévoit un tag de contrat : `git checkout main && git pull && git tag contract-vX.Y.Z && git push --tags`.

**Règle d'or** : on ne lance un lot que quand **toutes** les PR du lot précédent dont il dépend sont fusionnées. Les dépendances exactes sont rappelées en tête de chaque lot.

---

# Partie 3 — Les lots, dans l'ordre

Chaque lot indique : ce qu'il faut avoir fusionné avant ; les agents à lancer (un terminal chacun) ; pour chaque agent, la tâche en clair, puis le prompt à coller tel quel.

## Lot 0 — Le squelette du projet

**Avant de lancer** : Partie 2 terminée (dépôt, `docs/`, `CLAUDE.md`, `.claude/`).
**Agents** : 1 seul (tout le reste attend le `pom.xml`).

Note : la tâche **M0-08** de C (`CLAUDE.md`, `settings.json`, hooks, agents) n'a pas de prompt : vous l'avez faite vous-même en Partie 2, sections 2.3 à 2.5. Sa partie frontend viendra avec le document H.

### Agent 1 · tâche M0-01 — Initialiser `psymate-api`

**En clair** : créer le projet Spring Boot 4.1 vide mais complet : Maven avec wrapper, Java 21, Spring Modulith 2, Flyway, springdoc (génération OpenAPI), Testcontainers, Spotless (formatage), un `Makefile`, un `docker-compose.yml` avec PostgreSQL 16 + pgvector et Mailpit (boîte mail de test), et la CI GitHub Actions. À la fin, `./mvnw verify` est vert, `make up` démarre les conteneurs, et `GET /actuator/health` répond `UP`. Aucun code métier.

```bash
claude --worktree M0-01
```

```text
Tâche M0-01 — Initialiser psymate-api (dépôt psymate-api, racine). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/G-decisions.md G1 (Q1 : nom PsyMate, package com.psymate ; Q9 : base psymate, utilisateur psymate, rôle psymate_app), docs/C-plan.md « Décisions d'outillage » et section C3 (structure du dépôt, environnement local), docs/E-modele-donnees.md E2 (extensions PostgreSQL : pgcrypto, pg_trgm, unaccent, vector, btree_gist) et E4 « Vérifications en CI ».

Objectif : un projet Spring Boot 4.1.x Maven (groupId com.psymate, artifactId psymate-api), Java 21, avec Spring Modulith 2.1.2 (import du BOM spring-modulith-bom en dependencyManagement ; starters spring-modulith-starter-core, spring-modulith-starter-jdbc, spring-modulith-starter-test sans version), Flyway, springdoc-openapi (version compatible Boot 4 / Jackson 3), Testcontainers, Spotless ; un Makefile (cibles up, down, seed, test, run, contract, db-reset) ; un docker-compose.yml avec postgres (image pgvector/pgvector:pg16, base psymate, utilisateur psymate, rôle applicatif psymate_app créé par un script d'init du conteneur) et mailpit (ports 1025 / 8025) ; une CI GitHub Actions qui lance ./mvnw verify sur chaque PR.

Versions : tu lis la documentation officielle de Spring Boot 4.1 pour les noms exacts des starters et des artefacts (plusieurs ont été renommés depuis la ligne 3). Tu figes chaque version dans pom.xml. Aucune dépendance Jackson 2 (com.fasterxml.jackson). Tu listes dans la PR chaque dépendance ajoutée avec sa version et le lien vers sa page de documentation.

Périmètre : pom.xml, mvnw, .mvn/, Makefile, docker-compose.yml, src/main/java/com/psymate/App.java, src/main/resources/application.yml (profils local et test ; app.name=PsyMate ; app.mail.from=test@gmail.com en local ; versioning.grace-period=5m, devenu 75m avec G Q31 ; spring.modulith.events.jdbc.schema-initialization.enabled=false), src/test/java/com/psymate/AppIT.java, .github/workflows/ci.yml, README.md court (commandes). Aucun module, aucune migration, aucune entité : c'est M0-02 et M0-03.

Critères d'acceptation (C2 M0-01) :
- ./mvnw verify vert en local et en CI ;
- make up démarre Postgres et Mailpit ; make db-reset recrée la base locale ;
- GET /actuator/health répond UP ;
- pom.xml ne contient aucune dépendance de la ligne Boot 3 / Jackson 2.

Tests : un test de contexte Spring ; un test Testcontainers minimal (AppIT) qui démarre pgvector/pgvector:pg16 et vérifie que l'application se connecte.

Interdits : code métier, migration Flyway, module Modulith, secret, URL externe.

Livrable : branche courante, ./mvnw verify vert, PR ouverte avec le template (gh pr create), titre « M0-01 Initialiser psymate-api ».
```

**Après fusion** : rien à taguer.

---

## Lot 1 — Modules vides et base de données

**Avant de lancer** : M0-01 fusionné.
**Agents** : 2 en parallèle (deux terminaux). Ils ne touchent pas aux mêmes fichiers.

### Agent 1 · tâche M0-02 — Modules Modulith vides et règles de dépendance

**En clair** : créer les onze dossiers de modules (`identity`, `patients`, `vocabulary`, `clinical`, `linking`, `search`, `overview`, `events`, `directory`, plus `ai` et `companion` vides), chacun avec son fichier `package-info.java` qui déclare de quels autres modules il a le droit de dépendre, et les tests d'architecture qui font échouer le build si quelqu'un franchit une frontière. Aucune classe métier.

```bash
claude --worktree M0-02
```

```text
Tâche M0-02 — Modules Modulith vides et règles de dépendance (dépôt psymate-api, tous les modules, package-info.java seulement). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A3 « Correspondance avec les modules Spring Boot », A5 « Modules et dépendances autorisées » (tableau complet, y compris la ligne directory et la ligne companion), A10.1 (identity.admin ne dépend d'aucun module clinique), A11 « Module, événements et endpoints réservés ». Le module scheduling (agenda) n'existe pas : G Q25.

Objectif : les packages com.psymate.identity, com.psymate.patients, com.psymate.vocabulary, com.psymate.clinical, com.psymate.linking, com.psymate.search, com.psymate.overview, com.psymate.events, com.psymate.directory, chacun avec package-info.java et @ApplicationModule(allowedDependencies = …) conformes au tableau A5 ; le package com.psymate.shared (types communs, déclaré comme module partagé ouvert) ; les packages réservés com.psymate.ai et com.psymate.companion avec leur package-info.java et aucune autre classe (companion : allowedDependencies = patients, clinical, events) ; les sous-packages com.psymate.identity.auth, com.psymate.identity.preferences, com.psymate.identity.verification, com.psymate.identity.admin, vides ; ModularityTests (ApplicationModules.of(App.class).verify()) ; ArchitectureTests (ArchUnit) : aucun accès à un repository d'un autre module, directory et identity.admin n'importent rien de patients, clinical, linking, overview, search.

Périmètre : src/main/java/com/psymate/**/package-info.java, src/test/java/com/psymate/ModularityTests.java, src/test/java/com/psymate/ArchitectureTests.java, dépendances de test ArchUnit dans pom.xml (annoncée dans la PR). Aucune entité, aucun service, aucune migration.

Critères d'acceptation (C2 M0-02) :
- une dépendance interdite ajoutée volontairement (par exemple directory → patients) fait échouer ./mvnw verify ; tu le démontres par un test qui attend l'échec de verify() sur un module de test, ou par une capture dans la PR après avoir essayé puis retiré la dépendance ;
- companion et ai existent sans aucune classe ;
- ModularityTests et ArchitectureTests sont verts.

Interdits : toute classe hors package-info.java et tests ; toute migration.

Livrable : PR « M0-02 Modules Modulith vides », ./mvnw verify vert.
```

### Agent 2 · tâche M0-03 — Migration de base `V01__init.sql`

**En clair** : écrire le premier fichier SQL qui crée la base : extensions, tenants, praticiens (avec toutes leurs colonnes, y compris celles de la vérification et des préférences), jetons, sessions, journal de sécurité, patients et leurs annexes, les référentiels vides (types d'entrées, émotions, types de liens), la table d'événements de Modulith, et les deux tables réservées au compagnon. Le SQL est déjà écrit dans E : l'agent le copie, ne l'invente pas. Il ajoute un test qui applique la migration sur une base vierge et compare les tables créées à la liste attendue.

```bash
claude --worktree M0-03
```

```text
Tâche M0-03 — Migration de base V01__init.sql (dépôt psymate-api, src/main/resources/db/migration et son test). Utilise l'agent backend-module-dev.

Contexte : docs/E-modele-donnees.md fait autorité (il remplace la description de C2 M0-03 et A4 pour la base). Lis E2 (conventions), E3.1, E3.2, E3.4 (sauf patient_tag qui est en V02), E3.5 (structures seules de entry_type, emotion, link_type : pas de seed), E3.8 (event_publication), E3.11 (patient_device, patient_submission SANS les deux ALTER TABLE de fin, qui viennent en V05), E4 (plan des migrations et vérifications CI), E7 O11 (REVOKE sur les tables append-only). Décision G Q9 : le rôle applicatif s'appelle psymate_app (pas clinic_app) ; il est créé par l'init du conteneur (M0-01), tu ne le crées pas.

Objectif : src/main/resources/db/migration/V01__init.sql contenant, copiés depuis E dans cet ordre : extensions + fonction f_unaccent + tenant (E3.1) ; practitioner, auth_token, totp_recovery_code, spring_session, spring_session_attributes, security_event (E3.2) ; patient, patient_version, patient_flag, patient_access (E3.4) ; entry_type, emotion, link_type (E3.5, CREATE TABLE seulement) ; event_publication (E3.8 : vérifie d'abord le schéma exact fourni par la version de spring-modulith-starter-jdbc du pom.xml et utilise celui-là ; indique dans la PR les différences avec E3.8) ; patient_device, patient_submission (E3.11, sans les ALTER). Puis : REVOKE UPDATE, DELETE ON security_event, patient_version FROM psymate_app (les autres tables append-only n'existent pas encore ; leur REVOKE viendra avec leur migration).

Tests : FlywayMigrationIT (Testcontainers pgvector/pgvector:pg16) : la migration s'applique sur une base vierge ; la liste des tables publiques est exactement celle attendue (fichier src/test/resources/expected-schema-v01.yaml que tu écris : table → colonnes) ; patient_device et patient_submission existent et sont vides ; les extensions pg_trgm, unaccent, vector, btree_gist, pgcrypto sont installées.

Critères d'acceptation (C2 M0-03, révisés par E4) :
- Flyway applique V01 sur un Postgres vierge ;
- toutes les tables listées pour V01 dans E4 existent avec leurs index ;
- patient_device et patient_submission existent et sont vides ;
- aucun écart avec le DDL de E hors ceux justifiés dans la PR (event_publication).

Interdits : toute classe Java hors le test ; tout INSERT de seed (c'est V02) ; les tables entry_*, link, practitioner_profile, verification_* (elles ont leur propre migration en E4) ; modification du DDL de E sans le signaler.

Livrable : PR « M0-03 Migration V01__init », ./mvnw verify vert.
```

**Après fusion des deux** : `make db-reset` en local pour vérifier que la base se crée.

---

## Lot 2 — Trois couloirs indépendants

**Avant de lancer** : M0-02 **et** M0-03 fusionnés.
**Agents** : 3 en parallèle (trois terminaux) : `identity`, `events`, `vocabulary`. Ils ne se croisent jamais.

### Agent 1 · tâche M0-04 — Identité minimale

**En clair** : la connexion de base. Un praticien s'inscrit (e-mail, mot de passe, profession), se connecte, se déconnecte, demande une réinitialisation de mot de passe par e-mail (visible dans Mailpit). Le compte reçoit un `tenant`. Sans session, l'API répond 401. C'est la version simple ; l'inscription complète avec confirmation d'e-mail, téléphone et adresse viendra en M1-10.

```bash
claude --worktree M0-04
```

```text
Tâche M0-04 — Identité : inscription, connexion, tenant, réinitialisation (dépôt psymate-api, module identity, sous-package identity.auth). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A2 UC-01, A4.7, ADR-09 (tenant), A5 (module identity : expose CurrentPractitioner et PractitionerFacade), docs/E-modele-donnees.md E3.1 et E3.2 (tables tenant, practitioner, auth_token, spring_session* : elles existent déjà, créées par V01 ; tu ne crées aucune migration), docs/G-decisions.md G2 (Q4 : expéditeur app.mail.from, test@gmail.com en local ; Q6 : session 12 h, 30 jours avec « rester connecté »), docs/design/11-connexion.md et docs/design/26-mot-de-passe-oublie.md (politique de mot de passe ≥ 12 caractères, Argon2id, jeton 30 min usage unique).

Objectif : Spring Security avec session côté serveur (Spring Session JDBC sur les tables spring_session existantes) et cookie HttpOnly Secure SameSite=Lax ; POST /api/auth/signup { email, password, firstName, lastName, profession } → crée tenant + practitioner (verification_status = 'email_pending', preferences par défaut selon profession : coach → {"vocabulary.subjectLabel":"client","entryTypes.medicationVisible":false}, psychiatrist → {"entryTypes.medicationVisible":true}, sinon {}), 201 ; POST /api/auth/login { email, password, rememberMe } → 200 + cookie, 401 avec message unique sinon ; POST /api/auth/logout ; POST /api/auth/password-reset/request { email } → toujours 202, e-mail via Mailpit si le compte existe, ligne auth_token kind='password_reset' ; POST /api/auth/password-reset/confirm { token, password } → 204 ; hachage Argon2id ; CurrentPractitioner (principal courant) et PractitionerFacade (id, tenantId, profession, verificationStatus, preferences) dans com.psymate.identity.api ; e-mail envoyé par Spring Mail vers Mailpit (host/port externalisés, expéditeur app.mail.from en configuration, nom d'affichage PsyMate).

Note : la confirmation d'e-mail, le téléphone, l'adresse et le 2FA sont M1-10 et M1-11, pas ici. Mais verification_status est posé à 'email_pending' dès maintenant.

Périmètre : src/main/java/com/psymate/identity/** (api, auth), src/main/resources/application.yml (clés mail et sécurité), tests src/test/java/com/psymate/identity/**, messages_fr/en.properties pour les e-mails (FR obligatoire, EN si possible : G Q23). Aucune migration.

Critères d'acceptation (C2 M0-04) :
- inscription puis connexion renvoient un cookie de session ; accès à une route protégée sans session → 401 ;
- e-mail de réinitialisation visible dans Mailpit, jeton haché en base, consommé une seule fois, expiré après 30 min (test avec horloge contrôlée) ;
- login échoué sur e-mail inconnu et sur mauvais mot de passe → même code 401, même corps ;
- le mot de passe « motdepasse » (11 caractères) est refusé 422.

Tests : AuthIT (Testcontainers + Mailpit en conteneur ou SMTP de test), test unitaire de la politique de mot de passe.

Interdits : migration ; dépendance vers un autre module ; 2FA ; confirmation d'e-mail.

Livrable : PR « M0-04 Identité minimale », ./mvnw verify vert.
```

### Agent 2 · tâche M4-01a — Events minimal (avancé par D)

**En clair** : le mécanisme qui enregistre chaque événement métier dans la table `event_publication` et le livre aux modules abonnés (recherche, vue d'ensemble, plus tard l'IA). Version minimale : publier et enregistrer. La republication après redémarrage et l'idempotence des abonnés viendront en M4-01. C'est avancé ici pour que le module clinique (M1-03) puisse émettre ses événements avec le vrai mécanisme au lieu d'en inventer un.

```bash
claude --worktree M4-01a
```

```text
Tâche M4-01a — Events minimal : publisher et registre Modulith (dépôt psymate-api, module events). Utilise l'agent backend-module-dev. Cette tâche a été créée par docs/D-orchestration-backend.md D6-A1 en scindant M4-01 ; elle n'a pas de ligne propre dans C2.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.9 (hooks V2 : outbox), A7 (les séquences montrent qui émet quoi), A5 (module events : expose DomainEventPublisher ; ne dépend de rien), docs/E-modele-donnees.md E3.8 (table event_publication déjà créée par V01, liste des événements de domaine V1), docs/D-orchestration-backend.md D6 ligne A1.

Objectif : dans com.psymate.events.api : une interface DomainEventPublisher { void publish(Object event); } et une classe de base DomainEvent (id uuid, occurredAt, tenantId) ; dans com.psymate.events (interne) : l'implémentation sur ApplicationEventPublisher de Spring, et la configuration Spring Modulith event publication registry en JDBC sur la table event_publication existante (spring.modulith.events.jdbc.schema-initialization.enabled=false, déjà posé). Un abonné de démonstration en package de test uniquement (pas en main) qui prouve qu'un événement publié est écrit dans event_publication puis marqué complété quand l'abonné a fini.

Ce que tu ne fais PAS (c'est M4-01) : republication des événements incomplets au redémarrage, idempotence, purge des événements complétés.

Périmètre : src/main/java/com/psymate/events/**, src/test/java/com/psymate/events/**. Aucune migration (si le schéma event_publication de V01 ne correspond pas à la version du starter du pom.xml, arrête-toi et dis-le dans ton rapport : ne modifie pas V01).

Critères d'acceptation (D6-A1) :
- un événement publié dans une transaction est présent dans event_publication après commit ;
- un abonné @ApplicationModuleListener de test le reçoit et la ligne passe à complétée ;
- ModularityTests reste vert (events ne dépend d'aucun module).

Tests : EventPublicationIT (Testcontainers).

Interdits : toucher aux autres modules ; migration ; code de republication.

Livrable : PR « M4-01a Events minimal », ./mvnw verify vert.
```

### Agent 3 · tâche M1-02 — Vocabulaires

**En clair** : remplir les référentiels : les 6 types d'entrées, les 29 émotions globales, les 8 types de liens avec leur matrice (quel type peut pointer vers quel type), et créer les tables de tags et de fusion d'émotions (migration `V02`). Puis les endpoints : lire les émotions (globales + celles créées par le praticien), créer une émotion personnalisée (famille obligatoire), gérer ses tags (créer, renommer, fusionner), avec la règle « un tag qui a le nom d'une émotion est refusé ».

```bash
claude --worktree M1-02
```

```text
Tâche M1-02 — Vocabulaires : émotions globales et custom, types d'entrées, types de liens, tags (dépôt psymate-api, module vocabulary). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.5 (émotions), A4.6 (vocabulaires et tags), UC-11 ; docs/E-modele-donnees.md E3.5 (tables tag, patient_tag, emotion_merge : à créer), E4 ligne V02, E5.1, E5.2, E5.3 (seed complet, à copier tel quel), E7 O1 ; docs/G-decisions.md G3 (Q8 : fusion d'émotions en V1, promotion en V2 ; Q11 : les 29 émotions de E5.2 sont livrées telles quelles, familles et libellés compris ; Q12 : matrice E5.3 conservée) ; docs/design/17-emotions-personnalisees.md (fusion réversible 30 jours) ; docs/design/14-preferences-praticien.md section « Émotions personnalisées ».

Objectif :
1. Migration src/main/resources/db/migration/V02__vocabulary_seed.sql : CREATE TABLE tag, patient_tag, emotion_merge (E3.5) puis les INSERT de E5.1, E5.2, E5.3 copiés sans modification (G Q11, Q12).
2. Module vocabulary : façades com.psymate.vocabulary.api EmotionCatalog (émotions globales + custom du praticien courant, actives), LinkTypeCatalog (types avec allowedSourceTypes / allowedTargetTypes), TagFacade (tags du praticien, résolution par libellé normalisé) ; endpoints GET /api/vocabulary/emotions, GET /api/vocabulary/link-types, GET /api/vocabulary/entry-types, GET/POST /api/tags, PATCH /api/tags/{id} (renommer), POST /api/tags/{id}/merge { targetId }, POST /api/me/emotions { labelFr, family } (code 'c_' + 8 hex, scope custom, practitioner_id = courant), PATCH /api/me/emotions/{code} (renommer), POST /api/me/emotions/{code}/merge { targetCode } (réécrit entry_feeling.emotion_code et renseigne original_emotion_code, ligne emotion_merge avec undo_until = now + 30 jours), POST /api/me/emotions/merges/{id}/undo.
3. Normalisation : label_normalized et normalized_label = lower(unaccent(trim(x))) sans '#', calculés en Java (même règle que la fonction f_unaccent côté SQL).
4. Règle : créer un tag dont le libellé normalisé égale une émotion globale ou custom du praticien → 422 avec code TAG_IS_EMOTION et l'émotion proposée.

Note : entry_feeling n'existe pas encore (M1-03). Le merge d'émotions réécrit entry_feeling via une requête que tu écris mais dont le test d'intégration ne vérifiera la réécriture qu'à partir de M1-03 : écris le test avec @Disabled("M1-03") et un commentaire, et teste dès maintenant la création de la ligne emotion_merge et l'undo.

Périmètre : src/main/java/com/psymate/vocabulary/**, V02__vocabulary_seed.sql, tests. Dépendances autorisées : identity (api) uniquement.

Critères d'acceptation (C2 M1-02 + E) :
- GET /api/vocabulary/emotions renvoie les 29 globales + les custom du praticien courant, jamais celles d'un autre praticien ;
- la base contient 6 entry_type, 29 emotion, 8 link_type après V02 ; chaque code de allowed_source_types / allowed_target_types existe dans entry_type (test SQL) ;
- un tag égal à une émotion (« anxiété », « Anxiete », « #anxiété ») est refusé 422 ;
- une émotion custom sans famille est refusée 422 ; deux custom de même libellé normalisé pour le même praticien → 409 ;
- fusionner deux tags réétiquette (test unitaire de la logique, intégration complète après M1-03).

Tests : VocabularyIT, test unitaire de normalisation (table de cas avec accents, majuscules, '#', espaces).

Interdits : toucher à entry_* (M1-03) ; modifier le seed de E sans le dire dans la PR ; dépendance hors identity.

Livrable : PR « M1-02 Vocabulaires », ./mvnw verify vert, contrat regénéré (make contract).
```

**Après fusion des trois** : rien à taguer encore (le contrat est tagué au lot 3 avec M0-05).

---

## Lot 3 — Contrat, patients, identité enrichie, profil public

**Avant de lancer** : M0-04 fusionné (et, pour lancer M1-03 au lot suivant : M1-02 et M4-01a aussi).
**Agents** : d'abord **M0-05 seul** (court, 20 minutes), fusion et tag, puis **3 en parallèle** : `patients`, `identity`, `directory`.

### Agent 1 · tâche M0-05 — Contrat OpenAPI et tag `contract-v0.1.0`

**En clair** : faire générer automatiquement le fichier `contract/openapi.yaml` par le build, et faire échouer la CI si le fichier commité ne correspond plus au code. Puis poser le premier tag. C'est le « message » que le frontend recevra.

```bash
claude --worktree M0-05
```

```text
Tâche M0-05 — Contrat OpenAPI généré, vérifié en CI, tag contract-v0.1.0 (dépôt psymate-api, racine). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/C-plan.md « Décisions d'outillage » (ligne Contrat d'API) et C3 « Conventions » (ligne Contrat), docs/D-orchestration-backend.md D6-A4 (plan des tags : v0.1.0 = auth minimale).

Objectif : la cible make contract démarre l'application sur le profil test (base Testcontainers ou H2 interdit : utilise Postgres via docker compose ou Testcontainers), télécharge /v3/api-docs.yaml de springdoc et l'écrit dans contract/openapi.yaml, trié de façon stable (clés ordonnées) pour que le diff soit lisible ; un test ou une étape CI « contract check » régénère le fichier et échoue si le résultat diffère du fichier commité ; un script tools/tag-contract.sh <version> qui vérifie que l'arbre est propre, crée le tag git contract-v<version> et l'affiche.

Périmètre : Makefile, tools/tag-contract.sh, .github/workflows/ci.yml, contract/openapi.yaml (généré), éventuellement un test ContractIT. Aucun code métier.

Critères d'acceptation (C2 M0-05) :
- contract/openapi.yaml est à jour dans la PR et contient les routes /api/auth/*, /api/vocabulary/*, /api/tags, /api/me/emotions ;
- la CI échoue si on modifie un contrôleur sans regénérer le fichier (démontré dans la PR : capture ou commit de test retiré) ;
- tools/tag-contract.sh 0.1.0 fonctionne (ne lance PAS le tag toi-même : c'est moi qui le fais après fusion).

Interdits : code métier ; dépendance non annoncée.

Livrable : PR « M0-05 Contrat OpenAPI », ./mvnw verify vert.
```

**Après fusion de M0-05** : `git checkout main && git pull && tools/tag-contract.sh 0.1.0 && git push --tags`. Puis ouvrez trois terminaux.

### Agent 2 · tâche M1-01 — Patients

**En clair** : créer, lire, modifier, archiver un patient. Un praticien ne voit jamais les patients d'un autre (403). Archiver masque sans supprimer. Chaque modification de la fiche crée une version. Les drapeaux (« à surveiller » avec raison, « risque », « suivi conjoint ») et les tags de patient sont gérés ici. La création d'un patient émet l'événement `PatientCreated` ; la séance d'accueil sera créée par le module clinique (M1-03) en réaction à cet événement.

```bash
claude --worktree M1-01
```

```text
Tâche M1-01 — Patients : CRUD, propriété, archivage, versions, drapeaux, tags (dépôt psymate-api, module patients). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A2 UC-02, ADR-09 (propriété, tenant), A5 (module patients : expose PatientFacade, PatientOwnershipGuard, PatientCreated ; dépend de identity et events) ; docs/E-modele-donnees.md E3.4 (patient, patient_version, patient_flag, patient_access : créées par V01) et E3.5 patient_tag (créée par V02), E7 O2, O3, O10 ; docs/G-decisions.md G3 (Q7 : tags, versions et drapeaux de patient retenus ; Q30 : clinical_rev incrémenté par l'application ; Q19 : aucune règle particulière pour les mineurs en V1) ; docs/design/16-fiche-patient.md (champs du formulaire, drapeaux, tags, versions) ; docs/design/01-liste-patients.md (ce que la liste affiche).

Objectif : entité Patient ; PatientFacade (exists, ownedBy, displayName, birthDate) et PatientOwnershipGuard (lève 403 si le patient n'appartient pas au praticien courant ou n'est pas dans son tenant) dans com.psymate.patients.api ; événements PatientCreated, PatientUpdated, PatientArchived publiés via DomainEventPublisher (module events) ; endpoints GET /api/patients?q&archived&tag&sort (liste du praticien courant, filtre nom tolérant aux accents via f_unaccent + trigram), POST /api/patients (corps de la fiche 16 : displayName obligatoire, firstName, lastName, birthDate, pronoun, phone, email, context{…}, followUp{…}, flags[], tags[]), GET /api/patients/{id}, PATCH /api/patients/{id} (chaque PATCH crée une ligne patient_version avec snapshot des colonnes éditables et incrémente current_version), GET /api/patients/{id}/versions, DELETE /api/patients/{id} (= archived_at, jamais de suppression), POST /api/patients/{id}/restore, POST/DELETE /api/patients/{id}/flags (watch exige reason), PUT /api/patients/{id}/tags (liste de libellés, résolus via TagFacade du module vocabulary ; un libellé égal à une émotion → 422).

Ne crée PAS la séance d'accueil : c'est le module clinical (M1-03) qui écoute PatientCreated.

Périmètre : src/main/java/com/psymate/patients/**, tests. Dépendances : identity.api, events.api, vocabulary.api (TagFacade). Aucune migration.

Critères d'acceptation (C2 M1-01 + fiche 16) :
- un praticien ne lit, ne modifie ni n'archive jamais le patient d'un autre (403, pas 404) ;
- archiver masque de la liste par défaut, GET …?archived=true le montre, les données sont intactes ;
- trois PATCH → GET /versions renvoie 3 versions avec leur snapshot ;
- flag watch sans reason → 422 ; deux flags actifs de même kind → 409 ;
- tag « anxiété » sur un patient → 422 TAG_IS_EMOTION ;
- PatientCreated est écrit dans event_publication à la création.

Tests : PatientIT (propriété, archivage, versions, flags, tags), test unitaire du filtre de nom.

Interdits : migration ; toucher à clinical ; créer une entrée.

Livrable : PR « M1-01 Patients », ./mvnw verify vert, contrat regénéré.
```

### Agent 3 · tâche M1-10 — Inscription enrichie et confirmation d'e-mail

**En clair** : remplacer l'inscription simple de M0-04 par l'inscription complète de la fiche 12 : profession, numéro professionnel, téléphone, adresse du cabinet ou « visio uniquement », consentements. L'inscription est ouverte à tous (G Q21). Après l'inscription, un e-mail de confirmation (lien valable 24 h) ; tant qu'il n'est pas cliqué, l'API refuse tout sauf les routes d'authentification. Crée aussi les tables du dossier professionnel et des décisions administratives (migration `V03`), qui seront utilisées par M1-13 et M1-14.

```bash
claude --worktree M1-10
```

```text
Tâche M1-10 — Inscription enrichie, confirmation d'e-mail, migration V03 (dépôt psymate-api, module identity, sous-package identity.auth + migration). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10 (hypothèses H12-H13, F28, F29, A10.2, A10.3 machine d'états, A10.4 endpoints), docs/C-plan.md C6 ligne M1-10 ; docs/E-modele-donnees.md E3.2 (practitioner a DÉJÀ toutes ses colonnes depuis V01 : tu ne fais aucun ALTER TABLE), E3.3 (verification_document, verification_decision, document_access_log, practitioner_appeal : À CRÉER dans V03__verification.sql, DDL à copier tel quel), E4 ligne V03, E8 (lignes « C M1-10 » et « D5 ») ; docs/G-decisions.md G2 (Q21 : inscription ouverte, aucun code d'invitation, la règle A10 R11 « bêta sur invitation » ne s'applique pas ; Q9 : rôle psymate_app) ; docs/design/12-inscription.md et docs/design/25-confirmation-email.md (routes, jetons, limites, états).

Objectif :
1. Migration V03__verification.sql = E3.3 copié, plus REVOKE UPDATE, DELETE ON verification_decision, document_access_log FROM psymate_app.
2. POST /api/auth/register remplace /signup (garde /signup en alias déprécié jusqu'au tag v0.2.0) : { email, password, firstName, lastName, profession, professionalNumber?, phone, address{street, postalCode, city} OU remoteOnly=true, consents{terms, privacy} } → 201 identique que l'e-mail existe déjà ou non (anti-énumération ; si le compte existe, aucun compte créé, e-mail d'information envoyé) ; mot de passe ≥ 12 caractères + refus des mots de passe compromis (liste locale de 10 000 mots de passe courants dans src/main/resources, décision : pas d'appel externe HIBP en V1) ; verification_status = 'email_pending' ; preferences par défaut selon profession.
3. Confirmation : auth_token kind 'email_confirm', 24 h, usage unique ; POST /api/auth/email/confirm { token } → 204 + session ouverte, verification_status passe à 'docs_pending', email_confirmed_at posé, security_event 'email_confirmed' ; POST /api/auth/email/resend { email } → 202 toujours, limite 1/min/compte et 5/jour, chaque renvoi invalide le précédent ; POST /api/auth/email/change { newEmail } (session email_pending seulement) ; GET /api/auth/session → état courant.
4. Filtre : un compte email_pending reçoit 403 { "code": "EMAIL_NOT_CONFIRMED" } sur tout /api/** sauf /api/auth/**.
5. Événement PractitionerRegistered publié.

Périmètre : src/main/java/com/psymate/identity/** (auth, api), V03__verification.sql, tests, messages des e-mails FR (EN si possible).

Critères d'acceptation (C6 M1-10, fiches 12 et 25) :
- register sur e-mail nouveau et sur e-mail existant → même 201, même corps, temps de réponse comparable (test de timing ± 50 ms) ;
- adresse absente et remoteOnly=false → 422 ; profession hors liste → 422 ; consentements manquants → 422 ;
- jeton de confirmation : valide une fois ; deuxième usage → 410 ; après 24 h → 410 ;
- avant confirmation, GET /api/patients → 403 EMAIL_NOT_CONFIRMED ; après → 200 ;
- coach → preferences contient vocabulary.subjectLabel = client et entryTypes.medicationVisible = false ;
- V03 crée exactement les quatre tables de E3.3 (test de schéma).

Tests : RegistrationIT, EmailConfirmationIT, test de timing, test de schéma V03.

Interdits : 2FA, verrouillage (M1-11) ; préférences endpoints (M1-12) ; upload de documents (M1-13) ; ALTER TABLE practitioner ; tout mécanisme d'invitation.

Livrable : PR « M1-10 Inscription enrichie et confirmation », ./mvnw verify vert, contrat regénéré.
```

### Agent 4 · tâche M5-01 — Profil public (annuaire) : modèle et édition

**En clair** : la fiche publique du praticien côté édition : il remplit nom affiché, titre, photo, présentation, spécialités, publics, modalités, langues, ville, contacts (chacun avec un interrupteur « afficher »), disponibilité ; il publie ou dépublie. Les spécialités et publics viennent d'une liste fermée (G Q15). La publication est refusée s'il manque le nom, la profession, la ville ou un contact affiché. La condition « dossier approuvé par l'admin » sera ajoutée en M5-06. Crée la table `practitioner_profile` (migration `V04`).

```bash
claude --worktree M5-01
```

```text
Tâche M5-01 — Profil public : modèle et édition (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.10, UC-13, A3 « Contexte Directory », A5 (directory dépend de identity et events seulement, expose ProfileFacade, DirectoryFacade, ProfilePublished, ProfileUnpublished) ; docs/E-modele-donnees.md E3.10 (practitioner_profile + fonction et trigger search_vector : À CRÉER dans V04__directory_profile.sql ; contact_request est V06, pas ici), E4 ligne V04 ; docs/G-decisions.md G3 (Q15 : spécialités et publics = LISTE FERMÉE servie par l'API, pas de saisie libre, modifiable plus tard par l'admin ; cela remplace A4.10 « vocabulaire global + libre ») ; docs/design/10-mon-profil-public.md (règle de publication, 409 avec liste des manques, enregistrement automatique, aperçu).

Objectif :
1. Migration V04__directory_profile.sql = E3.10 partie practitioner_profile (table, index, fonction trg_profile_search_vector, trigger), plus une petite table directory_vocabulary (kind ∈ specialty|audience, code, label_fr, label_en, sort_order, active) avec un seed initial de spécialités et de publics raisonnable (une vingtaine de spécialités courantes : anxiété, dépression, TCC, EMDR, thérapie de couple, burn-out, TDAH, addictions… ; publics : adults, young_adults, adolescents, children, couples_families). Documente le seed dans la PR ; il sera relu.
2. GET /api/me/profile (créé vide au premier appel avec display_name = practitioner.display_name et slug généré : prénom-nom normalisé + suffixe si collision), PUT /api/me/profile (corps = colonnes éditables de E3.10, validation : specialties et audiences ⊆ codes actifs de directory_vocabulary (422 sinon), languages ISO 639-1, modalities ∈ in_person|video|home, availability ∈ accepting|waitlist|closed|unknown, website_url http(s)), POST /api/me/profile/photo (multipart ≤ 5 Mo, JPEG/PNG vérifiés par signature, redimensionnée 512 px, EXIF retiré, stockée sous un storage key local en dev), DELETE /api/me/profile/photo, POST /api/me/profile/publish → 204 ou 409 { "missing": ["displayName"|"profession"|"city"|"contact"] }, POST /api/me/profile/unpublish → 204 ; GET /api/vocabulary/directory (listes fermées de spécialités, publics, modalités, langues, lues depuis directory_vocabulary pour les deux premières). L'endpoint admin de modification de cette liste est M1-14b, pas ici.
3. ProfileFacade dans com.psymate.directory.api ; événements ProfilePublished / ProfileUnpublished.

La condition « verification_status = approved » pour publier est M5-06 : ne l'implémente pas ici, mais laisse un point d'extension (méthode canPublish) et un commentaire « M5-06 ».

Périmètre : src/main/java/com/psymate/directory/**, V04__directory_profile.sql, tests. Dépendances : identity.api, events.api. Jamais patients, clinical, linking.

Critères d'acceptation (C2 M5-01 + fiche 10) :
- publication refusée sans contact affiché → 409 missing ["contact"] ; sans ville → missing ["city"] ;
- une spécialité hors liste → 422 ;
- retrait immédiat : après unpublish, published = false et published_at inchangé ;
- la photo téléversée ne contient plus de métadonnées EXIF (test : image avec EXIF en entrée, lecture en sortie) ;
- slug unique : deux praticiens « Claire Lambert » → claire-lambert et claire-lambert-2 ;
- ArchitectureTests : directory n'importe rien de patients/clinical/linking (déjà en place, doit rester vert).

Tests : ProfileIT, test unitaire du slug et de la validation.

Interdits : routes publiques /api/public/** (M5-02) ; contact_request (M5-03) ; dépendance clinique ; saisie libre de spécialité.

Livrable : PR « M5-01 Profil public », ./mvnw verify vert, contrat regénéré.
```

**Après fusion des trois** : pas de tag (v0.2.0 arrive après M1-12 et M1-13).

---

## Lot 4 — Le cœur clinique démarre ; l'identité se découpe en trois

**Avant de lancer** : M1-01, M1-02, M4-01a fusionnés (pour M1-03) ; M1-10 fusionné (pour M1-11, M1-12, M1-13) ; M5-01 fusionné (pour M5-02).
**Agents** : jusqu'à 5 en parallèle. Si vous préférez en lancer moins, l'ordre de priorité est M1-03 d'abord (chemin critique), puis M1-13, M1-12, M5-02, M1-11.

### Agent 1 · tâche M1-03 — Entrées : modèle hybride, versions, séance obligatoire

**En clair** : le cœur du produit. Une « entrée » est tout ce que le praticien note sur un patient : note, événement, situation, ressenti, séance, médicament. Chaque entrée a une date d'occurrence avec une précision (exacte, jour, mois, année, période, phase de vie), appartient obligatoirement à une séance, et toute correction crée une nouvelle version (sauf dans les 5 minutes qui suivent la création, pour les coquilles). La séance d'accueil est créée automatiquement quand un patient est créé. Crée les tables `entry`, `entry_version` et les trois tables de détail (migration `V05`). Pas encore d'endpoint REST (c'est M1-05) : ici le domaine, les services et les événements.

```bash
claude --worktree M1-03
```

```text
Tâche M1-03 — Entrées : modèle hybride, versions, règle de grâce, séance obligatoire, migration V05 (dépôt psymate-api, module clinical). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A1 « Langage ubiquitaire » et « Chevauchements », A4.1 (hybride), A4.2 (temps, six précisions), A4.5 (entry_feeling, copie de valence/arousal), A4.8 (versions, règle de grâce), A7 séquence 1 et « Cycle de vie d'une entrée », ADR-10, A11 « Règles attachées à ces colonnes », UC-03, UC-05 ; docs/E-modele-donnees.md E3.6 (entry, entry_version, FK current_version_id, entry_feeling, entry_medication, entry_session, entry_tag : À CRÉER dans V05__clinical.sql, DDL à copier), E3.11 (les deux ALTER TABLE de fin : fk_entry_submission et fk_submission_session, à mettre dans V05), E3.12 (entry_embedding, entry_chunk : à créer dans V05, vides, SANS ai_feedback qui est V07), E4 ligne V05, E7 O10 (patient.clinical_rev incrémenté par le service clinical à chaque écriture : G Q30) ; docs/C-plan.md C2 ligne M1-03.

Objectif :
1. Migration V05__clinical.sql = E3.6 + les deux ALTER de E3.11 + entry_embedding et entry_chunk de E3.12 + REVOKE UPDATE, DELETE ON entry_version FROM psymate_app.
2. Domaine com.psymate.clinical.domain : Entry (racine), EntryVersion, TimeSpan (from, to, precision, label ; six précisions avec les règles de A4.2 : day → 00:00–23:59:59, month, year, period avec libellé, life_phase calculé depuis patient.birth_date), FeelingDetail, MedicationDetail, SessionDetail, Visibility, EntryStatus.
3. Application com.psymate.clinical.application : EntryService.create(patientId, CreateEntryCommand) → vérifie la propriété via PatientOwnershipGuard, exige sessionId sauf type session, vérifie que la séance est une entrée de type session du même patient, écrit entry + entry_version 1 + table de détail selon le type (feeling : copie valence/arousal depuis EmotionCatalog si non null), author_type = 'practitioner', submission_id = NULL, incrémente patient.clinical_rev, publie EntryCreated ; EntryService.correct(entryId, CorrectEntryCommand) → nouvelle version sauf règle de grâce (même auteur, < versioning.grace-period depuis created_at, aucun lien (link_count = 0), aucune autre version) → modification en place sans version et sans événement ; sinon version n+1, mise à jour du détail et de payload, EntryCorrected ; EntryService.archive → archived_at, EntryArchived ; EntryFacade dans com.psymate.clinical.api (exists, belongsToPatient, typeOf, sameOwnedPatient(a, b), currentVersionOf).
4. Abonné : @ApplicationModuleListener sur PatientCreated → crée la séance d'accueil (entry type session, entry_session.kind = 'intake', occurred_from = création, closed_at = NULL).
5. Chaque type d'entrée a un validateur Jakarta dédié (champs minimaux : note → body ; event → title ; situation → title + occurred_from ; feeling → emotionCode + intensity ; session → modality ; medication → molecule).

Pas d'endpoint REST ici (M1-05), pas de séance courante / clôture (M1-04).

Périmètre : src/main/java/com/psymate/clinical/** (domain, application, persistence, api), V05__clinical.sql, tests. Dépendances : patients.api, vocabulary.api, events.api, identity.api.

Critères d'acceptation (C2 M1-03) :
- les six types se créent et se corrigent via EntryService ; chaque type a sa ligne de détail quand il en a une (test « chaque type a sa table et son validateur ») ;
- une entrée sans sessionId (hors type session) est refusée ; une sessionId pointant vers une note est refusée ;
- une correction après 5 minutes crée une version ; avant 5 minutes, par le même auteur, sans lien ni version, ne crée rien (test avec horloge contrôlée : Clock injecté) ; (prompt d'époque : la fenêtre est devenue 1 h 15 avec G Q31)
- valence/arousal copiés si l'émotion les a, NULL sinon ;
- toute entrée créée porte author_type = 'practitioner' et submission_id IS NULL ;
- les six précisions temporelles donnent les bonnes bornes (table de cas) ;
- la création d'un patient produit une séance d'accueil ;
- V05 crée exactement les tables de E4 ligne V05 (test de schéma) ; entry_embedding et entry_chunk sont vides.

Tests : EntryServiceIT par type, GraceRuleTest, TimeSpanTest (table), IntakeSessionIT, schéma V05.

Interdits : contrôleur REST ; liens (M2-01) ; toucher à patients, vocabulary, events ; renommer ou retirer une colonne de E.

Livrable : PR « M1-03 Entrées », ./mvnw verify vert.
```

### Agent 2 · tâche M1-11 — Connexion durcie, verrouillage, 2FA TOTP

**En clair** : la connexion de la fiche 11 : 5 échecs → compte verrouillé 15 minutes ; message d'erreur unique ; double authentification par code à 6 chiffres (application type Google Authenticator) activable par le praticien, avec 10 codes de secours ; journal de sécurité ; réponses spécifiques pour un compte non confirmé ou suspendu.

```bash
claude --worktree M1-11
```

```text
Tâche M1-11 — Connexion durcie, verrouillage, 2FA TOTP (dépôt psymate-api, module identity, sous-package identity.auth). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10 (F33, UC-19, A10.4 endpoints login 202/200 et /api/me/2fa/*), A10.3 (tableau États → Connexion : email_pending → 403 email_unconfirmed, rejected → 403 suspended) ; docs/E-modele-donnees.md E3.2 (colonnes totp_secret_enc, totp_enabled_at, failed_logins, locked_until, tables totp_recovery_code et security_event : existent depuis V01) ; docs/design/11-connexion.md ; docs/C-plan.md C6 ligne M1-11 ; docs/G-decisions.md G2 (Q6 : 12 h / 30 jours ; Q24 : Bucket4j pour les limites de taux).

Objectif : POST /api/auth/login { email, password, otp?, rememberMe } → 200 { session } ; 202 { "otpRequired": true } si 2FA active et otp absent ; 401 message unique « identifiants incorrects » (même corps pour e-mail inconnu, mauvais mot de passe, otp faux) ; 423 + en-tête Retry-After après 5 échecs sur la clé (email_normalized + IP) pendant 15 min ; 403 { "code": "EMAIL_NOT_CONFIRMED" } si email_pending ; 403 { "code": "ACCOUNT_SUSPENDED" } si rejected / suspended / closed, sessions révoquées. 2FA : POST /api/me/2fa/enable → secret TOTP (RFC 6238, 30 s, 6 chiffres) chiffré en totp_secret_enc (AES-GCM, clé app.crypto.key externalisée) + URI otpauth:// pour le QR (issuer PsyMate) ; POST /api/me/2fa/verify { code } → active (totp_enabled_at), génère 10 codes de secours hachés (totp_recovery_code), les renvoie une seule fois ; POST /api/me/2fa/disable { password, code } ; GET /api/me/2fa/recovery-codes/regenerate ; un code de secours accepté à la place de l'otp est marqué used_at. security_event : login_ok, login_failed, locked, 2fa_enabled, 2fa_disabled, recovery_code_used, session_revoked. POST /api/auth/sessions/revoke-others. Règle : role = admin sans 2FA active → 403 sur /api/admin/** (le filtre existe ici ; les routes admin viennent en M1-14).

Périmètre : src/main/java/com/psymate/identity/auth/**, tests. Aucune migration. Ne touche pas à identity/preferences ni identity/verification (d'autres agents y travaillent en parallèle). Si tu dois ajouter une méthode à PractitionerFacade (identity/api), ajoute-la en fin d'interface et signale-le en tête de PR.

Critères d'acceptation (C6 M1-11, fiche 11) :
- 5 échecs → 423 avec Retry-After ≈ 900 ; la 6e tentative avec le bon mot de passe échoue aussi pendant le verrou ;
- e-mail inconnu vs mauvais mot de passe → même 401, même corps, temps comparable (± 50 ms) ;
- 2FA : enable puis verify avec un code calculé dans le test → login renvoie 202 puis 200 avec otp ; code de secours utilisable une fois ;
- compte email_pending → 403 EMAIL_NOT_CONFIRMED ; compte suspended → 403 ACCOUNT_SUSPENDED et ses sessions existantes sont invalides ;
- chaque cas écrit la ligne security_event attendue.

Tests : LoginHardeningIT, TotpIT, test de timing.

Interdits : migration ; préférences ; documents ; admin.

Livrable : PR « M1-11 Connexion durcie et 2FA », ./mvnw verify vert, contrat regénéré.
```

### Agent 3 · tâche M1-12 — Préférences par compte

**En clair** : chaque réglage visible dans l'interface (vue simple ou complète, fenêtre 30 j / 90 j / 1 an, thème, langue, dire « patient » ou « client », afficher le type médicament…) est enregistré sur le compte du praticien et le suit sur tous ses appareils. Lecture et mise à jour partielle (on envoie une clé, les autres sont conservées).

```bash
claude --worktree M1-12
```

```text
Tâche M1-12 — Préférences par compte (dépôt psymate-api, module identity, sous-package identity.preferences). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10 (H12, F32, UC-18, ADR-11, A10.2 règles de merge), docs/E-modele-donnees.md E3.2 (colonne practitioner.preferences jsonb, existe depuis V01), E5.4 (défauts par profession, posés à l'inscription) ; docs/design/14-preferences-praticien.md (les 9 clés V1 et leurs valeurs) ; docs/C-plan.md C6 ligne M1-12 ; docs/G-decisions.md G3 (sessions.defaultDurationMin vaut 60 par défaut) et G4 (Q23 : locale fr par défaut).

Objectif : GET /api/me/preferences → objet plat des 9 clés (valeurs stockées, complétées par les défauts calculés pour les clés absentes) ; PATCH /api/me/preferences (merge RFC 7396 : une clé à null est supprimée et revient au défaut ; clés inconnues ignorées sans erreur ; valeurs hors domaine → 422 avec la clé fautive) ; validation des domaines de la fiche 14 (overview.view ∈ simple|complete, overview.window ∈ 30d|90d|12m, timeline.groupBy ∈ session|month|chronological, theme ∈ light|soft|dark|system, locale ∈ fr|en (défaut fr), vocabulary.subjectLabel ∈ patient|client, entryTypes.medicationVisible bool, entries.defaultVisibility ∈ clinical|private, sessions.defaultDurationMin entier 15–180, défaut 60) ; événement PreferencesChanged (practitionerId, clés modifiées) ; PractitionerFacade.preferencesOf(id) exposé.

Périmètre : src/main/java/com/psymate/identity/preferences/**, tests. Aucune migration. Ne touche pas à identity/auth ni identity/verification. Ajout de méthode à PractitionerFacade : en fin d'interface, signalé en tête de PR.

Critères d'acceptation (C6 M1-12) :
- PATCH { "theme": "dark" } conserve les autres clés ;
- PATCH { "foo": 1 } → 200, rien n'est stocké ;
- PATCH { "overview.window": "7d" } → 422 ;
- un coach fraîchement inscrit lit vocabulary.subjectLabel = client et entryTypes.medicationVisible = false ; tout compte neuf lit sessions.defaultDurationMin = 60 et locale = fr ;
- PreferencesChanged est publié avec la liste des clés changées.

Tests : PreferencesIT, test unitaire du merge (table de cas).

Interdits : migration ; auth ; documents.

Livrable : PR « M1-12 Préférences », ./mvnw verify vert, contrat regénéré.
```

### Agent 4 · tâche M1-13 — Dossier professionnel (API)

**En clair** : le praticien dépose ses justificatifs (diplôme, attestation ADELI/RPPS, certification, carte professionnelle, pièce d'identité), un ou plusieurs fichiers PDF/JPG/PNG de 10 Mo max, stockés chiffrés hors base, et soumet son dossier à vérification. Pour toutes les professions, le diplôme et la pièce d'identité sont obligatoires (G Q14). Les fichiers sont servis par lien temporaire (5 minutes) et purgés 90 jours après la décision.

```bash
claude --worktree M1-13
```

```text
Tâche M1-13 — Dossier professionnel : documents, stockage chiffré, soumission, purge (dépôt psymate-api, module identity, sous-package identity.verification). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10 (F29, UC-16, A10.2 règles sur les fichiers, A10.3 états docs_pending → under_review, needs_more → under_review, A10.4 endpoints /api/me/verification/*), R12 ; docs/E-modele-donnees.md E3.3 (tables créées par V03 : verification_document avec purpose, sha256, purged_at, deleted_at ; verification_decision ; document_access_log), E7 O9 ; docs/G-decisions.md G3 (Q14 : toutes les professions exigent diploma ET identity ; registration (ADELI/RPPS) recommandé pour psychologist et psychiatrist, jamais bloquant) et G4 (Q17 : stockage disque local chiffré en dev, le stockage de bêta sera décidé avec l'étude hébergement) ; docs/design/13-dossier-professionnel.md (progression, règles de complétude, états et verrous) ; docs/C-plan.md C6 ligne M1-13. Mets les règles de complétude dans une classe CompletenessRules facilement modifiable et cite G Q14 en commentaire.

Objectif : GET /api/me/verification → { status, documents[], missing[], recommended[], submittedAt, decisions[] (messages reçus seulement, jamais internal_note) } ; POST /api/me/verification/documents (multipart : type, title, issuer, year, file) → contrôle de signature de fichier (PDF %PDF, JPEG FFD8, PNG 89504E47), taille ≤ 10 Mo, EXIF retiré des images, sha256, chiffrement AES-GCM (clé app.crypto.key) vers un StorageService (interface ; implémentation disque local sous app.storage.dir en dev), ligne verification_document ; DELETE /api/me/verification/documents/{id} (soft delete, seulement si status ∈ docs_pending|needs_more) ; GET /api/me/verification/documents/{id}/url → URL signée 5 min servant le fichier déchiffré au praticien propriétaire seulement (403 sinon) ; POST /api/me/verification/submit → 409 { missing } si incomplet, sinon status = under_review, événement VerificationSubmitted, e-mail aux comptes role = admin ; PATCH /api/me/professional-info { professionalNumber, phone, address, remoteOnly } ; tâche planifiée @Scheduled quotidienne : documents dont purge_after < now et purged_at IS NULL → fichier supprimé, purged_at posé (purge_after est posé par M1-14 au moment de la décision ; ici tu poses la tâche et son test).

Périmètre : src/main/java/com/psymate/identity/verification/**, tests, application.yml (app.storage.dir, app.crypto.key pour le profil test). Aucune migration. Ne touche pas à identity/auth ni identity/preferences.

Critères d'acceptation (C6 M1-13, fiche 13) :
- un praticien ne lit jamais le document d'un autre (403 sur /url et sur GET) ;
- fichier .exe renommé .pdf → 415 ; 11 Mo → 413 ;
- le fichier sur disque est illisible sans la clé (test : contenu ≠ original) et redevient identique via /url ;
- submit sans diplôme ou sans pièce d'identité → 409 avec la liste des types manquants, quelle que soit la profession ; complet → under_review et e-mail dans Mailpit ;
- la purge supprime le fichier et garde la ligne (test avec purge_after dans le passé).

Tests : VerificationDocumentIT, CompletenessRulesTest (table par profession), PurgeJobIT.

Interdits : migration ; routes /api/admin/** (M1-14) ; auth ; préférences.

Livrable : PR « M1-13 Dossier professionnel », ./mvnw verify vert, contrat regénéré.
```

### Agent 5 · tâche M5-02 — Annuaire public : recherche et fiche

**En clair** : les pages que les visiteurs sans compte consultent : la liste des praticiens publiés avec recherche par mot-clé et ville, filtres (profession, modalité, public, langue, « accepte de nouveaux patients »), tri par pertinence, distance ou nom, 20 par page ; et la fiche détaillée par `slug`, qui ne montre que les contacts que le praticien a cochés. Ces routes sont hors authentification et limitées en débit.

```bash
claude --worktree M5-02
```

```text
Tâche M5-02 — Annuaire public : recherche et fiche (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A2 UC-14, UC-15 (lecture seule), A4.10 (règles d'affichage des contacts), A5 « Endpoints de l'annuaire » et « Module ajouté : directory » (exclusion de l'authentification, limitation de débit par adresse) ; docs/E-modele-donnees.md E3.10 (practitioner_profile existe depuis V04 ; search_vector maintenu par trigger ; geo point) ; docs/design/09-fiche-publique-praticien.md (route /api/public/directory/practitioners/{slug}, 404 si non publié, badge « vérifié ») ; docs/C-plan.md C2 ligne M5-02 ; docs/G-decisions.md G2 (Q24 : Bucket4j) et G4 (Q20 : aucun numéro d'urgence à exposer).

Objectif : GET /api/public/directory?q&city&professions&modalities&audiences&languages&accepting&sort&page → 20 par page, published = true seulement, recherche q via search_vector (plainto_tsquery french + simple) avec repli trigram sur display_name, ville par égalité normalisée ou rayon si lat/lng fournis (geo), facettes (compte par profession, modalité, public, langue) dans la réponse, tri relevance|distance|name ; GET /api/public/directory/practitioners/{slug} → fiche complète filtrée : contact_phone seulement si show_phone, idem e-mail et adresse, professional_number seulement si show_professional_number, verified = (practitioner.verification_status = 'approved') lu via PractitionerFacade ; 404 si non publié. Sécurité : /api/public/** exclu de l'authentification ; limitation de débit par IP (60 requêtes / minute) avec Bucket4j (dépendance à annoncer dans la PR, version figée) ; en-têtes de cache public 60 s. DirectoryFacade dans com.psymate.directory.api.

Périmètre : src/main/java/com/psymate/directory/**, configuration Spring Security (ajout de la règle permitAll sur /api/public/** uniquement), tests. Aucune migration.

Critères d'acceptation (C2 M5-02 + fiche 09) :
- sans session, les deux routes répondent 200 ; un profil published = false → absent de la liste et 404 sur la fiche ;
- show_phone = false → le téléphone n'apparaît nulle part dans la réponse (test sur le JSON brut) ;
- q = « anxiete » trouve une bio contenant « anxiété » ;
- 61e requête dans la minute depuis la même IP → 429 ;
- ArchitectureTests : aucune donnée patient accessible depuis directory (règle existante, reste verte) ; aucun import de patients, clinical, linking.

Tests : PublicDirectoryIT, test du filtrage des contacts, test de débit.

Interdits : formulaire de contact (M5-03) ; écriture ; dépendance clinique.

Livrable : PR « M5-02 Annuaire public », ./mvnw verify vert, contrat regénéré.
```

**Après fusion de M1-12 et M1-13** : `tools/tag-contract.sh 0.2.0 && git push --tags` (identité étendue : c'est ce tag que le frontend attend pour les écrans connexion, inscription, dossier, préférences).

---

## Lot 5 — Séances, outbox complète, administration, contact

**Avant de lancer** : M1-03 fusionné (pour M1-04 et M4-01) ; M1-13 fusionné (pour M1-14) ; M5-02 fusionné (pour M5-03).
**Agents** : 4 en parallèle.

### Agent 1 · tâche M1-04 — Séances : démarrer, clôturer, compléter

**En clair** : la séance courante (celle en cours, sinon la dernière) est présélectionnée quand on saisit une entrée ; on démarre une séance, on la clôture avec durée et compte rendu ; on peut ajouter des entrées à une séance passée, chacune gardant sa date de saisie ; une séance non clôturée est signalée.

```bash
claude --worktree M1-04
```

```text
Tâche M1-04 — Séances : démarrer, clôturer, compléter, séance courante (dépôt psymate-api, module clinical). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A2 UC-04, F11, ADR-10, H2 ; docs/E-modele-donnees.md E3.6 (entry_session : kind intake|session|contact, modality in_person|video|phone|email, duration_min, closed_at, summary_version_id) ; docs/C-plan.md C2 ligne M1-04 ; docs/B-ux.md B2 « En-tête patient permanent » (ce que l'écran affiche : séance en cours depuis n min, dernière séance). La durée proposée par défaut vient de la préférence sessions.defaultDurationMin (60 par défaut, G).

Objectif : SessionService dans clinical.application : start(patientId, modality, occurredFrom?) → crée une entrée type session ouverte (closed_at NULL) ; une seule séance ouverte par patient à la fois (409 sinon) ; close(sessionId, durationMin, summaryBody) → closed_at, duration_min (si absent : préférence sessions.defaultDurationMin du praticien), nouvelle version de l'entrée session avec body = compte rendu, summary_version_id ; current(patientId) → la séance ouverte, sinon la plus récente clôturée, sinon la séance d'accueil ; contact(patientId, modality phone|email, occurredFrom, body) → séance kind = contact, immédiatement clôturée (durée facultative) ; EntryService.create utilise current(patientId) quand sessionId est absent de la commande ; ajout d'entrées à une séance clôturée autorisé (recorded_at = maintenant, occurred_from libre) ; EntryFacade gagne currentSession(patientId) et openSessionOf(patientId).

Pas d'endpoint REST (M1-05).

Périmètre : src/main/java/com/psymate/clinical/**, tests. Aucune migration.

Critères d'acceptation (C2 M1-04) :
- une entrée créée pendant une séance ouverte reçoit son session_id sans le préciser ;
- une séance clôturée accepte des ajouts ; leur recorded_at est postérieur à closed_at ;
- deux start sans close → 409 ;
- current() renvoie dans l'ordre : ouverte, dernière clôturée, accueil (trois tests) ;
- close crée une version de l'entrée session portant le compte rendu ; close sans durationMin prend la préférence du praticien.

Tests : SessionServiceIT.

Interdits : REST ; migration ; autres modules.

Livrable : PR « M1-04 Séances », ./mvnw verify vert.
```

### Agent 2 · tâche M4-01 — Outbox complète

**En clair** : compléter le mécanisme d'événements de M4-01a : si l'application redémarre pendant qu'un événement n'a pas été traité, il est rejoué ; un abonné qui reçoit deux fois le même événement ne fait pas deux fois l'effet ; les événements traités sont purgés après un délai.

```bash
claude --worktree M4-01
```

```text
Tâche M4-01 — Outbox complète : republication, idempotence, purge (dépôt psymate-api, module events). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.9, A7, A9 R5 et R10 ; docs/E-modele-donnees.md E3.8 ; docs/C-plan.md C2 ligne M4-01 ; docs/D-orchestration-backend.md D6-A1 (M4-01a a déjà créé DomainEventPublisher et le registre ; tu complètes).

Objectif : republication des publications incomplètes au démarrage (spring.modulith.events.republish-outstanding-events-on-restart=true) et par tâche planifiée toutes les 5 minutes pour celles de plus de 2 minutes ; purge des publications complétées depuis plus de 7 jours (tâche quotidienne) ; un composant IdempotencyGuard dans events.api (clé = listener + event id, table ou cache ; si table, la créer dans une migration V05b n'est PAS autorisé : utilise la complétion du registre Modulith comme garantie et documente-le, ou une table en mémoire Caffeine côté abonné) ; une documentation courte docs/ops/events.md expliquant comment lire event_publication en cas d'incident.

Périmètre : src/main/java/com/psymate/events/**, docs/ops/events.md, tests. Aucune migration.

Critères d'acceptation (C2 M4-01) :
- un événement dont l'abonné lève une exception reste incomplet ; après « redémarrage » (nouveau contexte Spring dans le test) il est rejoué et complété ;
- un abonné de test reçoit deux fois le même événement (rejeu forcé) et son effet n'est appliqué qu'une fois ;
- les publications complétées depuis > 7 jours sont supprimées par la purge, les incomplètes jamais.

Tests : RepublicationIT, PurgeIT.

Interdits : migration ; toucher à clinical ou linking.

Livrable : PR « M4-01 Outbox complète », ./mvnw verify vert.
```

### Agent 3 · tâche M1-14 — Administration : validation des praticiens (API)

**En clair** : le côté administrateur : la file des dossiers à vérifier (du plus ancien au plus récent, avec un indicateur « hors délai » au-delà de 2 jours ouvrés), l'ouverture d'un dossier, la visualisation de chaque justificatif par lien temporaire (chaque ouverture est journalisée), le marquage conforme / non conforme, la liste de contrôle, et la décision : approuver, demander un complément (message obligatoire), refuser et suspendre (message obligatoire, sessions révoquées). Chaque décision est enregistrée pour toujours et notifiée par e-mail. L'administration ne voit jamais un patient.

```bash
claude --worktree M1-14
```

```text
Tâche M1-14 — Administration : validation des praticiens (dépôt psymate-api, module identity, sous-package identity.admin). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10 (F30, F31, UC-17, A10.1 règle ArchUnit identity.admin, A10.3 machine d'états, A10.4 endpoints /api/admin/verifications/*, ADR-12) ; docs/E-modele-donnees.md E3.3 (verification_decision.kind ∈ approved|needs_more|rejected|…, ck_decision_message, document_access_log, purge_after = decided_at + 90 jours), E3.2 (verified_at, verified_by, suspended_at, purge_after du compte) ; docs/design/20-admin-validation-praticiens.md (file, checklist, décisions, SLA) ; docs/C-plan.md C6 ligne M1-14 ; docs/G-decisions.md G3 (Q29 : le journal admin est la table security_event, aucune autre table).

Objectif : garde : role = admin ET 2FA active, sinon 403 (filtre posé par M1-11, vérifie qu'il couvre /api/admin/**) ; GET /api/admin/verifications?status&sort&cursor → dossiers under_review (défaut) ou needs_more, du plus ancien au plus récent, overdue = submittedAt + 2 jours ouvrés < now ; GET /api/admin/verifications/{practitionerId} → identité, coordonnées, documents (type, title, issuer, year, reviewed_status), décisions passées, JAMAIS de donnée patient (le module n'y a pas accès) ; GET /api/admin/verifications/{id}/documents/{docId}/url → URL signée 5 min + ligne document_access_log ; PATCH /api/admin/verifications/{id}/documents/{docId} { reviewedStatus } ; POST /api/admin/verifications/{id}/decision { kind: approved|needs_more|rejected, messageToPractitioner, internalNote, checklist } → ligne verification_decision (append-only) ; effets : approved → verification_status = approved, verified_at/by, PractitionerApproved ; needs_more → needs_more, e-mail avec le message ; rejected → rejected, suspended_at, purge_after du compte = now + 30 jours, toutes les sessions révoquées, PractitionerRejected, e-mail ; dans tous les cas purge_after des documents = now + 90 jours ; message obligatoire pour needs_more et rejected (422 sinon, en plus de la contrainte SQL) ; chaque décision écrit aussi une ligne security_event (kind admin.decision) ; rappel quotidien par e-mail aux admins listant les dossiers overdue ; règle ArchUnit dédiée : com.psymate.identity.admin n'importe rien de com.psymate.patients, clinical, linking, overview, search (si M0-02 l'a déjà écrite, vérifie et renforce).

Périmètre : src/main/java/com/psymate/identity/admin/**, tests, ArchitectureTests (ajout de la règle si absente). Aucune migration. Ne touche pas à identity/auth, preferences, verification (lecture via leurs classes de identity.api ou package-private du module identity si nécessaire : identity est un seul module Modulith, ses sous-packages peuvent se voir ; signale tout accès transverse en tête de PR).

Critères d'acceptation (C6 M1-14, fiche 20) :
- un praticien non admin → 403 sur /api/admin/** ; un admin sans 2FA → 403 ;
- la règle ArchUnit échoue si on ajoute un import de com.psymate.patients dans identity.admin (démontré dans la PR) ;
- approved → badge et annuaire débloqués : PractitionerFacade.verificationStatusOf = approved, événement publié ;
- rejected → le praticien ne peut plus se connecter (403 ACCOUNT_SUSPENDED), sa session précédente est invalide, e-mail reçu avec le motif ;
- needs_more sans message → 422 ;
- chaque ouverture de document crée une ligne document_access_log ;
- la réponse de GET /api/admin/verifications/{id} ne contient aucun champ venant de patient ou entry (test sur le JSON).

Tests : AdminVerificationIT, ArchitectureTests.

Interdits : migration ; liste des comptes, journal, recours, vocabulaire de l'annuaire (M1-14b) ; signalements (V1.1, G Q26).

Livrable : PR « M1-14 Admin validation », ./mvnw verify vert, contrat regénéré.
```

### Agent 4 · tâche M5-03 — Formulaire de contact de la fiche publique

**En clair** : un visiteur envoie un message à un praticien depuis sa fiche ; le message est relayé par e-mail au praticien, protégé contre le spam (champ piège, délai minimal, limite par IP), gardé 30 jours au plus pour la lutte anti-spam, jamais lié à un dossier. Crée la table `contact_request` (migration `V06`).

```bash
claude --worktree M5-03
```

```text
Tâche M5-03 — Formulaire de contact : anti-spam, relais e-mail, purge (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md F26, UC-15 (A2 spam refusé silencieusement) ; docs/E-modele-donnees.md E3.10 partie contact_request (À CRÉER dans V06__directory_contact.sql), E4 ligne V06 ; docs/design/09-fiche-publique-praticien.md (honeypot, 5 / h / IP, délai minimal 3 s, champs nom, e-mail, objet, message, consentement) ; docs/C-plan.md C2 ligne M5-03 ; G Q24 : la limite par IP utilise Bucket4j (déjà présent depuis M5-02).

Objectif : migration V06__directory_contact.sql = E3.10 contact_request ; POST /api/public/directory/practitioners/{slug}/contact { name, email, subject, message, consent, website (honeypot, doit rester vide), formShownAt } → 202 toujours (même si refusé silencieusement) ; refus silencieux si honeypot rempli, si now - formShownAt < 3 s, si la fiche n'est pas publiée ; limite 5 / heure / IP (ip_hash = sha256(ip + sel quotidien)) → 429 ; ligne contact_request puis e-mail au praticien (contact_email ou e-mail du compte) avec reply-to = e-mail du visiteur, relayed_at posé, relay_error en cas d'échec ; purge quotidienne des lignes dont purge_after < now (DELETE physique, c'est voulu : E2) ; aucun lien avec un patient, aucune écriture hors directory.

Périmètre : src/main/java/com/psymate/directory/**, V06__directory_contact.sql, tests. Dépendances : identity.api (e-mail du praticien), events.api.

Critères d'acceptation (C2 M5-03, fiche 09) :
- message valide → e-mail dans Mailpit avec le bon destinataire et reply-to ;
- honeypot rempli → 202 et aucun e-mail, aucune ligne ;
- 6e message dans l'heure depuis la même IP → 429 ;
- lignes de plus de 30 jours supprimées par la purge ;
- ArchitectureTests reste vert.

Tests : ContactRequestIT, PurgeIT.

Interdits : stocker le message ailleurs que contact_request ; dépendance clinique.

Livrable : PR « M5-03 Formulaire de contact », ./mvnw verify vert, contrat regénéré.
```

**Après fusion de M5-03** : `tools/tag-contract.sh 0.7.0 && git push --tags` (annuaire : les pages publiques du frontend peuvent démarrer). **Après fusion de M1-14** : `tools/tag-contract.sh 0.8.0 && git push --tags` (écrans admin).

---

## Lot 6 — Les endpoints des entrées : la vague qui ouvre tout

**Avant de lancer** : M1-04 fusionné (pour M1-05) ; M1-14 fusionné (pour M7-08, M5-06 et M1-14b) ; M4-01 fusionné (pour M7-05).
**Agents** : jusqu'à 5 en parallèle. **M1-05 est la priorité absolue** : tout le lot 7 l'attend. M1-14b (couloir `identity`) ne peut tourner en même temps que M7-08 : lancez M7-08 d'abord (court), fusionnez, puis M1-14b.

### Agent 1 · tâche M1-05 — Endpoints des entrées et contrat `v0.3.0`

**En clair** : exposer en REST tout ce que M1-03 et M1-04 ont construit : créer une entrée sur un patient, la lire, la corriger, voir ses versions, démarrer et clôturer une séance. Les erreurs de validation sont renvoyées champ par champ. Chaque réponse d'entrée porte `authorType` (toujours « practitioner » en V1), et ce champ est refusé en écriture.

```bash
claude --worktree M1-05
```

```text
Tâche M1-05 — Endpoints entrées, validation 422, authorType en lecture, contrat (dépôt psymate-api, module clinical). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A5 « Endpoints principaux » et les deux exemples JSON (création d'un ressenti, réponse timeline), A11 (authorType en lecture seulement), C3 « Conventions » ligne API (RFC 9457, errors[] par champ) ; docs/C-plan.md C2 ligne M1-05 ; docs/B-ux.md B4 écran 5 (champs minimaux par type, sélecteur de précision).

Objectif : POST /api/patients/{id}/entries (corps = exemple A5 sans « links » : type, status, occurred{from, to?, precision, label?}, sessionId?, visibility, title?, body?, tags[], feeling{emotionCode, intensity}? | medication{molecule, dosage, frequency, prescriber, periodFrom, periodTo}? | session{modality, durationMin}?) → 201 EntryDto ; GET /api/entries/{id} ; PATCH /api/entries/{id} (correction → version ou grâce ; corps partiel + reason?) ; GET /api/entries/{id}/versions ; DELETE /api/entries/{id} (archive) ; POST /api/entries/{id}/restore ; POST /api/patients/{id}/sessions/start { modality } ; POST /api/sessions/{id}/close { durationMin, summary } ; POST /api/patients/{id}/sessions/contact { modality, occurredFrom, body } ; GET /api/patients/{id}/sessions/current. EntryDto : id, patientId, type, status, visibility, authorType (toujours "practitioner"), occurred{from, to, precision, label}, recordedAt, sessionId, version, title, body, tags[], feeling{emotionCode, family, intensity, valence, arousal}?, medication{…}?, session{kind, modality, durationMin, closedAt}?, linkCount, archivedAt. Validation Jakarta par type ; erreurs 422 application/problem+json avec errors[] = [{field, code, message}]. authorType et submissionId refusés en entrée : 422 UNKNOWN_FIELD si présents dans le JSON de création ou de modification. Le champ « links » de l'exemple A5 est M2-02 : s'il est présent → 422 NOT_YET_SUPPORTED avec commentaire « M2-02 ».

Périmètre : src/main/java/com/psymate/clinical/web/**, DTO et mappers MapStruct dans clinical, tests. Aucune migration.

Critères d'acceptation (C2 M1-05) :
- le payload de l'exemple A5 (sans links) est accepté → 201 avec version 1 et feeling.valence/arousal copiés (ou null) ;
- une erreur par champ : intensité 11 et émotion inconnue dans le même envoi → 422 avec deux entrées dans errors[] ;
- authorType: "practitioner" dans chaque réponse (détail, versions, création) ; authorType envoyé en création → 422 ;
- PATCH 2 minutes après création par le même auteur → version reste 1 ; PATCH 10 minutes après → version 2 (Clock de test) ;
- un praticien ne lit pas l'entrée d'un patient d'un autre (403).

Tests : EntryControllerIT par type, ValidationIT, contrat regénéré et vérifié par la CI.

Interdits : links (M2-02) ; timeline (M3-01) ; migration.

Livrable : PR « M1-05 Endpoints entrées », ./mvnw verify vert, contrat regénéré.
```

### Agent 2 · tâche M7-08 — Compte administrateur de démarrage

**En clair** : une commande `make admin EMAIL=…` qui crée le premier administrateur avec un mot de passe à usage unique affiché une fois, 2FA obligatoire au premier login, et la procédure écrite dans `docs/ops/admin.md`. Le premier admin sera `hushan@gmail.com` (G Q5).

```bash
claude --worktree M7-08
```

```text
Tâche M7-08 — Compte admin de bootstrap et procédure (dépôt psymate-api, module identity, racine pour le Makefile). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10 (rôle admin, 2FA obligatoire), docs/C-plan.md C6 ligne M7-08, docs/E-modele-donnees.md E3.2 (role, totp_enabled_at), docs/G-decisions.md G2 (Q5 : variable ADMIN_EMAIL, valeur par défaut hushan@gmail.com ; mot de passe affiché une seule fois dans le terminal ; aucun e-mail).

Objectif : une commande Spring Boot (profil cli ou ApplicationRunner activé par --create-admin=<email>) qui crée tenant + practitioner role = admin, verification_status = approved, email_confirmed_at = now, mot de passe aléatoire 20 caractères affiché une seule fois sur la sortie standard, drapeau « mot de passe à changer » (colonne existante ? non : utilise preferences {"security.mustChangePassword": true}, lu par le login de M1-11 → 403 PASSWORD_CHANGE_REQUIRED jusqu'au changement ; ajoute ce code dans auth si absent et signale-le en tête de PR) ; cible Makefile admin EMAIL=… (EMAIL prend par défaut la variable ADMIN_EMAIL de .env, elle-même documentée avec l'exemple hushan@gmail.com) ; docs/ops/admin.md : création, premier login, activation 2FA obligatoire, perte d'accès (second admin ou recréation), rotation, procédure identique en production après le premier déploiement.

Périmètre : src/main/java/com/psymate/identity/admin/bootstrap/**, Makefile, .env.example, docs/ops/admin.md, tests. Aucune migration.

Critères d'acceptation (C6 M7-08) :
- après make admin EMAIL=a@b.fr, un admin existe, son mot de passe n'est stocké que haché ;
- sans 2FA active, /api/admin/** répond 403 ; après activation (flux M1-11), 200 ;
- relancer la commande avec le même e-mail → message clair, aucun doublon.

Tests : AdminBootstrapIT.

Interdits : migration ; toucher aux routes admin de M1-14.

Livrable : PR « M7-08 Admin bootstrap », ./mvnw verify vert.
```

### Agent 3 · tâche M1-14b — Administration : comptes, suspension, journal, recours, vocabulaire (nouvelle, G Q15, Q27, Q28)

**En clair** : le reste de l'administration (fiches 21, 22, 27) : la liste de tous les comptes praticiens avec suspension et réactivation manuelles, le journal paginé des décisions et événements de sécurité (en lecture seule, jamais modifiable), le dépôt et le traitement d'un recours par un praticien suspendu, et la modification de la liste fermée des spécialités et publics de l'annuaire. Crée la vue `admin_journal_v` (migration `V10`).

```bash
claude --worktree M1-14b
```

```text
Tâche M1-14b — Administration : liste des comptes, suspension manuelle, journal, recours, vocabulaire de l'annuaire (dépôt psymate-api, module identity, sous-package identity.admin + migration V10). Utilise l'agent backend-module-dev. Cette tâche a été créée par docs/G-decisions.md (Q15, Q27, Q28) ; elle n'a pas de ligne propre dans C.

Contexte : lis CLAUDE.md, docs/G-decisions.md G3 (Q15, Q29) et G4 (Q27, Q28) ; docs/A-architecture.md A10 (A10.1 règle ArchUnit, A10.3 états suspended / closed) ; docs/E-modele-donnees.md E3.2 (security_event, suspended_at), E3.3 (practitioner_appeal, créée par V03), E4 ligne V10 (vue admin_journal_v), E7 O8 ; docs/design/21-admin-praticiens.md, docs/design/22-admin-journal.md, docs/design/27-compte-suspendu.md (section recours) ; la table directory_vocabulary créée par M5-01.

Objectif :
1. Migration V10__admin_journal.sql : vue admin_journal_v (E4) sur security_event + verification_decision, colonnes : occurred_at, kind, actor (admin ou praticien), practitioner_id, summary, details jsonb.
2. GET /api/admin/practitioners?q&status&role&cursor → tous les comptes (jamais de donnée patient), colonnes de la fiche 21 ; GET /api/admin/practitioners/{id} ; POST /api/admin/practitioners/{id}/suspend { reason } → suspended_at, sessions révoquées, PractitionerSuspended, e-mail, security_event admin.suspend ; POST /api/admin/practitioners/{id}/reactivate { reason } → suspended_at NULL, security_event admin.reactivate ; les deux exigent reason (422 sinon).
3. GET /api/admin/journal?kind&practitionerId&from&to&cursor → lecture paginée de admin_journal_v ; aucune route d'écriture, et REVOKE déjà posé sur security_event.
4. Recours : POST /api/me/appeal { message } ouvert aux comptes rejected ou suspended (le filtre ACCOUNT_SUSPENDED de M1-11 doit laisser passer cette route et GET /api/auth/session ; signale la modification en tête de PR), un seul recours ouvert à la fois (409) ; GET /api/admin/appeals?status ; POST /api/admin/appeals/{id}/decision { outcome: accepted|dismissed, messageToPractitioner } → accepted réactive le compte (même effet que /reactivate), e-mail dans les deux cas, security_event admin.appeal.
5. Vocabulaire de l'annuaire : GET/POST/PATCH /api/admin/vocabulary/directory (kind, code, labelFr, labelEn, sortOrder, active) ; désactiver plutôt que supprimer ; un code utilisé par un profil publié ne peut pas être désactivé (409). Passe par une façade DirectoryVocabularyFacade exposée par directory.api (ajoute-la dans directory ; c'est la seule modification hors identity, signale-la).

Périmètre : src/main/java/com/psymate/identity/admin/**, V10__admin_journal.sql, la façade dans com.psymate.directory.api, le filtre de M1-11 (une ligne), tests. Garde admin + 2FA de M1-14 réutilisée.

Critères d'acceptation (fiches 21, 22, 27 ; G) :
- suspend → le praticien reçoit 403 ACCOUNT_SUSPENDED, sa fiche annuaire est dépubliée (via l'abonné de M5-06 s'il est fusionné, sinon test @Disabled("M5-06")), son e-mail reçu ;
- reactivate → connexion à nouveau possible ;
- un praticien suspendu peut déposer un recours et un seul ; l'admin l'accepte → compte réactivé ;
- le journal renvoie la décision de M1-14 et la suspension dans l'ordre chronologique inverse ; toute tentative d'UPDATE sur security_event échoue avec le rôle psymate_app (test SQL) ;
- désactiver une spécialité utilisée par un profil publié → 409 ; une spécialité désactivée disparaît de GET /api/vocabulary/directory ;
- aucun champ patient dans aucune réponse admin (test JSON) ; ArchitectureTests reste vert.

Tests : AdminPractitionersIT, AdminJournalIT, AppealIT, DirectoryVocabularyAdminIT, schéma V10.

Interdits : signalements (V1.1) ; toucher à identity/auth hors la ligne du filtre ; données patient.

Livrable : PR « M1-14b Admin comptes, journal, recours, vocabulaire », ./mvnw verify vert, contrat regénéré.
```

### Agent 4 · tâche M5-06 — Publication conditionnée à la vérification

**En clair** : on ne peut publier sa fiche dans l'annuaire que si l'administrateur a approuvé le dossier ; si un praticien est refusé ou suspendu, sa fiche est dépubliée automatiquement en moins d'une minute ; la fiche publique affiche le badge « Profil vérifié ».

```bash
claude --worktree M5-06
```

```text
Tâche M5-06 — Publication conditionnée à la vérification (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A10.1 (directory lit verification_status via PractitionerFacade ; consomme PractitionerRejected), A10.3 tableau États → Annuaire ; docs/C-plan.md C6 ligne M5-06 ; docs/design/10-mon-profil-public.md (409 missing ["verification"]).

Objectif : ProfileFacade.publish / POST /api/me/profile/publish vérifie PractitionerFacade.verificationStatusOf(id) == approved, sinon 409 { "missing": ["verification", …autres manques] } ; abonné @ApplicationModuleListener sur PractitionerRejected et sur PractitionerSuspended (publié par identity.admin lors d'un rejected en M1-14 et d'une suspension manuelle en M1-14b : vérifie qu'il existe, sinon utilise PractitionerRejected seul et note-le) → published = false, ProfileUnpublished ; la fiche publique et la liste portent verified = true quand approved (M5-02 l'a prévu : vérifie).

Périmètre : src/main/java/com/psymate/directory/**, tests. Aucune migration.

Critères d'acceptation (C6 M5-06) :
- profil complet mais praticien under_review → 409 missing ["verification"] ;
- approved → publication 204 ;
- PractitionerRejected publié → en moins d'une minute (test : abonné synchrone ou attente ≤ 60 s) la fiche répond 404 sur /api/public/directory/practitioners/{slug}.

Tests : PublicationGateIT.

Interdits : migration ; dépendance clinique.

Livrable : PR « M5-06 Publication conditionnée », ./mvnw verify vert.
```

### Agent 5 · tâche M7-05 — Vérification des réserves V2 et compagnon

**En clair** : un test de conformité qui prouve que tout ce qui a été réservé pour plus tard est bien là et bien vide : les événements attendus passent par l'outbox, les tables `entry_embedding`, `entry_chunk`, `ai_feedback`, `patient_device`, `patient_submission` existent et sont vides, aucune entrée n'a un `author_type` autre que praticien, `authorType` est dans le contrat en lecture, et les packages `ai` et `companion` sont vides. Il tourne en CI et protège contre un agent qui « nettoierait » ces réserves.

```bash
claude --worktree M7-05
```

```text
Tâche M7-05 — Vérification des hooks V2 et des réserves du compagnon (dépôt psymate-api, module events pour la partie outbox, src/test pour le reste). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.9, A8 « Décisions V1 prises uniquement pour la V2 », A11 (règles, R13) ; docs/C-plan.md C2 ligne M7-05 et C7 ; docs/E-modele-donnees.md E3.11, E3.12, E4 « Vérifications en CI ». Note : ai_feedback n'existe qu'après M2-01 (V07) et link n'existe pas encore : écris les tests correspondants avec @Disabled("M2-01") et un commentaire, à réactiver au lot 7.

Objectif : une classe ReservesConformanceIT qui vérifie : (1) après création d'un patient, d'une entrée et d'une correction, event_publication contient PatientCreated, EntryCreated, EntryCorrected (LinkAccepted/LinkRejected : @Disabled M2-01) ; (2) entry_embedding, entry_chunk, patient_device, patient_submission existent et count = 0 (ai_feedback : @Disabled) ; (3) SELECT count(*) FROM entry WHERE author_type <> 'practitioner' OR submission_id IS NOT NULL = 0 après le seed de test ; (4) la contrainte ck_entry_session_required existe (pg_constraint) ; (5) contract/openapi.yaml contient authorType dans le schéma de réponse EntryDto et dans aucun schéma de requête ; (6) les packages com.psymate.ai et com.psymate.companion ne contiennent que package-info.java (ArchUnit ou scan du classpath) ; (7) les entrées visibility = 'private' sont exclues de l'export (export M6-05 pas encore là : @Disabled("M6-05")). Un rapport texte « Conformité V2 / compagnon » affiché à la fin du test.

Périmètre : src/test/java/com/psymate/ReservesConformanceIT.java et utilitaires de test. Aucun code main, aucune migration.

Critères d'acceptation (C2 M7-05) : rapport vert sur main ; chaque point ci-dessus est un test nommé ; les trois @Disabled sont listés dans la PR avec la tâche qui les réactive.

Interdits : toute modification de code main.

Livrable : PR « M7-05 Conformité des réserves », ./mvnw verify vert.
```

**Après fusion de M1-05** : `tools/tag-contract.sh 0.3.0 && git push --tags` (patients + entrées : le frontend peut construire la liste des patients, la page patient et l'éditeur d'entrée). **Après fusion de M1-14b** : `tools/tag-contract.sh 0.8.1 && git push --tags` (écrans admin complets).

---

## Lot 7 — Liens, timeline, recherche, durcissement partagé

**Avant de lancer** : M1-05 fusionné (pour M2-01, M3-01, M4-02) ; M4-01 fusionné (pour M4-02) ; M5-02 fusionné (pour M7-02).
**Agents** : 4 en parallèle (`linking`, `clinical`, `search`, `shared`). **Attention** : M7-02 touche `shared`, lu par tout le monde : lancez-le en dernier dans le lot et fusionnez-le quand les trois autres sont fusionnés, puis relancez la CI.

### Agent 1 · tâche M2-01 — Liens : table, règles, cycle de vie

**En clair** : relier deux entrées par un lien typé (« déclenche », « aggrave », « soulage »…) avec les règles : même patient, type autorisé pour ce couple source/cible, pas de doublon actif, pas d'auto-lien ; modifier le type crée un nouveau lien et marque l'ancien remplacé ; archiver une entrée remplace ses liens ; les rejets sont gardés pour la future IA. Crée la table `link` et la table vide `ai_feedback` (migration `V07`).

```bash
claude --worktree M2-01
```

```text
Tâche M2-01 — Liens : table, règles, cycle de vie, migration V07 (dépôt psymate-api, module linking). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.3, A7 « Cycle de vie d'un lien », A5 (linking dépend de clinical, vocabulary, events ; expose LinkFacade, GraphQueryFacade, LinkAccepted, LinkRejected), UC-06, ADR-03 ; docs/E-modele-donnees.md E3.7 (link : À CRÉER dans V07__linking.sql), E3.12 ai_feedback (À CRÉER dans V07, vide), E4 ligne V07, E5.3 (matrice allowed_source_types / allowed_target_types déjà en base, conservée : G Q12) ; docs/C-plan.md C2 ligne M2-01 ; docs/design/06-editeur-de-lien.md (ce que l'écran attend : types autorisés seulement, « ce lien existe déjà »).

Objectif : migration V07__linking.sql = E3.7 + ai_feedback de E3.12 ; LinkService : create(sourceId, targetId, linkType, rationale?) → vérifie via EntryFacade.sameOwnedPatient que les deux entrées sont du même patient du praticien courant (403/422 sinon), que (type source, type cible) est autorisé par LinkTypeCatalog (422 LINK_TYPE_NOT_ALLOWED avec les types autorisés), unicité active (409 LINK_EXISTS avec l'id existant), provenance manual, status accepted, author_id, incrémente entry.link_count des deux côtés et patient.clinical_rev, publie LinkAccepted ; changeType(linkId, newType) → nouveau lien manual + ancien superseded (superseded_by) ; remove(linkId) → status superseded, décrémente link_count, LinkRejected n'est PAS émis (rejected est réservé aux suggestions IA : documente-le) ; abonné sur EntryArchived → tous les liens actifs de l'entrée passent superseded ; endpoints POST /api/entries/{id}/links { targetId, linkType, direction incoming|outgoing, rationale? } → 201, PATCH /api/links/{id} { linkType } → nouveau lien, DELETE /api/links/{id} → 204, GET /api/entries/{id}/links → liens acceptés avec la phrase lisible (source, label, cible ; inverse_label si l'entrée est la cible) ; LinkFacade dans linking.api (acceptedLinksOf, countFor).

Périmètre : src/main/java/com/psymate/linking/**, V07__linking.sql, tests. Dépendances : clinical.api, vocabulary.api, events.api, identity.api, patients.api.

Critères d'acceptation (C2 M2-01) :
- lien note → ressenti de type triggers → 422 (non autorisé par la matrice) ; événement → ressenti triggers → 201 ;
- doublon actif → 409 avec l'id existant ; auto-lien → 422 ;
- changer le type : l'ancien est superseded avec superseded_by, le nouveau est manual/accepted ;
- archiver la source → ses liens passent superseded ;
- deux entrées de patients différents → refus ;
- ai_feedback existe et est vide ; link_count des entrées est juste après create/remove.

Tests : LinkServiceIT, LinkRulesTest (table de cas sur la matrice), schéma V07.

Interdits : graphe / CTE (M2-03) ; links[] dans POST /entries (M2-02) ; toucher à clinical.

Livrable : PR « M2-01 Liens », ./mvnw verify vert, contrat regénéré.
```

### Agent 2 · tâche M3-01 — Timeline fenêtrée

**En clair** : l'endpoint qui alimente l'écran Timeline : une fenêtre de temps, au plus 200 entrées triées de la plus récente à la plus ancienne, un curseur pour la suite, des filtres (types, émotions, tags, brouillons), les situations en cours qui chevauchent la fenêtre même si elles ont commencé avant, les compteurs par type et émotion, et un résumé par mois pour la vue « 10 ans ». Doit répondre en moins de 150 ms.

```bash
claude --worktree M3-01
```

```text
Tâche M3-01 — Timeline fenêtrée, curseur, facettes, résumé mensuel (dépôt psymate-api, module clinical). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A6 « Timeline », A5 exemple de réponse timeline (items, nextCursor, facets), A4 « Stratégie d'index » (index déjà créés par V05), UC-07, F11 ; docs/C-plan.md C2 ligne M3-01 ; docs/B-ux.md B4 écran 3 (modes chronologique / par séance, zone « avant le suivi », pastille « dit en séance du… »).

Objectif : GET /api/patients/{id}/timeline?from&to&types&emotions&tags&draftsOnly&includePrivate&cursor&limit (≤ 200) → items triés par occurred_from DESC puis id, keyset cursor encodé base64 sur (occurred_from, id), facettes de la fenêtre (types, emotions, tags), ongoingSituations (situations avec occurred_to NULL ou chevauchant la fenêtre, via occurred_range && tstzrange(from, to)), chaque item = EntryDto allégé (id, type, occurred, title, emotion{code, family}, intensity, tags, linkCount, sessionId, status, authorType, recordedAt) ; GET /api/patients/{id}/timeline/summary?granularity=month|year&from&to → par période : count par type, intensité moyenne des ressentis ; entrées archivées exclues ; private exclues sauf includePrivate = true (le praticien propriétaire seulement).

Périmètre : src/main/java/com/psymate/clinical/** (application/query + web), tests. Aucune migration (les index de E3.6 suffisent ; si un index manque, propose-le dans la PR sans le créer).

Critères d'acceptation (C2 M3-01) :
- 200 éléments en moins de 150 ms et résumé décennie en moins de 100 ms sur un jeu de 2 000 entrées généré par le test (mesure affichée dans le rapport de test, seuil non bloquant en CI mais noté) ;
- le curseur ne saute ni ne répète aucune entrée (test de pagination exhaustive) ;
- une situation commencée avant from et toujours en cours apparaît dans ongoingSituations ;
- les facettes correspondent exactement au contenu de la fenêtre ;
- les entrées private n'apparaissent pas sans includePrivate.

Tests : TimelineIT, TimelinePaginationTest, perf indicative.

Interdits : migration ; recherche plein texte (M4-02) ; toucher à linking.

Livrable : PR « M3-01 Timeline », ./mvnw verify vert, contrat regénéré.
```

### Agent 3 · tâche M4-02 — Recherche plein texte

**En clair** : chercher dans le dossier d'un patient : plein texte en français tolérant aux fautes (« licencimen » trouve « licenciement »), classement par pertinence puis récence, extraits surlignés, facettes par type, émotion, tag, période. Installe les triggers qui maintiennent l'index de recherche (migration `V08`).

```bash
claude --worktree M4-02
```

```text
Tâche M4-02 — Index de recherche et requêtes (dépôt psymate-api, module search). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A6 « Recherche » (requête SQL de référence, repli trigram si < 3 résultats, facettes, boost récence ×1,5 sur 90 jours), A7 séquence 4, UC-10, A5 (search dépend de events et patients) ; docs/E-modele-donnees.md E3.9 partie recherche (fonction entry_search_vector_refresh, trigger, index GIN : À CRÉER dans V08__search.sql, DDL à copier), E4 ligne V08 ; docs/C-plan.md C2 ligne M4-02 ; docs/design/07-recherche-dossier.md (syntaxe tag: émotion: avant:, puces par type avec compteurs).

Objectif : migration V08__search.sql = E3.9 partie recherche ; un abonné dans search sur EntryCreated / EntryCorrected qui appelle entry_search_vector_refresh(id) (le trigger couvre le changement de version ; l'abonné couvre tags et émotions modifiés hors version) et un rafraîchissement initial de toutes les entrées existantes au premier démarrage après V08 (tâche one-shot ou script make) ; GET /api/patients/{id}/search?q&types&emotions&tags&from&to&sessionId&includeArchived&page → résultats classés ts_rank_cd puis récence avec boost 90 jours, snippet ts_headline sur body, facettes (types, emotions, tags) sur le même filtre, section approximate = true pour les résultats du repli trigram (similarity > 0.3) quand moins de 3 résultats exacts ; parsing de la syntaxe q : « tag:travail », « émotion:anxiété » ou « emotion:anxiety », « avant:2026-03 », « après:… » retirés du texte et appliqués en filtre ; configuration french + unaccent via f_unaccent.

Périmètre : src/main/java/com/psymate/search/**, V08__search.sql, tests. Dépendances : events.api, patients.api (garde de propriété), identity.api. Lecture SQL directe de entry / entry_version autorisée pour ce module de projection (A5 : search reconstruit depuis les événements et lit par SQL), jamais via les classes de clinical.

Critères d'acceptation (C2 M4-02) :
- « licencimen » trouve « licenciement » (section approchés) ; « licenciement » le trouve en exact avec snippet surligné ;
- 5 000 entrées : réponse < 300 ms (mesure dans le rapport de test) ;
- une entrée dont on change les tags sans nouvelle version est retrouvée par son nouveau tag ;
- facettes exactes ; tag:travail filtre ; archivées exclues par défaut ;
- un praticien ne cherche pas dans le dossier d'un autre (403).

Tests : SearchIT, QuerySyntaxTest.

Interdits : toucher à clinical ; modifier V05.

Livrable : PR « M4-02 Recherche », ./mvnw verify vert, contrat regénéré.
```

### Agent 4 · tâche M7-02 — Limitation de débit, journal d'audit, erreurs

**En clair** : les protections transversales : limiter le nombre de requêtes par session et par IP (surtout sur les routes publiques), journaliser qui a lu ou modifié quel patient, et uniformiser toutes les réponses d'erreur au format RFC 9457. À lancer **après** les trois autres agents du lot et à fusionner en dernier, car il touche le code partagé par tous les modules.

```bash
claude --worktree M7-02
```

```text
Tâche M7-02 — Limitation de débit, journal d'audit applicatif, erreurs cohérentes (dépôt psymate-api, module shared + configuration). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A9 R7, C3 « Conventions » ligne API ; docs/C-plan.md C2 ligne M7-02 ; docs/E-modele-donnees.md E3.2 security_event (le journal d'accès patient y va avec kind = 'patient.read' / 'patient.write', details = { patientId, route } : décision O8 de E7 et G Q29 : pas de nouvelle table) ; docs/G-decisions.md G2 (Q24 : Bucket4j, valeurs actuelles inchangées) ; les limites déjà posées localement par M5-02 (60/min/IP public), M5-03 (5/h/IP), M1-10 (resend), M0-04/M1-11 (login) : tu les regroupes dans un composant unique RateLimiter sur Bucket4j sans changer leurs valeurs.

Objectif : RateLimiter configurable par route dans application.yml (clé IP pour /api/public/**, clé session pour /api/** : 600 req/min par session) → 429 avec Retry-After ; les limites existantes migrent vers ce composant ; AuditFilter : toute requête sur /api/patients/{id}/** et /api/entries/{id} écrit une ligne security_event (practitioner_id = courant, kind patient.read ou patient.write selon la méthode, details { patientId, method, path }) de façon asynchrone ; ProblemDetail handler global : toutes les exceptions → application/problem+json { type, title, status, detail, instance, code, errors[] } ; codes d'erreur centralisés dans shared ; pages d'erreur cohérentes pour 401, 403, 404, 409, 413, 415, 422, 423, 429, 500.

Périmètre : src/main/java/com/psymate/shared/**, configuration Spring Security/web, les points d'appel des limites existantes dans identity et directory (remplacement 1 pour 1, signalé en tête de PR), tests. Aucune migration.

Critères d'acceptation (C2 M7-02) :
- un scan de l'annuaire (61 requêtes/min) est ralenti : 429 ;
- chaque GET et chaque PATCH sur un patient produit une ligne security_event avec le patientId ;
- toute erreur a le même format ; un test provoque les dix codes et vérifie le corps ;
- les tests de M5-02, M5-03, M1-11 restent verts après la migration vers RateLimiter.

Tests : RateLimitIT, AuditIT, ProblemDetailIT.

Interdits : migration ; changer une valeur de limite existante ; toucher à la logique métier des modules.

Livrable : PR « M7-02 Débit, audit, erreurs », ./mvnw verify vert.
```

**Après fusion de M3-01** : `tools/tag-contract.sh 0.5.0 && git push --tags` (timeline).

---

## Lot 8 — Entrée + liens, graphe, vue d'ensemble, export

**Avant de lancer** : M2-01 et M3-01 fusionnés ; **aucune PR `clinical` ni `linking` ouverte** (M2-02 touche les deux).
**Agents** : **M2-02 seul d'abord** (court), fusion, puis **3 en parallèle**.

### Agent 1 · tâche M2-02 — Créer une entrée et ses liens en une seule transaction

**En clair** : permettre, quand on saisit un ressenti, de le relier dans le même geste à un ou plusieurs événements ou situations : le champ `links[]` du formulaire. Tout ou rien : si un lien est invalide, l'entrée n'est pas créée.

```bash
claude --worktree M2-02
```

```text
Tâche M2-02 — Création d'entrée avec links[] en une transaction (dépôt psymate-api, modules clinical ET linking : seule tâche autorisée à toucher les deux). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A7 séquence 2 (clinical appelle linking.createLinks dans la même transaction ; un lien invalide annule tout, 422 détaillé), A5 exemple JSON (champ links[] : { targetId, linkType, direction }) ; docs/C-plan.md C2 ligne M2-02 ; docs/D-orchestration-backend.md D5 ligne « clinical + linking » et D6-A6.

Objectif : LinkFacade (linking.api) gagne createForNewEntry(entryId, List<LinkRequest>) appelé par EntryService.create dans la même transaction, après l'écriture de l'entrée et avant l'événement EntryCreated ; direction incoming = la cible devient source ; les erreurs de LinkService remontent en 422 avec errors[] indexées (links[0].linkType…) ; POST /api/patients/{id}/entries accepte links[] et renvoie l'entrée avec ses liens créés (champ links dans EntryDto de création) ; le 422 NOT_YET_SUPPORTED de M1-05 disparaît.

Périmètre : le point d'appel dans clinical.application.EntryService, le DTO de création dans clinical.web, LinkFacade et son implémentation dans linking. Rien d'autre. Aucune migration.

Critères d'acceptation (C2 M2-02) :
- un ressenti avec deux liens valides → 201, les deux liens existent en manual/accepted ;
- un ressenti avec un lien vers l'entrée d'un autre patient → 422, AUCUNE entrée créée (rollback vérifié) ;
- un lien de type non autorisé pour le couple → 422 links[i].linkType, rollback ;
- ModularityTests reste vert (clinical ne dépend toujours que de patients, vocabulary, events ; c'est linking qui dépend de clinical : pour que clinical appelle linking sans dépendance cyclique, utilise un événement interne synchrone ou une interface déclarée dans clinical.api implémentée par linking ; explique ton choix en tête de PR).

Tests : EntryWithLinksIT (transactionnel).

Interdits : toute autre modification de clinical ou linking.

Livrable : PR « M2-02 Entrée + liens », ./mvnw verify vert, contrat regénéré.
```

**Fusionnez M2-02, puis ouvrez trois terminaux.**

### Agent 2 · tâche M2-03 — Graphe : voisinage et niveau de détail

**En clair** : l'endpoint de l'écran Graphe : à partir d'un patient ou d'une entrée, les entrées reliées jusqu'à une profondeur de 1 à 3, avec les arêtes ; au-delà de 150 nœuds, les ressentis de même type reliés au même voisin sont regroupés en un « super-nœud » que l'on peut développer. Moins de 300 ms sur un gros dossier.

```bash
claude --worktree M2-03
```

```text
Tâche M2-03 — Requête de voisinage (CTE récursive), graphe, super-nœuds, expand (dépôt psymate-api, module linking). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A4.4 (CTE de référence, profondeur ≤ 3, seuils), A6 « Graphe » (format nodes/edges, limite 150 nœuds, regroupement, expand), UC-08 ; docs/C-plan.md C2 ligne M2-03 ; docs/design/04-graphe-patient.md (ce que l'écran attend : formes par type, degré, filtres par type de lien).

Objectif : GraphQueryService : neighborhood(patientId, rootId?, depth 1–3, linkTypes?, from?, to?) → CTE récursive sur link (status accepted, index source/target) depuis rootId (ou toutes les entrées du patient si racine = patient, limité aux 150 plus liées) ; nodes { id, type, title, occurred, intensity?, emotion?, degree, authorType } ; edges { id, source, target, linkType, provenance, status } ; au-delà de 150 nœuds : regroupement des nœuds de degré 1, de même type, reliés au même voisin, en super-nœuds { id synthétique, type, count, dominantEmotion?, memberIds[] } ; GET /api/patients/{id}/graph?root&depth&linkTypes&from&to ; POST /api/patients/{id}/graph/expand { superNodeId | memberIds[] } → les nœuds et arêtes du groupe. GraphQueryFacade dans linking.api.

Périmètre : src/main/java/com/psymate/linking/** (query + web), tests. Aucune migration.

Critères d'acceptation (C2 M2-03) :
- sur un jeu généré de 2 000 entrées / 1 500 liens : profondeur 2 en moins de 300 ms (mesure dans le rapport) ;
- profondeur 4 → 422 ; profondeur 3 renvoie au plus les nœuds à distance ≤ 3 (test sur un graphe connu) ;
- 200 ressentis liés à une même situation → réponse ≤ 150 nœuds avec un super-nœud ; expand renvoie les 200 ;
- un praticien ne voit pas le graphe d'un patient d'un autre (403).

Tests : GraphQueryIT, GraphGroupingTest, perf indicative.

Interdits : migration ; toucher à clinical.

Livrable : PR « M2-03 Graphe », ./mvnw verify vert, contrat regénéré.
```

### Agent 3 · tâche M4-03 — Projection « vue d'ensemble »

**En clair** : l'écran d'accueil d'un patient doit s'ouvrir en moins de 200 ms : on ne recalcule pas tout à chaque fois, on garde une « photo » pré-calculée par patient (faits clés, situations actives, tendance d'intensité sur 90 jours, émotions dominantes), mise à jour à chaque événement ; si la photo est en retard, on la recalcule à la volée. Crée la table `patient_overview` (migration `V09`).

```bash
claude --worktree M4-03
```

```text
Tâche M4-03 — Projection vue d'ensemble (dépôt psymate-api, module overview). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md A6 « Vue d'ensemble » (tableau des blocs, sources, rafraîchissement), A7 séquence 3 (source_rev en retard → recalcul à la volée), UC-09, A9 R5, A5 (overview dépend de events et patients) ; docs/E-modele-donnees.md E3.9 partie patient_overview (À CRÉER dans V09__overview.sql), E7 O10 (source_rev = patient.clinical_rev, G Q30) ; docs/C-plan.md C2 ligne M4-03 ; docs/design/02-vue-ensemble-patient.md (vue complète ; la vue « Récit » est M4-06).

Objectif : migration V09__overview.sql = E3.9 patient_overview ; OverviewProjector : calcule payload { facts { age, followedSince, sessionCount, activeMedications[], flags[] }, activeSituations[] (occurred_to NULL, triées par link_count, avec depuis, nombre de ressentis liés, intensité moyenne), trend { points[] 90 jours : date, intensité moyenne glissante 7 j, valence si connue ; markers : séances, changements de médicament }, emotions { top5 90 j avec variation vs 90 j précédents }, drafts { count, openSession } } ; OverviewListener sur EntryCreated, EntryCorrected, EntryArchived, LinkAccepted, PatientUpdated → recalcul et écriture (source_rev = patient.clinical_rev courant, compute_ms) ; GET /api/patients/{id}/overview?window=30d|90d|12m → lit patient_overview ; si source_rev < patient.clinical_rev ou absent → recalcule, réécrit, répond ; recentActivity (10 dernières entrées) toujours live, pas dans la projection.

Périmètre : src/main/java/com/psymate/overview/**, V09__overview.sql, tests. Dépendances : events.api, patients.api, identity.api. Lecture SQL directe des tables clinical/linking autorisée pour ce module de projection (A5), jamais via leurs classes.

Critères d'acceptation (C2 M4-03) :
- ouverture < 200 ms sur un jeu de 2 000 entrées (mesure) ;
- test de course : créer une entrée puis lire immédiatement l'overview → la réponse contient l'entrée (recalcul à la volée), jamais une donnée obsolète ;
- le listener est idempotent (deux fois le même événement → même payload) ;
- la tendance a au plus 90 points, les marqueurs de séances sont présents.

Tests : OverviewProjectionIT, OverviewRaceIT, schéma V09.

Interdits : NarrativeComposer / vue Récit (M4-06) ; toucher à clinical, linking.

Livrable : PR « M4-03 Projection vue d'ensemble », ./mvnw verify vert, contrat regénéré.
```

### Agent 4 · tâche M6-05 — Export du dossier (PDF / Markdown) et export du compte refusé

**En clair** : exporter le dossier complet d'un patient en Markdown et en PDF, dans l'ordre chronologique, sans les notes privées. Et, pour un compte refusé ou suspendu (fiche 27), un export complet asynchrone de tous ses patients, téléchargeable par lien pendant 7 jours (G Q18). Crée la table `export_job`.

```bash
claude --worktree M6-05
```

```text
Tâche M6-05 — Export PDF / Markdown d'un dossier, export du compte refusé (dépôt psymate-api, module clinical). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/A-architecture.md F14, A4.7 (private exclu des exports) ; docs/C-plan.md C2 ligne M6-05 ; docs/E-modele-donnees.md E7 O12 (table export_job) ; docs/G-decisions.md G4 (Q18 : export du compte refusé retenu, lien valable 7 jours, contenu = export de chaque patient + liste des patients) ; docs/design/27-compte-suspendu.md (bouton « Exporter mes données ») ; docs/Etude-couleurs-Nordique.md §8 (les exports utilisent le thème clair).

Objectif :
1. GET /api/patients/{id}/export?format=md|pdf → Markdown : en-tête patient (faits clés, sans coordonnées de contact), puis chaque séance dans l'ordre chronologique avec ses entrées (type, date avec précision affichée, titre, texte, émotion/intensité, médicament), liens listés en phrases, versions : seule la version courante ; entrées private exclues toujours ; PDF généré depuis le Markdown via une bibliothèque Java annoncée dans la PR (openhtmltopdf ou équivalent, thème clair, Inter en fallback système) ; en-tête de page « Export PsyMate du … · usage professionnel » ; Content-Disposition attachment.
2. Migration V11__export_job.sql : table export_job (id, practitioner_id, status queued|running|done|failed, requested_at, finished_at, storage_key, expires_at, error) copiée de E7 O12 ; le numéro V11 est à ajouter en fin de E4 (signale-le dans la PR, ne modifie pas E).
3. POST /api/me/export → 202 { jobId } (autorisé aux comptes approved, rejected et suspended : le filtre ACCOUNT_SUSPENDED laisse passer cette route et GET /api/me/export/{jobId}, signale la modification en tête de PR) ; tâche asynchrone : un zip contenant patients.md (liste) + un export Markdown et PDF par patient, chiffré via le StorageService de M1-13, expires_at = now + 7 jours ; GET /api/me/export/{jobId} → statut + URL signée de téléchargement quand done ; purge quotidienne des exports expirés (fichier supprimé, ligne conservée).

Périmètre : src/main/java/com/psymate/clinical/export/**, V11__export_job.sql, le filtre de M1-11 (une ligne), tests. Dépendances : patients.api, linking.api (LinkFacade), identity.api (StorageService exposé via identity.api : si absent, ajoute une façade minimale et signale-le).

Critères d'acceptation (C2 M6-05 + G Q18) :
- le PDF s'ouvre (test : parse avec PDFBox, compte de pages > 0, texte extrait contient le nom affiché) ;
- ordre chronologique vérifié sur trois séances ;
- une entrée private n'apparaît ni dans le Markdown ni dans le texte du PDF ;
- 403 pour un patient d'un autre praticien ;
- un compte suspended lance un export et le télécharge ; après expires_at le lien répond 410 et le fichier est supprimé par la purge.

Tests : ExportIT, AccountExportJobIT.

Interdits : toucher à linking hors lecture via LinkFacade ; export d'un autre praticien.

Livrable : PR « M6-05 Export », ./mvnw verify vert, contrat regénéré.
```

**Après fusion de M2-03** : `tools/tag-contract.sh 0.4.0 && git push --tags` (liens et graphe). **Après fusion de M4-03** : `tools/tag-contract.sh 0.6.0 && git push --tags` (recherche + vue d'ensemble ; M4-02 doit être fusionné aussi).

---

## Lot 9 — Fin du backend V1

**Avant de lancer** : M4-03 fusionné (pour M4-06) ; M2-03, M3-01, M4-02, M4-03 fusionnés (pour M7-01) ; M7-02 fusionné (pour M7-06).
**Agents** : 3 en parallèle. M7-06 est un document, pas du code : vous pouvez le faire vous-même ou le confier à un agent en lecture seule.

### Agent 1 · tâche M4-06 — Résumé rédigé par règles (vue « Récit »)

**En clair** : la vue simple de l'accueil patient raconte le patient en 3 à 5 phrases assemblées par des règles (sans IA) : où il en est, ce qui monte ou descend, ce qui s'est passé depuis la dernière séance, la dernière séance en entier. Enrichit la projection de M4-03.

```bash
claude --worktree M4-06
```

```text
Tâche M4-06 — Résumé rédigé par règles et payload enrichi (dépôt psymate-api, module overview). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/C-plan.md C6 ligne M4-06 ; docs/design/02-vue-ensemble-patient.md en entier (section « Résumé assemblé par règles », vue simple « Récit », feelings en points réels, sinceLastSession, recentSessions) ; docs/B-ux.md B8 « Deux niveaux pour la vue d'ensemble » ; docs/A-architecture.md A6 ; G Q23 : fragments FR complets obligatoires, EN si possible.

Objectif : NarrativeComposer (overview.application) : fragments FR (et EN) dans messages_fr/en.properties, assemblés par règles : phrase 1 cadre (suivi depuis, rythme, nombre de séances) ; phrase 2 tendance (« monte » si moyenne 30 j > moyenne 30 j précédents + 1, « baisse » si < -1, « reste stable » sinon ; ressenti dominant nommé) ; phrase 3 situations actives en phrase ; phrase 4 événement non commenté (événement des 30 derniers jours sans lien ni ressenti postérieur → phrase dédiée) ; phrase 5 traitement si médicament actif ; point d'attention si flag actif ; payload enrichi : narrative { sentences[], attention? }, feelings { points[] : une valeur par ressenti réel (date, code, famille, intensité), séries par émotion, markers médicament, ticks séances }, sinceLastSession { entries[] }, recentSessions[] (5, la dernière avec son compte rendu complet) ; GET /api/patients/{id}/overview renvoie le payload complet, le client choisit la vue.

Périmètre : src/main/java/com/psymate/overview/**, messages, tests. Aucune migration.

Critères d'acceptation (C6 M4-06) :
- seed « Marie D. » du test (anxiété croissante sur 60 jours) → phrase contenant « monte » ;
- seed plat → « reste stable » ;
- événement sans commentaire → phrase dédiée présente ; avec un ressenti lié → absente ;
- locale en → phrases en anglais si les fragments EN existent, sinon repli FR sans erreur ;
- aucune phrase ne contient de terme diagnostic (liste interdite : trouble, diagnostic, risque, pathologie ; test).

Tests : NarrativeComposerTest (table de cas), OverviewNarrativeIT.

Interdits : migration ; IA ; toucher aux autres modules.

Livrable : PR « M4-06 Récit », ./mvnw verify vert, contrat regénéré.
```

### Agent 2 · tâche M7-01 — Seed réaliste et tests de charge

**En clair** : un générateur de données fictives (5 patients pour le quotidien, 100 patients × 2 000 entrées × 10 ans pour la performance, 1 patient DEMO lisible pour l'onboarding) et les scénarios de charge qui vérifient les cibles de latence sur la timeline, la recherche, la vue d'ensemble et le graphe.

```bash
claude --worktree M7-01
```

```text
Tâche M7-01 — Seed réaliste et tests de charge (dépôt psymate-api, tools/seed et tests de performance). Utilise l'agent seed-perf.

Contexte : lis CLAUDE.md, docs/C-plan.md C3 « Données de démonstration » et C2 ligne M7-01 ; docs/E-modele-donnees.md E5.5 et E5.3 (matrice des liens à respecter), E3.6 (séance obligatoire, author_type) ; docs/A-architecture.md A6 (cibles : recherche < 300 ms p95 sur 5 000 entrées, overview < 200 ms, timeline < 150 ms, graphe < 300 ms profondeur 2) ; docs/B-ux.md B1 personas (pour des dossiers crédibles) ; docs/G-decisions.md G4 (Q22 : le patient DEMO est généré ici, 18 mois, 20 séances, marqué « DEMO », relu ensuite par le propriétaire du projet).

Objectif : tools/seed (module Java lancé par make seed SIZE=small|medium|demo, profil local uniquement, refuse de tourner sur un autre profil) : prénoms et initiales fictifs, 10 ans d'historique, séances toutes les 1 à 3 semaines, situations qui s'ouvrent et se ferment, ressentis dont l'intensité suit les situations actives, médicaments avec changements de posologie, 10 % de dates imprécises (toutes les précisions), 5 % d'entrées privées, liens plausibles respectant la matrice, tout passant par les services (EntryService, LinkService) et jamais par du SQL direct, pour que les événements et projections suivent ; scénarios de performance (src/test/java/com/psymate/perf, profil perf, exclus de la CI de PR, lancés par make perf) mesurant p50/p95 des quatre endpoints sur le seed medium, rapport Markdown dans target/perf-report.md.

Critères d'acceptation (C2 M7-01) : toutes les cibles de A6 tenues sur le seed medium, rapport joint à la PR ; make seed SIZE=small tourne en moins de 2 minutes ; aucune entrée du seed sans session_id ; author_type = practitioner partout ; le patient DEMO a display_name commençant par « DEMO · ».

Interdits : SQL direct d'insertion ; exécution hors profil local ; modification des modules métier (si une cible n'est pas tenue, ouvre une issue avec la mesure au lieu de corriger).

Livrable : PR « M7-01 Seed et charge », ./mvnw verify vert.
```

### Agent 3 · tâche M7-06 — Document de passation pour l'étude hébergement

**En clair** : pas de code. Un document qui liste ce que l'étude hébergement / sécurité / RGPD devra décider (G Q16, Q17, Q19) et où, dans le code, ces décisions se branchent (configuration externalisée, secrets, journaux, stockage des fichiers, sauvegardes, fournisseur SMTP).

```bash
claude --worktree M7-06
```

```text
Tâche M7-06 — Document de passation pour l'étude hébergement (dépôt psymate-api, docs/ops). Utilise l'agent reviewer (lecture seule du code) puis écris le document.

Contexte : lis docs/A-architecture.md « Plus tard (hors périmètre) », A9 R7, A10 R12 ; docs/C-plan.md C2 ligne M7-06 ; docs/E-modele-donnees.md E2 (secrets, chiffrement), E7 O9, O12 ; docs/G-decisions.md G2 (Q4 : fournisseur SMTP à choisir) et G4 (Q16, Q17, Q19 : décisions reportées à cette étude) ; le code : application.yml (toutes les clés app.*), StorageService (M1-13), app.crypto.key (M1-11, M1-13), Spring Session, security_event, export_job.

Objectif : docs/ops/hebergement-passation.md : (1) inventaire des décisions attendues : hébergeur HDS, chiffrement au repos et des sauvegardes, gestion des clés (app.crypto.key : rotation), RPO/RTO, rétention des journaux, fournisseur SMTP pour noreply@psymate.com, stockage des justificatifs et des exports en bêta (S3, MinIO ou hébergeur HDS), RGPD (registre, DPO, droits d'accès et d'effacement face à l'append-only, durée de conservation après clôture, mineurs et consentement des représentants légaux), modèle de menaces ; (2) pour chaque décision, le point d'entrée dans le code (clé de configuration, interface, table) ; (3) ce qui est déjà fait (Argon2id, AES-GCM, URL signées, purges, REVOKE, rate limiting) et ce qui manque ; (4) liste des secrets à provisionner.

Critères d'acceptation (C2 M7-06) : document relu par toi, rien d'implémenté, aucune clé ou URL réelle.

Livrable : PR « M7-06 Passation hébergement ».
```

---

## Après le lot 9

Le backend V1 est complet. Les anciennes questions « à trancher » sont closes (G) :

| Sujet | Décision | Où |
| --- | --- | --- |
| Liste des comptes, suspension manuelle, journal, recours, vocabulaire de l'annuaire (Q15, Q27, Q28) | Fait | M1-14b, lot 6 |
| Export du compte refusé (Q18) | Fait | M6-05, lot 8 |
| Signalements de l'annuaire (Q26) | V1.1 | Deux tâches `directory` à écrire plus tard (DDL E6.1) |
| Agenda Google (Q25) | Hors V1 | Aucun jalon Agenda (ancien M8) ; fiches 15 et 15b marquées « plus tard » |
| Étude hébergement HDS / RGPD (Q16, Q17, Q19) | Après le backend V1 | M7-06 prépare la passation |

Le même document pour le frontend `psymate-web` (**document H**) suivra le même plan : préparation, lots alignés sur les tags de contrat (`v0.1.0` → auth, `v0.2.0` → identité étendue, `v0.3.0` → patients et entrées, …), un prompt par écran à partir des fiches `docs/design/`.

---

## Lot 10 — Compléments du contrat pour la V1 web (M10, ajouté le 2 novembre 2026)

Les fiches design écrites le 2 novembre (`docs/design/03, 05, 08, 18, 19, 28, 29`) ont révélé sept écarts entre ce que les écrans doivent afficher et ce que le contrat `contract-v0.9.0` expose. Ils sont décrits dans `docs/C-plan.md` **C9** (tâches M10-01 à M10-07) et `docs/couverture-ecrans.md` §4. Les lots M8 (migration JPA) et M9 (restructuration) n'ont pas de prompt ici : ils suivent `docs/etudes/revue-architecture-2026-11-01.md`.

**Avant de lancer** : G Q36 interdit toute tâche fonctionnelle dans un module pendant sa restructuration M9. Donc, par couloir : `directory` (M10-01, M10-02) attend **M9-11** ; `identity.auth` (M10-03, M10-04) attend **M9-17** ; `patients` (M10-05) attend **M9-09** (M9-04 est déjà fusionnée). Si le lot 3 web (annuaire) arrive avant M9-11, trancher dans G (nouvelle ligne) que M10-01 et M10-02 passent avant, puis lancer.
**Agents** : 3 couloirs en parallèle ; dans chaque couloir, les deux tâches sont séquentielles. M10-07 est V1.1 : après la V1 web ; M10-06 a été avancée en V1 le 3 novembre 2026 (contrat `v0.11.0`).
**Contrat** : chaque tâche regénère `contract/openapi.yaml` (`make contract`) **sans** changer `app.contract.version` ; après la fusion de M10-05 (dernière des cinq), vous faites le commit « Contrat v0.10.0 » (bump de `app.contract.version` dans `application.yml`, `make contract`) puis `tools/tag-contract.sh 0.10.0 && git push --tags`, comme pour les tags précédents.
**Structure** : « respecte la structure du module telle qu'elle est sur `main` » veut dire : dossiers par rôle (`api / controller / dto / service / repository / model / config`, G Q33, Q35) si la tâche M9 du module est fusionnée, sinon la structure existante.

### Couloir `directory` · Agent 1 · tâche M10-01 — Vocabulaire de l'annuaire en accès public

**En clair** : les pages publiques de l'annuaire (fiche 08) doivent afficher les libellés des spécialités, publics, modalités et langues, mais `GET /api/vocabulary/directory` exige une session praticien. On ajoute le même contenu sous `/api/public/`, sans session, avec la limite de débit publique.

```bash
claude --worktree M10-01
```

```text
Tâche M10-01 — Vocabulaire de l'annuaire en accès public (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-01, docs/G-decisions.md Q15 (liste fermée) et Q24 (limites de débit), docs/A-architecture.md A4.10, docs/design/08-annuaire-public.md (sections « Route & module » et « Écarts avec la maquette »). Le code : DirectoryVocabularyController (GET /api/vocabulary/directory, réponse DirectoryVocabularyDto), le service qu'il appelle, PublicSecurityConfig (chaîne /api/public/** sans session ni CSRF), la règle de débit app.rate-limits.rules.public-directory (/api/public/**, 60 par minute et par IP) dans application.yml. Respecte la structure du module telle qu'elle est sur main.

Objectif : GET /api/public/directory/vocabulary, sans authentification, qui renvoie exactement le même DTO que GET /api/vocabulary/directory (spécialités et publics actifs, modalités, langues, mêmes codes, mêmes libellés FR/EN, listes complètes non paginées), servi par le même service (aucune duplication de requête). L'endpoint praticien est conservé tel quel. Documentation springdoc complète (summary, description « Public : sans session… », réponses 200 et 429) comme les autres endpoints publics. Vérifie que la chaîne PublicSecurityConfig et la règle public-directory couvrent le nouveau chemin sans modification ; si ce n'est pas le cas, explique dans la PR ce que tu as dû ajouter.

Périmètre : src/main/java/com/psymate/directory/** (un contrôleur public ou une méthode de PublicDirectoryController, selon la structure), tests src/test/java/com/psymate/directory/**, contract/openapi.yaml regénéré. Aucune migration, aucune modification de identity.

Critères d'acceptation (C9 M10-01) :
- sans cookie de session, GET /api/public/directory/vocabulary répond 200 avec les mêmes codes et libellés que GET /api/vocabulary/directory appelé avec une session ;
- une spécialité désactivée par l'admin (PATCH /api/admin/vocabulary/directory/specialty/{code}) n'apparaît dans aucune des deux réponses ;
- au-delà de 60 requêtes par minute depuis la même IP, 429 avec Retry-After (test avec la règle abaissée dans le profil test, comme PublicDirectoryIT le fait déjà) ;
- aucune donnée patient ni praticien hors vocabulaire dans la réponse (ArchitectureTests reste vert).

Tests : PublicDirectoryVocabularyIT (Testcontainers) ; cas ajouté dans DirectoryVocabularyAdminIT pour la désactivation vue côté public.

Interdits : dupliquer la requête du vocabulaire ; toucher à identity ; changer app.contract.version (le bump et le tag sont faits après M10-05) ; dépendance Maven.

Livrable : PR « M10-01 Vocabulaire de l'annuaire public », ./mvnw verify vert, contrat regénéré, make log.
```

### Couloir `directory` · Agent 1 (à la suite) · tâche M10-02 — Filtre et facette `specialties` dans l'annuaire public

**En clair** : la fiche 08 filtre les praticiens par spécialité, mais `GET /api/public/directory` n'a que `q`, `city`, `lat/lng/radiusKm`, `professions`, `modalities`, `audiences`, `languages`, `accepting`, `sort`, `page`. On ajoute `specialties` et sa facette avec compteurs.

```bash
claude --worktree M10-02
```

```text
Tâche M10-02 — Filtre et facette specialties dans l'annuaire public (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev. Dépend de M10-01 fusionnée.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-02, docs/B-ux.md « Écran 8 », docs/design/08-annuaire-public.md, docs/A-architecture.md A4.10, docs/E-modele-donnees.md E3 (table practitioner_profile : colonne des spécialités, et directory_vocabulary). Le code : PublicDirectoryController (GET /api/public/directory, paramètres et DTO de réponse avec ses facettes), PublicDirectoryService, PublicDirectoryRepository (recherche publique native, G Q32 : JdbcClient ou fragment *NativeQueries selon l'état du module), PublicDirectoryIT. Regarde comment les facettes existantes (professions, audiences, modalities, languages) sont calculées et fais pareil.

Objectif : paramètre de requête specialties (répétable : ?specialties=anxiety&specialties=burnout, codes de la liste fermée), filtre « au moins une des spécialités demandées » sur les profils publiés ; facette specialties dans la réponse (code, libellé, compteur), calculée sans le filtre de spécialité, comme les autres facettes ; un code inconnu ou désactivé → 422 VALIDATION_FAILED avec errors[{field: "specialties", code: "INVALID_VALUE"}] ; q continue de fonctionner indépendamment. Index : si le filtre a besoin d'un index (GIN sur la colonne des spécialités), c'est une migration numérotée selon E4, annoncée dans la PR ; sinon aucune migration.

Périmètre : src/main/java/com/psymate/directory/**, tests, éventuellement une migration (E4), contract/openapi.yaml regénéré.

Critères d'acceptation (C9 M10-02) :
- specialties=anxiety ne renvoie que les profils publiés et approuvés portant ce code ;
- les compteurs de la facette specialties correspondent aux résultats que donnerait chaque code seul, sans le filtre de spécialité ;
- un code hors liste → 422 avec le champ nommé ;
- la latence de GET /api/public/directory ne se dégrade pas (mesure avant/après dans la PR sur le seed small, make seed).

Tests : PublicDirectoryIT (cas ajoutés : filtre, facette, code inconnu, combinaison avec professions et q).

Interdits : saisie libre (les codes viennent de directory_vocabulary, G Q15) ; toucher à identity ; changer app.contract.version ; dépendance Maven.

Livrable : PR « M10-02 Filtre specialties annuaire public », ./mvnw verify vert, contrat regénéré, make log.
```

### Couloir `identity.auth` · Agent 2 · tâche M10-03 — `GET /api/auth/me` enrichi pour le shell web

**En clair** : le shell Angular doit savoir, dès le chargement, si le compte est administrateur, si la 2FA est active et si un changement de mot de passe est imposé (compte créé par `make admin`). Aujourd'hui `MeResponse` ne dit rien de tout cela et le web devrait le déduire d'erreurs 403.

```bash
claude --worktree M10-03
```

```text
Tâche M10-03 — GET /api/auth/me enrichi : role, twoFactorEnabled, passwordChangeRequired (dépôt psymate-api, module identity, sous-package identity.auth). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-03, docs/A-architecture.md A10.4, docs/G-decisions.md Q5 (premier admin : mot de passe à usage unique, changement obligatoire, 2FA obligatoire) et A11 (rien en écriture), docs/design/18-compte-securite.md (sections « Route & module » et « Écarts avec la maquette » : le web attend ces trois champs). Le code : le DTO MeResponse (AuthDtos ou son fichier propre selon la structure du module), AuthController (GET /api/auth/me), AuthService, la table practitioner (colonne role, E3.2), TotpRepository / TwoFactorService (comment on sait que la 2FA est active), identity.admin.bootstrap (M7-08 : comment le « changement de mot de passe obligatoire » est mémorisé), AdminTwoFactorFilter (les 403 TWO_FACTOR_REQUIRED et PASSWORD_CHANGE_REQUIRED, s'ils existent déjà).

Objectif : trois champs ajoutés à MeResponse, documentés springdoc : role ("practitioner" | "admin"), twoFactorEnabled (booléen), passwordChangeRequired (booléen : vrai pour un compte créé par make admin tant que POST /api/auth/password/change n'a pas été appelé ; faux sinon). Aucun de ces champs n'existe dans un DTO d'écriture. Si le flag « changement obligatoire » n'est stocké nulle part aujourd'hui (seulement déduit), stocke-le proprement : colonne dans practitioner via une migration numérotée selon E4, annoncée dans la PR ; sinon aucune migration.

Périmètre : src/main/java/com/psymate/identity/auth/** (et identity/persistence si une colonne est ajoutée), tests src/test/java/com/psymate/identity/auth/**, contract/openapi.yaml regénéré. Respecte la structure du module telle qu'elle est sur main.

Critères d'acceptation (C9 M10-03) :
- un administrateur créé par make admin lit passwordChangeRequired=true, puis false après POST /api/auth/password/change ;
- un praticien qui a activé la 2FA (POST /api/me/2fa/enable puis /verify) lit twoFactorEnabled=true, false après /disable ;
- role vaut "admin" pour l'admin, "practitioner" pour les autres ; aucun des trois champs n'est accepté en écriture (un body qui les contient est ignoré ou refusé 422, test) ;
- ContractIT vert après make contract.

Tests : AuthMeIT (Testcontainers), cas ajoutés dans l'IT de M7-08 (bootstrap admin).

Interdits : toucher à identity.admin hors lecture ; exposer le secret TOTP ; changer app.contract.version ; dépendance Maven.

Livrable : PR « M10-03 GET /api/auth/me enrichi », ./mvnw verify vert, contrat regénéré, make log.
```

### Couloir `identity.auth` · Agent 2 (à la suite) · tâche M10-04 — Changement d'e-mail d'un compte confirmé

**En clair** : `POST /api/auth/email/change` ne sert aujourd'hui qu'à corriger l'adresse d'un compte **non confirmé**. L'écran Compte et sécurité (fiche 18) doit permettre à un praticien confirmé de changer son e-mail de connexion, avec confirmation sur la nouvelle adresse.

```bash
claude --worktree M10-04
```

```text
Tâche M10-04 — Changement d'e-mail d'un compte confirmé (dépôt psymate-api, module identity, sous-package identity.auth). Utilise l'agent backend-module-dev. Dépend de M10-03 fusionnée.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-04, docs/A-architecture.md A10.4, docs/design/18-compte-securite.md (carte « E-mail de connexion ») et docs/design/25-confirmation-email.md (lien 24 h, usage unique, anti-énumération), docs/E-modele-donnees.md E3.2 (auth_token : kinds et colonnes ; security_event), docs/G-decisions.md Q4 (expéditeur), Q23 (textes FR d'abord). Le code : AuthController POST /api/auth/email/change (variante publique actuelle : email + password, compte email_pending seulement) et POST /api/auth/email/confirm, AuthService, AuthTokenRepository, AuthMailer et messages_fr/en.properties, SecurityEvent.

Objectif : la même route POST /api/auth/email/change accepte, avec une session, un corps { newEmail, password } pour un compte email_confirmed, under_review, needs_more ou approved : vérification du mot de passe (les échecs comptent dans le verrou de LoginAttemptLimiter), jeton auth_token kind='email_change' (24 h, haché, usage unique) portant la nouvelle adresse, e-mail envoyé à la NOUVELLE adresse (Mailpit, texte FR obligatoire, EN si possible), réponse 202 dans tous les cas (nouvelle adresse déjà prise : 202 identique + e-mail « vous avez déjà un compte » à cette adresse, comme l'inscription). POST /api/auth/email/confirm { token } avec un jeton email_change : bascule practitioner.email, écrit security_event kind='email_changed', révoque les autres sessions (même mécanisme que /api/auth/sessions/revoke-others), e-mail d'information à l'ANCIENNE adresse. Un jeton expiré ou consommé → 410. La variante publique existante (compte email_pending) reste inchangée. Si auth_token n'a pas de colonne pour stocker la nouvelle adresse, migration numérotée selon E4, annoncée dans la PR.

Périmètre : src/main/java/com/psymate/identity/auth/**, messages_fr/en.properties, éventuellement une migration (E4), tests src/test/java/com/psymate/identity/auth/**, contract/openapi.yaml regénéré.

Critères d'acceptation (C9 M10-04) :
- l'ancien e-mail reste valide pour se connecter jusqu'à la confirmation ; après confirmation, seul le nouveau fonctionne ;
- jeton expiré (horloge contrôlée, 24 h + 1 min) → 410 ; jeton réutilisé → 410 ;
- une adresse déjà utilisée par un autre compte ne fuit pas (202 identique, corps identique, délai comparable) ;
- les autres sessions sont révoquées à la confirmation ; security_event email_changed présent ;
- deux e-mails visibles dans Mailpit (nouvelle adresse : lien ; ancienne : information).

Tests : EmailChangeIT (Testcontainers + Mailpit), test unitaire du jeton (expiration, usage unique).

Interdits : changer l'adresse sans confirmation ; message différent selon l'existence du compte ; changer app.contract.version ; dépendance Maven.

Livrable : PR « M10-04 Changement d'e-mail d'un compte confirmé », ./mvnw verify vert, contrat regénéré, make log.
```

### Couloir `patients` · Agent 3 · tâche M10-05 — `errors[].code` dans le module patients

**En clair** : les erreurs 422 du module `patients` renvoient `errors[{field, message}]` sans `code`, alors que les autres modules renvoient `{field, code, message}`. Le composant « état » du web (planche 29) affiche les messages par champ à partir du `code` ; on harmonise.

```bash
claude --worktree M10-05
```

```text
Tâche M10-05 — errors[].code dans le module patients (dépôt psymate-api, module patients). Utilise l'agent backend-module-dev. Dépend de M9-04 (fusionnée) et M9-09.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-05 et C3 « Conventions » (ligne API : erreurs RFC 9457 avec errors[] par champ), docs/design/29-etats.md section « Correspondance avec le contrat », docs/design/16-fiche-patient.md (règles de validation de la fiche patient). Le code : shared/problem (FieldProblem(field, code, message) unique depuis M9-04, ErrorCode, GlobalExceptionHandler, Problems), PatientException et PatientExceptionHandler (leur FieldError sans code), PatientController et PatientService (où les 422 sont construits), et, pour les codes à réutiliser, la façon dont clinical et identity produisent REQUIRED, INVALID_FORMAT, TOO_LONG, OUT_OF_RANGE, INVALID_VALUE, INVALID_DATE (grep dans src/main/java).

Objectif : toutes les réponses 422 du module patients renvoient errors[] composé de FieldProblem {field, code, message} avec les mêmes codes que les autres modules ; FieldError propre à patients supprimé ; aucune nouvelle valeur de code sans la lister dans la PR. Les messages restent en français (messages_fr.properties). Le schéma OpenAPI des 422 de patients référence le même schéma FieldProblem que les autres modules (vérifie dans contract/openapi.yaml après make contract).

Périmètre : src/main/java/com/psymate/patients/**, tests src/test/java/com/psymate/patients/**, contract/openapi.yaml regénéré. Aucune migration. Respecte la structure du module telle qu'elle est sur main (dossiers par rôle si M9-09 est fusionnée).

Critères d'acceptation (C9 M10-05) :
- POST /api/patients sans displayName → 422, errors[0] = {field: "displayName", code: "REQUIRED", message en français} ;
- birthDate dans le futur → code INVALID_DATE ; displayName de 300 caractères → TOO_LONG ; pronoun hors liste → INVALID_VALUE ;
- PATCH /api/patients/{id} suit les mêmes règles ; les IT existants de patients restent verts ;
- ContractIT vert après make contract, et le diff du contrat ne montre que l'ajout de code dans le schéma des 422 de patients.

Tests : PatientValidationIT (table de cas), cas ajoutés dans l'IT de M1-01.

Interdits : changer la sémantique d'un 403/404 (c'est M9-04) ; toucher à shared/problem autrement qu'en lecture ; changer app.contract.version ; dépendance Maven.

Livrable : PR « M10-05 errors[].code dans patients », ./mvnw verify vert, contrat regénéré, make log.
```

**Après fusion des cinq** : `git checkout main && git pull`, bump `app.contract.version` à `0.10.0` dans `application.yml`, `make contract`, commit « Contrat v0.10.0 : compléments pour la V1 web (M10-01 à M10-05) », `tools/tag-contract.sh 0.10.0 && git push --tags`. Puis côté web : `CONTRACT_TAG=contract-v0.10.0`, copie du contrat dans `docs/contract/openapi.yaml`, regénération de `docs/api-reference.md`.

### Avancée en V1 le 3 novembre 2026 · tâche M10-06 — `GET /api/me/security` : sessions actives et dernière connexion

**En clair** : la carte « Sessions actives » et la carte « Dernière connexion » de la fiche 18 n'ont pas d'API. À lancer après la V1 web, dans le couloir `identity.auth`.

```bash
claude --worktree M10-06
```

```text
Tâche M10-06 — GET /api/me/security : sessions actives, dernière connexion, révocation d'une session (dépôt psymate-api, module identity, sous-package identity.auth). Utilise l'agent backend-module-dev. V1.1 : après la V1 web. Dépend de M10-03.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-06, docs/design/18-compte-securite.md (cartes Sessions actives et Dernière connexion, section « Écarts avec la maquette »), docs/E-modele-donnees.md E3.2 (spring_session, spring_session_attributes, security_event), docs/G-decisions.md Q6 (durées de session), docs/A-architecture.md A9 R7 (pas d'IP complète exposée). Le code : SessionNativeQueries (identity.persistence), POST /api/auth/sessions/revoke-others, IpAddresses (troncature), SecurityEvent (kinds de connexion).

Objectif : GET /api/me/security → { sessions: [{ id (opaque, pas l'id de session HTTP brut), createdAt, expiresAt, lastAccessAt, current, device (déduit de l'User-Agent stocké en attribut de session : navigateur + système, jamais la chaîne brute), city (déduite de l'IP tronquée si une table de géolocalisation existe, sinon null) }], lastLogin: { at, device, city } (dernier security_event de connexion réussie) } ; POST /api/me/sessions/{id}/revoke → 204, ferme cette session (404 si elle n'appartient pas au praticien, 409 si c'est la session courante : utiliser logout). Si l'User-Agent n'est pas encore stocké à la création de session, ajoute-le (attribut Spring Session, pas de migration) et documente que les sessions antérieures auront device = null.

Périmètre : src/main/java/com/psymate/identity/auth/**, identity/persistence (requêtes de session), tests, contract/openapi.yaml regénéré (bump à 0.11.0 décidé au moment du lancement).

Critères d'acceptation (C9 M10-06) : la session courante est marquée current ; révoquer une autre session la rend invalide en moins d'une minute (test : requête avec l'ancien cookie → 401) ; aucune IP complète ni User-Agent brut dans la réponse ; un praticien ne voit jamais les sessions d'un autre.

Tests : MeSecurityIT.

Interdits : exposer l'id de session HTTP ; toucher à identity.admin ; dépendance Maven.

Livrable : PR « M10-06 GET /api/me/security », ./mvnw verify vert, contrat regénéré, make log.
```

### V1.1 · tâche M10-07 — Filtre par spécialité sur `GET /api/admin/practitioners`

**En clair** : quand l'admin ne peut pas désactiver une spécialité utilisée (`409 VOCABULARY_IN_USE`, fiche 28), il doit pouvoir voir quels profils publiés la portent. À lancer après la V1 web, dans le couloir `identity.admin`.

```bash
claude --worktree M10-07
```

```text
Tâche M10-07 — Filtre specialty sur GET /api/admin/practitioners (dépôt psymate-api, module identity, sous-package identity.admin, via la façade directory.api). Utilise l'agent backend-module-dev. V1.1 : après la V1 web. Dépend de M9-16.

Contexte : lis CLAUDE.md, docs/C-plan.md C9 ligne M10-07 et C6 ligne M1-14b, docs/design/28-admin-vocabulaire.md (état B et « Écarts avec la maquette »), docs/design/21-admin-praticiens.md (paramètres de GET /api/admin/practitioners), docs/A-architecture.md A5 (identity.admin ne dépend d'aucun module clinique ; directory expose une façade) et A10.1. Le code : le contrôleur et les requêtes keyset de GET /api/admin/practitioners (identity.admin), la façade de directory.api utilisée par M1-14b pour le vocabulaire, DirectoryVocabularyUsageQueries (compteur usedByPublishedProfiles), ArchitectureTests.

Objectif : paramètre specialty (code de la liste fermée) sur GET /api/admin/practitioners, qui restreint la liste aux comptes dont le profil publié porte cette spécialité ; la liste des ids correspondants est obtenue par une méthode ajoutée à la façade directory.api (identity.admin ne lit jamais practitioner_profile directement), compatible avec le curseur keyset existant ; code inconnu → 422 INVALID_VALUE ; la réponse 409 VOCABULARY_IN_USE de PATCH /api/admin/vocabulary/directory/{kind}/{code} gagne un champ practitionersUrl ("/api/admin/practitioners?specialty=<code>") pour le lien de l'écran 28.

Périmètre : identity/admin/**, directory/api (une méthode de façade et son implémentation), tests, contract/openapi.yaml regénéré (bump décidé au lancement).

Critères d'acceptation (C9 M10-07) : le nombre de comptes renvoyés par specialty=X égale usedByPublishedProfiles du 409 pour X ; un profil dépublié n'apparaît plus ; aucun champ patient dans la réponse ; ArchitectureTests (identity.admin sans module clinique) et ModularityTests restent verts.

Tests : AdminPractitionersIT (cas ajouté), DirectoryVocabularyAdminIT (champ practitionersUrl).

Interdits : lire practitioner_profile depuis identity.admin ; dépendance Maven.

Livrable : PR « M10-07 Filtre specialty admin », ./mvnw verify vert, contrat regénéré, make log.
```
