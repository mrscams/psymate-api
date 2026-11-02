# Liste de contrôle : convertir UN module en JPA (M8-02..M8-06)

Cadre : G Q32 et `docs/etudes/etude-jpa.md` (§3 verdicts, §5 ordre, §6 risques). Les gardes ArchUnit, `open-in-view=false` et `ddl-auto=validate` en test sont posés par M8-01. Ordre : patients (M8-02), vocabulary (M8-03), directory (M8-04), identity (M8-05), clinical.export (M8-06). Une PR = un module. Le reste de clinical (entry, versions, séances) et linking sont **hors Q32** : les convertir demanderait une nouvelle décision G.

## 1. Périmètre

- [ ] Un seul module dans la PR ; aucun fichier d'un autre module (passer par son package `api`).
- [ ] Liste écrite dans la PR : repositories **convertis** (CRUD une table, `FOR UPDATE`, jsonb, `text[]`) et repositories **laissés en `JdbcClient`** (plein texte, trigrammes, `WITH RECURSIVE`, `LATERAL`, `tstzrange`, `ON CONFLICT ... DO UPDATE ... WHERE`, advisory locks, keyset admin, projections).
- [ ] Contrôleurs, DTO, mappers MapStruct et contrat OpenAPI inchangés (`git status --short contract/` vide, `app.contract.version` inchangé).
- [ ] Aucune migration Flyway : le schéma est la référence, l'entité s'y adapte.

## 2. Entités

- [ ] Constructeur complet pour le code applicatif, constructeur sans argument `protected` pour Hibernate, pas de setters publics (méthodes métier nommées).
- [ ] `equals` / `hashCode` sur l'id seul (UUID attribué par l'application), jamais sur les autres champs.
- [ ] Entités et repositories Spring Data package-private ou internes au module : jamais exposés hors du module, jamais dans un DTO, un événement ni une classe `web` (règle ArchUnit).
- [ ] `jsonb` : `@JdbcTypeCode(SqlTypes.JSON)` ; `text[]` / `uuid[]` : `@JdbcTypeCode(SqlTypes.ARRAY)`. `inet`, `point` : colonne `String` + cast côté SQL, ou type dédié.
- [ ] `@Version` absent sauf besoin explicite (le modèle utilise `clinical_rev` incrémenté par l'application, G Q30).
- [ ] Chaque colonne `NOT NULL` du DDL est non nulle dans l'entité ; `ddl-auto=validate` passe sans `@Column` superflu.

## 3. Tables protégées (psymate_app)

- [ ] `security_event`, `patient_version`, `verification_decision`, `document_access_log` : entité `@Immutable` (aucun UPDATE ni DELETE possible, `REVOKE UPDATE, DELETE`).
- [ ] `entry_version` : `@Immutable`, ou `@DynamicUpdate` si l'entité est modifiée dans la fenêtre de grâce (seules `title`, `body`, `payload` sont accordées, G Q31).
- [ ] Un test sous le rôle `psymate_app` pour chaque table append-only touchée par la PR (écriture autorisée, UPDATE / DELETE refusés).
- [ ] `search_vector` (trigger) et `occurred_range` (colonne générée) : `@Column(insertable = false, updatable = false)`.

## 4. Frontières de modules

- [ ] Clés étrangères transverses en `UUID` nus (`patient_id`, `practitioner_id`, `tenant_id` d'un autre module) : jamais de `@ManyToOne`, `@OneToMany`, `@OneToOne` ni `@ManyToMany` vers une entité d'un autre module.
- [ ] Les associations internes au module sont limitées au strict nécessaire (préférer l'UUID et une requête dédiée).

## 5. Lecture, N+1 et transactions

- [ ] Listes chargées par lot (`@EntityGraph` ou requête dédiée), comme `withRelations` aujourd'hui : pas de N+1 sur patients/tags/drapeaux, profils, vocabulaires.
- [ ] `@Transactional(readOnly = true)` sur toute lecture ; aucune entité modifiée par erreur dans une transaction de lecture.
- [ ] Pas de `LAZY` hors transaction : l'objet retourné par la couche application est un record ou un DTO, jamais l'entité.
- [ ] `FOR UPDATE` : `@Lock(PESSIMISTIC_WRITE)` avec `ORDER BY` stable pour garder l'ordre de verrouillage (voir `AdminPractitionerQueries`).

## 6. Erreurs 409 d'unicité

- [ ] `DataIntegrityViolationException` arrive au flush / commit, pas à l'appel du repository : l'attraper **après** le commit, avec le pattern `TransactionTemplate` de `LinkService` / `AuthService` (jamais un `try/catch` autour de `save`).
- [ ] Les codes d'erreur (`LINK_EXISTS`, `TAG_EXISTS`, ...) et leurs statuts sont inchangés, un test par code.
- [ ] Pour `ON CONFLICT DO NOTHING` / `RETURNING`, garder la requête native plutôt que de réécrire la sémantique.

## 7. Réservé à une éventuelle conversion de clinical (hors Q32, décision G requise)

- [ ] FK `DEFERRABLE INITIALLY DEFERRED` de `entry.current_version_id` (nullable) vers `entry_version.id` (`fk_entry_current_version`, V05 l. 59-61) : insertion entry, puis version, puis rattachement, puis `flush` dans l'ordre exact du DDL, ou insertion native. Test sous `psymate_app`.
- [ ] La timeline, `pg_advisory_xact_lock` et les recherches par `tstzrange` restent natifs.

## 8. Vérifications avant la PR

- [ ] IT existants inchangés et verts (jamais réécrits pour la conversion) ; `./mvnw verify` vert.
- [ ] `ArchitectureTests` vert (aucune règle JPA désactivée) et `ddl-auto=validate` vert sur tous les IT.
- [ ] `make perf` relancé après chaque module, résultats A6 inchangés (étude §6.5).
- [ ] Contrat OpenAPI inchangé.
- [ ] Description de la PR : ce qui est converti, ce qui reste natif, pourquoi, et les dépendances Maven ajoutées (normalement aucune).
