# psymate-api

## Ce que c'est
API de PsyMate, suivi clinique pour psychologues, psychiatres et coachs. Monolithe modulaire Spring Boot 4.1 (Spring Framework 7, Jackson 3) / Spring Modulith 2.1.2 / Java 21 / PostgreSQL 16. Package racine : com.psymate.
Documents de référence dans docs/ : A-architecture.md, B-ux.md, C-plan.md, D-orchestration-backend.md, E-modele-donnees.md, G-decisions.md, design/*.md.
Pour la base de données, docs/E-modele-donnees.md fait autorité sur A et C. Pour toute question tranchée, docs/G-decisions.md fait autorité sur tous les autres.

## Commandes (utilise uniquement celles-ci)
- make up / make down : Postgres + Mailpit
- ./mvnw verify : build + tests + vérification des modules (doit être vert avant toute PR)
- make verify-fast (Windows : `bash tools/verify-fast.sh`) : même vérification en parallèle comme la CI (build une fois, puis unitaires + tranches d'IT de tools/it-slices.txt, chacune dans son processus Maven) ; 8 min 40 à 9 min mesurés contre 10 min 22 en séquentiel ; un seul verify à la fois sur le poste ; ./mvnw verify reste la référence avant une PR
- ./mvnw test -Dtest=NomDuTest : un test
- ./mvnw spring-boot:run : API sur :8080
- make seed : données fictives
- make contract : regénère contract/openapi.yaml
- make db-reset : recrée la base locale depuis zéro
- make log TASK=… AGENT=… STATUS=… SUMMARY="…" : enregistre l'avancement d'une tâche (skill progress-log) ; make progress : regénère docs/progress/index.html

## Architecture (règles non négociables)
- Un module Spring Modulith par contexte : identity, patients, vocabulary, clinical, linking, search, overview, events, directory. Packages réservés et vides : ai, companion.
- Un module n'écrit jamais dans les tables d'un autre et ne référence jamais ses classes internes ; il passe par la façade du package `api` ou par un événement. Seuls search et overview (modules de projection) lisent en SQL les tables d'autres modules, en lecture seule (G Q34).
- Structure d'un module par rôle (G Q33) : `api / controller / dto / service / repository / model / config`, le suffixe d'une classe donne son dossier, un fichier par DTO (G Q35). Un module garde sa structure actuelle jusqu'à la fusion de sa tâche de restructuration (C8, M9-07 à M9-18) ; d'ici là, un nouveau fichier suit la structure existante de son module.
- Dépendances autorisées : voir package-info.java de chaque module (ApplicationModules.verify() les vérifie).
- directory ne dépend que d'identity et events, et ne touche jamais aux données patient. identity.admin ne dépend d'aucun module clinique.
- Toute entrée appartient à une séance ; toute correction crée une version (sauf règle de grâce 1 h 15) ; aucune donnée clinique n'est supprimée physiquement ; seules les tables listées en E « Suppressions physiques autorisées » le sont (G Q37).
- Les liens sont des lignes de `link` avec provenance et statut ; jamais de clé étrangère "lien" dans `entry`.
- Une migration Flyway fusionnée ne se modifie jamais : on en ajoute une. Les numéros sont réservés dans docs/E-modele-donnees.md E4.
- L'IA V2 n'existe pas en V1 : ne pas écrire de code IA, mais ne pas casser les hooks (outbox, tables vides).
- Le compagnon patient n'existe pas en V1 (A11) : `entry.author_type` vaut toujours 'practitioner', `entry.submission_id` toujours NULL, `patient_device` et `patient_submission` restent vides, le package `companion` reste vide. Ne jamais les supprimer, ne jamais les remplir, ne jamais exposer `authorType` en écriture.
- L'agenda Google (fiches 15, 15b) est hors V1 (G, Q25) : aucun module scheduling.
- Accès aux données (G Q32) : JPA pour les modules CRUD (patients, vocabulary, directory, identity, clinical.export), `JdbcClient` pour search, overview, graphe, timeline et la recherche publique de l'annuaire ; dans un module JPA, le SQL natif (dont le keyset admin) passe par des fragments Spring Data `*NativeQueries` (G Q38). Gardes ArchUnit : `@Immutable` sur les tables append-only, `@DynamicUpdate` sur `entry_version`, aucune association JPA entre modules (UUID nus), jamais d'entité dans un DTO ni dans un contrôleur ; `open-in-view=false`, Flyway seul maître du DDL (`ddl-auto=validate` en test).

## Versions
Spring Boot 4.1.x, Spring Modulith 2.1.2, Java 21 : ne jamais rétrograder, ne jamais ajouter une dépendance de la ligne Boot 3 / Jackson 2 (`com.fasterxml.jackson.*` est Jackson 2 ; Jackson 3 vit sous `tools.jackson.*`). Les noms de starters sont ceux de la ligne 4 : vérifie dans la documentation officielle, pas de mémoire. Toute version est figée dans pom.xml, jamais une plage.

## Conventions
REST JSON camelCase, erreurs RFC 9457 (application/problem+json) avec errors[] par champ, pagination par curseur, dates UTC ISO 8601, MapStruct pour les DTO (`*Mapper` ; un mapping écrit à la main s'appelle `*Assembler`), un fichier par DTO, Jakarta Validation.
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
./mvnw verify vert, contrat à jour, PR ouverte avec le template rempli (ID de la tâche, critères cochés un par un, tests ajoutés, dépendances Maven ajoutées listées), puis `make log …` (skill progress-log) avec le fichier d'événement commité. Jamais d'édition de docs/progress/index.html ni d'un événement existant.
