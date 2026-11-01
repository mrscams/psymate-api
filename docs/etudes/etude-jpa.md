# Étude : réécrire l'accès aux données en JPA / Hibernate

Date : 2026-11-01 · État du code : `main` `adaec30` (backend V1 complet, 35 tâches, 329 tests unitaires, 822 tests d'intégration verts).
Question posée : peut-on passer de `JdbcClient` à JPA (Spring Data JPA + Hibernate) pour simplifier, en gardant des requêtes natives là où JPA ne peut pas ? Si oui, à quel coût, avec quels risques, et dans quel ordre ?

Réponse courte : **oui, c'est faisable**, mais pas comme une réécriture « JPA partout ». Un quart des requêtes resterait du SQL natif quoi qu'il arrive, et plusieurs règles du modèle (tables append-only, droits par colonne, triggers, verrous) demandent des précautions que JPA ne donne pas par défaut. La voie raisonnable est **hybride, module par module**, en commençant par les modules CRUD, et seulement après la V1 web. Le détail et les chiffres suivent.

## 1. Pourquoi le code est en JDBC aujourd'hui

Ce n'est pas une décision tranchée dans G : M0-01 a posé `spring-boot-starter-jdbc` et chaque module a suivi le précédent. `docs/A-architecture.md` l. 311 mentionne « EntryRepository (JPA) », seule trace d'une intention JPA. Il faut une ligne G dans les deux cas (voir §7).

## 2. Inventaire factuel (relevé sur le code, chemins exacts dans l'annexe)

| Mesure | Valeur |
| --- | --- |
| Appels JDBC (`jdbc.sql/query/queryForObject/update`) | 256 dans 30 classes |
| Requêtes logiques (variantes dynamiques comptées) | **269** : 109 écritures (35 INSERT, 65 UPDATE, 9 DELETE), 160 lectures/techniques |
| CRUD simple, une table (S) | **197 (73 %)** |
| Complexes : jointures, CTE, agrégats, SQL dynamique, plein texte (C) | **65 (24 %)** |
| Techniques : verrous advisory, `set_config`, fonctions SQL (T) | 7 (3 %) |
| Tables (`CREATE TABLE`) | 39, dont 24 avec un record Java, 6 réservées jamais touchées, 3 gérées par Spring Session / Modulith |
| Objets de domaine | records immuables (309 records, aucun setter) |
| `@Transactional` | 88, aucune isolation particulière, 1 `MANDATORY` (`set_config` local) |

Où sont les requêtes complexes : search 14, clinical 15 (dont timeline 7), overview 11, identity.admin 9, linking/graphe 6, directory 4, vocabulary 3, events 2, patients 1.

Fonctionnalités PostgreSQL utilisées (toutes avec exemples fichier:ligne en annexe) : `tstzrange &&` et index GiST, `jsonb` (`->>`, cast, opérateur `-`), `tsvector`/`tsquery` (`@@`, `websearch_to_tsquery`, `ts_rank_cd`, `ts_headline`), `pg_trgm` (`similarity`, `word_similarity`, `<%`, `set_config`), `f_unaccent`, CTE simples et `WITH RECURSIVE`, `FOR UPDATE` (9), `pg_advisory_xact_lock` (4), `RETURNING` (2), `ON CONFLICT` (4, dont un `DO UPDATE … WHERE`), tableaux (`= any(:ids)`, `unnest`, colonnes `text[]`), `inet`, `date_trunc`, arithmétique d'intervalles, `LATERAL`, comparaison de tuples pour le keyset, index partiels, type `point` (haversine), une vue, 6 triggers/fonctions, `REVOKE UPDATE, DELETE` sur 5 tables avec un `GRANT UPDATE (title, body, payload)` par colonne.

## 3. Ce que JPA / Hibernate 7 sait faire, et ce qu'il ne sait pas

| Besoin du code | En JPA | Verdict |
| --- | --- | --- |
| CRUD par id / clé unique / FK, count, exists (≈ 197 requêtes) | `JpaRepository`, requêtes dérivées, JPQL | **natif JPA** |
| `FOR UPDATE` (9) | `@Lock(PESSIMISTIC_WRITE)` sur une méthode de repository | JPA ; attention à l'ordre de verrouillage multi-lignes (`AdminPractitionerRepository`) : JPQL `ORDER BY` + lock fonctionne |
| `jsonb` lu/écrit comme texte ou objet | `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6+) | JPA ; les opérateurs `->>` dans un WHERE restent natifs |
| Colonnes `text[]`, `uuid[]` | `@JdbcTypeCode(SqlTypes.ARRAY)` ; `= any(:ids)` ↔ `IN :ids` en JPQL | JPA (le `IN` a des limites de taille, acceptables ici) |
| `inet` | type Hibernate personnalisé (ou colonne `String` + cast côté SQL) | contournable |
| `RETURNING`, `ON CONFLICT DO NOTHING` | pas en JPQL ; `@Modifying` natif, ou `save` + gestion du `DataIntegrityViolationException` | natif ou réécriture |
| `ON CONFLICT … DO UPDATE … WHERE source_rev <= excluded.source_rev` (overview) | natif seulement | **natif** |
| `tstzrange &&`, index GiST | aucun support | **natif** |
| Plein texte, trigrammes, `ts_headline`, `set_config` | aucun support | **natif** (toute la recherche : 14 requêtes) |
| `WITH RECURSIVE`, `LATERAL`, `UNION ALL` sur CTE `unnest` (graphe) | aucun support en JPQL | **natif** (6 requêtes) |
| Keyset sur tuple `(occurred_from, id) < (?, ?)` | réécriture en `a < ? OR (a = ? AND id < ?)` possible en JPQL | JPA, au prix de lisibilité et d'un plan d'exécution moins bon |
| `date_trunc`, intervalles, `greatest`, `string_agg distinct`, `IS DISTINCT FROM` | partiellement (fonctions HQL), souvent `function()` ou natif | natif pour les agrégats admin/overview |
| Verrous advisory (4) | `EntityManager.createNativeQuery` | natif (inchangé) |
| Vue `admin_journal_v` | entité `@Immutable` sur la vue, lecture en JPQL ; filtres dynamiques → Specifications ou natif | JPA possible |
| Colonne `search_vector` écrite par trigger | `@Column(insertable = false, updatable = false)` | JPA, à condition de l'exclure du mapping d'écriture |
| `GRANT UPDATE (title, body, payload)` sur `entry_version` | Hibernate émet `UPDATE … SET toutes_les_colonnes` par défaut → **refus de permission** sous `psymate_app` même sans changement de valeur. Il faut `@DynamicUpdate` sur l'entité, ou garder l'UPDATE natif | **piège** |
| Tables append-only (`REVOKE UPDATE, DELETE`) | entités `@Immutable` (toute tentative de modification est ignorée ou rejetée) | JPA, à marquer explicitement |
| FK `DEFERRABLE INITIALLY DEFERRED` entre `entry.current_version_id` et `entry_version.entry_id` | cycle insert : Hibernate insère l'entrée avec `current_version_id` NULL puis met à jour, ce que la colonne NOT NULL interdit ; ordre d'insertion à forcer ou natif | **piège**, à traiter dans `EntryService.create` |
| Frontières Modulith (jamais de classe interne d'un autre module) | aucun `@ManyToOne` entre entités de modules différents : garder des `UUID` nus (`patient_id` dans `Entry` reste un `UUID`) | règle à imposer par ArchUnit |
| Projections (search, overview) qui lisent les tables de clinical et linking par SQL (autorisé par A5) | elles n'ont pas d'entités : restent natives ou en `@SqlResultSetMapping` | natif |

Bilan : sur 269 requêtes, **≈ 190 passeraient en JPA** (repositories dérivés, JPQL, locks, types JSON/array), **≈ 72 resteraient natives** (plein texte, graphe, timeline, projections overview, admin keyset, upserts, verrous), 7 techniques inchangées. Autrement dit, JPA couvre les 73 % simples et presque rien des 27 % restants.

## 4. Trois options

### Option A — JPA partout (entités pour les 24 tables, natif pour le reste)

- 24 entités + ~20 repositories Spring Data ; ~72 requêtes natives (`@Query(nativeQuery = true)` ou `EntityManager`) avec leurs `RowMapper` actuels transformés en `@SqlResultSetMapping` ou projections d'interface.
- Deux styles de code dans chaque module complexe (JPQL pour le CRUD, SQL pour le reste), exactement ce qu'on voulait éviter.
- Effort : environ **12 à 16 jours-agent** (voir §5), risques §6 en entier.
- Gain : uniformité d'outillage, moins de SQL d'insertion/mise à jour, mapping automatique des records simples.

### Option B — Hybride par module (recommandée si l'on veut JPA)

- JPA dans les modules où le CRUD domine : **identity** (81 requêtes, 73 simples), **patients** (19, 18 simples), **vocabulary** (30, 26 simples), **directory** hors recherche publique (21 simples), **export_job**. Ces modules gagnent le plus et portent le moins de SQL spécifique.
- `JdbcClient` conservé dans les modules de **projection et de requête** : search, overview, graphe (linking.persistence.GraphRepository), timeline, admin keyset. A5 les définit déjà comme lecteurs SQL ; leur valeur est dans le SQL.
- Les deux technologies cohabitent sans problème : même `DataSource`, même gestionnaire de transactions (`JpaTransactionManager` pilote aussi JDBC), tests IT inchangés.
- Effort : environ **7 à 9 jours-agent**, par lots indépendants (un module = une PR, suite IT comme filet).
- Gain : 60 % des requêtes simples migrées, SQL complexe intact et lisible.

### Option C — Rester en JDBC, consolider

- Garder `JdbcClient` ; factoriser les `RowMapper` répétés (46 records-lignes) et les helpers de keyset ; ajouter une ligne G qui acte le choix et ses raisons (§7).
- Effort : 1 à 2 jours. Aucun risque de régression.
- Variante : **Spring Data JDBC** (agrégats, pas de lazy loading, pas de dirty checking, pas de cache) remplacerait la majorité des 197 requêtes simples sans les pièges d'Hibernate ; même effort que l'option B, moins de surprises, mais une bibliothèque moins connue des équipes JPA.

## 5. Estimation d'effort (option A, par module ; B = lignes marquées ★)

| Module | Requêtes (S/C/T) | Travail JPA | Jours-agent |
| --- | --- | --- | --- |
| identity ★ | 81 (73/8/0) | 8 entités (practitioner, tenant, auth_token, totp_recovery_code, verification_document/decision, practitioner_appeal, security_event `@Immutable`), 7 repositories ; 8 requêtes admin natives (keyset) ; vue journal `@Immutable` | 3 |
| patients ★ | 19 (18/1/0) | patient (+ jsonb context/follow_up), patient_version `@Immutable`, patient_flag, patient_tag ; `list` dynamique → Specification + `f_unaccent` en `function()` | 1 |
| vocabulary ★ | 30 (26/3/1) | emotion, tag, entry_type, link_type (`text[]`), emotion_merge ; 3 fusions natives | 1 |
| directory ★ (profil, contact, vocabulaire) | 24 (21/3/0) | practitioner_profile (`text[]`, `point`, `search_vector` non écrit), directory_vocabulary, contact_request ; recherche publique reste native | 1 |
| clinical.export ★ | 13 (9/3/1) | export_job ; lecture d'export native | 0,5 |
| clinical (entry, versions, séances) | 44 (30/12/2) | entry, entry_version (`@Immutable` sauf grâce → `@DynamicUpdate` ou natif), 3 détails, entry_tag ; FK différée ; timeline reste native | 3 |
| linking | 14 (8/6/0) | link ; graphe natif | 1 |
| search, overview, events, shared | 37 (6/27/4) | inchangés (natif) ou `@SqlResultSetMapping` sans intérêt | 0,5 |
| Transverse | — | dépendances (`spring-boot-starter-data-jpa`), `open-in-view=false`, `ddl-auto=validate` contre Flyway, règle ArchUnit « pas de relation JPA entre modules », naming strategy, types `inet`/`point`, revue des 822 IT | 2 |
| **Total** | | | **A ≈ 13 j · B ≈ 8 j** |

Hypothèses : un agent par module comme pour les vagues V2–V9, revue et `verify` inclus ; les tests d'intégration existants (sur vrai Postgres, rôle `psymate_app` dans les tests de schéma) servent de filet et ne sont pas réécrits.

## 6. Risques et précautions

1. **Droits par colonne et tables append-only.** Hibernate met à jour toutes les colonnes par défaut : sous `psymate_app`, `UPDATE entry_version SET …` échoue si la liste contient une colonne non accordée. Obligatoire : `@DynamicUpdate` sur `entry_version` (ou UPDATE natif), `@Immutable` sur `security_event`, `patient_version`, `verification_decision`, `document_access_log`. Un test IT sous `psymate_app` par table (déjà en place pour V03/V05) doit rester vert.
2. **Dirty checking et flush implicites.** Un record lu puis « modifié » n'existe pas aujourd'hui ; avec des entités mutables, une modification oubliée part en base au commit. Mitigation : entités à constructeur complet et sans setters publics, `@Transactional(readOnly = true)` partout en lecture (déjà le cas), pas de cache de second niveau.
3. **Cycle `entry` ↔ `entry_version` avec FK différée.** À écrire à la main dans `EntryService.create` (persist entry sans version, persist version, rattacher, flush) ou garder l'insertion native. À tester sous `psymate_app`.
4. **Frontières de modules.** Interdire par ArchUnit toute association JPA (`@ManyToOne`, `@OneToMany`) dont la cible est une entité d'un autre module ; les identifiants transverses restent des `UUID`. Sinon Modulith se tait (le type cible est dans un autre package) mais le couplage est réel.
5. **Performance.** Les cibles A6 sont mesurées (`make perf`) sur les requêtes natives, qui ne bougent pas. Les risques sont le N+1 sur les listes (patients avec tags/drapeaux, profils) : charger par lot (`@EntityGraph` ou requête dédiée) comme le fait `withRelations` aujourd'hui, et relancer `make perf` après chaque module.
6. **Open-in-view et sérialisation.** `spring.jpa.open-in-view=false` dès le départ ; jamais d'entité dans un DTO (MapStruct existe déjà pour ça) ; Jackson 3 ne doit jamais voir un proxy Hibernate.
7. **Schéma.** `ddl-auto=validate` en test pour détecter un mapping faux ; Flyway reste seul maître du DDL. Les colonnes écrites par trigger (`search_vector`) et les colonnes générées (`occurred_range`) sont `insertable = false, updatable = false`.
8. **Régression silencieuse des codes d'erreur.** Les 409 d'unicité (`LINK_EXISTS`, `TAG_EXISTS`…) reposent aujourd'hui sur `DuplicateKeyException` levée au moment de l'INSERT ; avec JPA elle arrive au flush/commit, souvent hors du `try`. Les `TransactionTemplate` de `LinkService`/`AuthService` montrent le pattern à garder : attraper après le commit, pas dans la méthode.
9. **Taille du changement.** 30 classes réécrites d'un coup = une PR illisible. Un module par PR, chacune avec `verify` vert et `make perf` inchangé.

## 7. Recommandation

- **Maintenant** : ne rien réécrire. Le backend est fini, vert, mesuré ; le frontend n'en dépend pas. Acter le choix actuel dans G (« Accès aux données : JDBC via `JdbcClient` ; JPA non retenu en V1 pour les raisons §3 ; réévaluation possible module par module ») et corriger A-architecture l. 311.
- **Si l'équipe veut JPA pour la maintenabilité des CRUD** : option **B**, après la V1 web, dans cet ordre — patients → vocabulary → directory → identity → clinical.export, une PR par module, avec les gardes §6 (1, 4, 6, 7) posés dans la première PR. Laisser search, overview, graphe et timeline en SQL : les y forcer coûterait plus qu'il ne rapporte.
- **Ne pas faire** l'option A : le SQL complexe resterait, en moins lisible, et les pièges 1 et 3 touchent précisément le cœur clinique.

## Annexe — emplacements cités

Classes d'accès : `identity/auth/PractitionerRepository.java`, `TotpRepository.java`, `AuthTokenRepository.java`, `identity/preferences/PreferencesRepository.java`, `identity/verification/VerificationRepository.java`, `identity/admin/{AdminVerificationRepository,AdminPractitionerRepository,AppealRepository,AdminJournalRepository}.java`, `patients/PatientRepository.java`, `vocabulary/VocabularyRepository.java`, `clinical/persistence/{EntryRepository,TimelineRepository}.java`, `clinical/export/{ExportRepository,ExportJobRepository}.java`, `linking/persistence/{LinkRepository,GraphRepository}.java`, `search/EntrySearchRepository.java`, `overview/{OverviewProjector,OverviewRepository}.java`, `directory/{ProfileRepository,PublicDirectoryRepository,DirectoryVocabularyAdminRepository,ContactRequestRepository}.java`, `events/JdbcEventDeduplicator.java`, `shared/audit/AuditWriter.java`.

Exemples PostgreSQL : `tstzrange &&` `TimelineRepository.java:213` ; `jsonb ->>` `OverviewRepository.java:95` ; `websearch_to_tsquery`/`ts_rank_cd`/`ts_headline` `EntrySearchRepository.java:56,298,312` ; `word_similarity`/`<%`/`set_config` `EntrySearchRepository.java:348-370` ; `WITH RECURSIVE` `GraphRepository.java:60` ; `LATERAL` `OverviewProjector.java:540` ; `ON CONFLICT DO UPDATE … WHERE` `OverviewRepository.java:81-84` ; `pg_advisory_xact_lock` `EntryRepository.java:262` ; keyset tuple `TimelineRepository.java:184` ; `point`/haversine `PublicDirectoryRepository.java:78-81` ; FK différée `V05__clinical.sql:59-61` ; `GRANT UPDATE (title, body, payload)` `V05__clinical.sql:139` ; triggers `V04__directory_profile.sql:52`, `V05__clinical.sql:141`, `V08__search.sql:21,41`.
