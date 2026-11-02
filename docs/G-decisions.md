# Document G – Décisions prises sur les questions ouvertes

29 octobre 2026 · Version 1.1 (complétée le 1er novembre 2026, puis le 2 novembre 2026) · Ce document tranche les 30 questions de la Partie 1 du runbook F (Q1–Q28) et de E7 (Q29, Q30), complétées ensuite par Q31 (G3), Q32 (G6) et Q33–Q38 (G7, revue d'architecture du 1er novembre 2026). Chaque agent applique ces décisions ; quand un autre document dit autre chose, **G l'emporte**. Les documents A, B, C, D, E et F ont été écrits avant ces décisions : les écarts connus sont listés en G5.

## G1. Identité du produit et des dépôts

| # | Décision |
| --- | --- |
| Q1 | Nom du produit : **PsyMate**. Package Java : **`com.psymate`** (remplace `fr.app`). Domaine : **psymate.com** ; fiches publiques `https://psymate.com/praticiens/{slug}`. Constante i18n `app.name = PsyMate`. |
| Q2 | Compte GitHub **`mrscams`**, dépôts **privés**, pas de licence open source. Backend : **`psymate-api`** (remplace `clinic-api`). Frontend : **`psymate-web`** (remplace `clinic-web`). Transfert vers une organisation `psymate` possible plus tard sans changement de procédure. |
| Q3 | Spring Boot 4.1, Spring Modulith 2.1.2, Java 21. L'agent de M0-01 lit la documentation officielle pour les noms de starters ; le `pom.xml` est validé à la relecture. |
| Q9 | Base locale `psymate`, utilisateur `psymate`, rôle applicatif **`psymate_app`** (remplace `clinic_app`) créé par le script d'init du conteneur ; Flyway fait les `REVOKE UPDATE, DELETE` sur les tables append-only. |

## G2. Comptes, sécurité, e-mails

| # | Décision |
| --- | --- |
| Q4 | Expéditeur en dev (Mailpit) : `test@gmail.com`, clé `app.mail.from`. En production : `noreply@psymate.com`. Fournisseur SMTP réel (Brevo, Postmark, Mailgun…) choisi dans l'étude hébergement (Q16). |
| Q5 | Premier administrateur : `hushan@gmail.com`, valeur par défaut de `ADMIN_EMAIL`. Création par `make admin EMAIL=…` (tâche M7-08) : mot de passe aléatoire de 20 caractères affiché une seule fois dans le terminal, changement obligatoire puis 2FA obligatoire au premier login. Aucun e-mail n'intervient. |
| Q6 | Session de connexion : **12 h**, **30 jours** avec « rester connecté » (fiche 11). |
| Q21 | **Inscription ouverte** (fiche 12). La règle A10 R11 « bêta sur invitation » est abandonnée. Les seuls freins sont la confirmation d'e-mail et les limites de taux. Le déploiement 5 → 20 → 50 praticiens se fait par invitation informelle, sans code d'invitation. |
| Q24 | Limitation de taux avec **Bucket4j**, valeurs actuelles conservées (3/h/e-mail, 5/h/IP contact, 10/h/IP, 60/min/IP annuaire, 600/min/session). On change d'outil seulement s'il pose problème. |

## G3. Modèle clinique et vocabulaires

| # | Décision |
| --- | --- |
| Q7 | Tags de patient, versions de patient, drapeaux en table : **oui aux trois** (E7 O2, O3). |
| Q8 | Fusion d'émotions custom : **oui en V1**. Promotion en émotion globale : V2. |
| Q10 | **Pas d'atelier pilote** avant le code. Les valeurs par défaut de E5 sont livrées telles quelles ; les praticiens pilotes seront invités une fois l'application utilisable. |
| Q11 | Les 29 émotions de E5.2 sont conservées. `shame` reste en famille **disgust** (déjà le cas dans le seed), `calm` reste en famille **trust**. Libellés « Mélancolie » et « Répulsion » conservés. |
| Q12 | Matrice des types de liens E5.3 : conservée. |
| Q13 | Couleurs des huit familles d'émotions : étude couleurs §4, § 6 conservée. |
| Q14 | Justificatifs : **toutes les professions** exigent un **diplôme** et une **pièce d'identité**. L'attestation ADELI/RPPS reste recommandée (psychologue, psychiatre), non bloquante. |
| Q15 | Spécialités et publics de l'annuaire : **liste fermée**, servie par `GET /api/vocabulary/directory`, **modifiable par l'admin** (seed initial en V04 + petit endpoint `/api/admin/vocabulary/directory` dans M1-14b). Pas de saisie libre. |
| Q29 | Journal admin : une seule table `security_event` + vue `admin_journal_v` (E7 O8). |
| Q30 | `patient.clinical_rev` incrémenté par l'application (E7 O10). |
| Q31 | `entry_version` : UPDATE interdit à `psymate_app` sauf `title`/`body`/`payload` de la version 1 dans la fenêtre de grâce, encadré par trigger (M1-03). Fenêtre de grâce : 1 h 15 min (décision du 2026-10-31, remplace les 5 min de A4.8) ; `versioning.grace-period = 75m`. |
| — | Durée de séance par défaut : `sessions.defaultDurationMin = 60` (préférence, fiche 14). |

## G4. Périmètre, bêta, plus tard

| # | Décision |
| --- | --- |
| Q16 | Étude hébergement HDS / chiffrement / sauvegardes / RGPD : **plus tard**, après le backend V1. M7-06 produit le document de passation. |
| Q17 | Stockage des justificatifs en bêta : décidé avec Q16. En dev : disque local chiffré (`StorageService`, M1-13). |
| Q18 | Export du compte refusé (fiche 27) : **oui**, extension de M6-05 avec la table `export_job` (E7 O12), lien valable 7 jours, format = l'export patient de M6-05 pour chaque patient + liste des patients. |
| Q19 | Mineurs : **rien de spécial en V1**. La date de naissance et le public « enfants » sont informatifs. À revoir avec l'étude RGPD. |
| Q20 | Numéros d'urgence (3114 / 15) dans le pied de page public : **non**. |
| Q22 | Patient DEMO : généré par M7-01 (18 mois, 20 séances), marqué « DEMO », relu par le propriétaire du projet. |
| Q23 | **Français d'abord.** La structure i18n (`messages_fr/en.properties`, `messages.fr/en.xlf`) est conservée pour ajouter des langues sans changement de code ; les fichiers EN peuvent être en retard, rien n'est bloqué sur l'anglais. |
| Q25 | Agenda Google (fiches 15, 15b) : **hors V1**. Pas de jalon « Agenda » (l'ancien M8) ; le module `scheduling` n'est pas créé ; les fiches 15 et 15b sont marquées « plus tard ». L'identifiant M8 a été réutilisé pour la migration JPA (Q32, plan C8). |
| Q26 | Signalements de l'annuaire (fiche 23) : **V1.1**. DDL prêt en E6.1. |
| Q27 | **M1-14b créée** : liste de tous les comptes, suspension / réactivation manuelle, journal paginé (vue `admin_journal_v`), migration `V10__admin_journal.sql`. S'insère après M1-14, en parallèle du lot 6. |
| Q28 | Recours du compte suspendu (fiche 27) : dans **M1-14b** (table `practitioner_appeal` déjà créée par V03). |

## G5. Écarts à reporter dans les autres documents

| Document | À changer |
| --- | --- |
| A, C, D, E, F, design/* | `clinic-api` → `psymate-api`, `clinic-web` → `psymate-web`, `fr.app` → `com.psymate`, `clinic_app` → `psymate_app`, « Suivi » → « PsyMate ». Reporté le 29 octobre 2026 dans A, C, D, E, F, 00-contexte et les fiches design concernées (README, 09, 10, 11, 12, 13, 14, 15, 15b, 17, 21, 22, 23, 24, 27). B, l'étude couleurs et les fiches 01, 02, 04, 06, 07, 16, 20, 25, 26 ne contenaient rien à changer. |
| A10 R11 | « Bêta sur invitation » → inscription ouverte (Q21). |
| A4.10, C M5-01 | Vocabulaire de l'annuaire fermé et administrable (Q15). |
| C, D | Jalon M8 / module `scheduling` retirés (Q25). Tâche M1-14b ajoutée (Q27, Q28). M6-05 étendue avec `export_job` (Q18). |
| E5.2 | Aucun changement (Q11) : le seed est livré tel quel. |
| F | Mis à jour le 29 octobre : noms, package, rôle, et renvoi à ce document. Le runbook frontend s'appellera **document H**. |
| A (structure d'un module) | `repository/` : JPA (Spring Data) pour le CRUD et fragments `*NativeQueries` pour le SQL natif des modules JPA, `JdbcClient` pour les modules de projection (Q32, précisée par Q38). Arbre complet par rôle : Q33, reporté en A3 le 2 novembre 2026. |
| A4.8 | Règle de grâce : 75 min (Q31), reporté le 2 novembre 2026. Suppression physique : périmètre de Q37. |
| A3, A5, CLAUDE.md | Lecture SQL des projections (Q34), structure par rôle (Q33), un fichier par DTO (Q35), suppression physique (Q37), SQL natif (Q38) : reportés le 2 novembre 2026. |
| C | Section C8 ajoutée le 2 novembre 2026 : tâches M8-01 à M8-06 (migration JPA, Q32) et M9-01 à M9-20 (restructuration, Q33–Q36). |

## G6. Décisions techniques

| # | Décision |
| --- | --- |
| Q32 | 2026-11-01 : Accès aux données : **JPA** (Spring Data JPA / Hibernate 7.4.13.Final) pour les modules CRUD (patients, vocabulary, directory, identity, clinical.export), **`JdbcClient`** pour les projections et requêtes (search, overview, graphe, timeline, keyset admin). Gardes : `@Immutable` sur les tables append-only (`security_event`, `patient_version`, `verification_decision`, `document_access_log`), `@DynamicUpdate` ou `@Immutable` sur `entry_version`, aucune association JPA inter-modules (UUID nus), jamais d'entité dans un DTO ni dans `web` (devenu « contrôleur » avec Q33), `open-in-view=false`, `ddl-auto=none` (`validate` en test), Flyway seul maître du DDL, colonnes trigger/générées non modifiables. Migration module par module (M8-02..06), une PR par module. Référence : `docs/etudes/etude-jpa.md`, liste de contrôle `docs/etudes/jpa-conversion-checklist.md`. **Précisée par Q38** (2 novembre 2026) : dans un module JPA, le SQL natif (dont le keyset admin) passe par des fragments Spring Data, pas par `JdbcClient`. |

## G7. Architecture interne et règles révisées (2 novembre 2026)

Décisions prises sur la revue d'architecture du 1er novembre 2026 (remises en cause R1 à R6, questions Q6 et Q7 de la revue). Elles s'appliquent par les tâches M9 du plan (C8).

| # | Décision |
| --- | --- |
| Q33 | **Structure d'un module par rôle.** Chaque module a un package racine, et à l'intérieur un dossier par rôle : `api` (façades, vues, événements, exceptions publiques), `controller` (contrôleurs, gestionnaires d'erreurs), `dto` (DTO, requêtes, mappers et assemblers), `service` (services, implémentations de façades et de ports, listeners, jobs), `repository` (repositories Spring Data, fragments natifs, requêtes `JdbcClient`), `model` (entités, records métier, règles, exceptions métier), `config` (configuration, filtres, propriétés). Le suffixe d'une classe donne son dossier. Un module ou un sous-domaine de moins de 8 classes reste plat. Sous-domaines admis : identity (`auth`, `admin`, `verification`, `preferences`) et clinical (`export`). Remplace l'arbre par couche (`domain / application / persistence / web`) de A3. Les classes internes sont **publiques entre dossiers d'un même module** (remplace « tout le reste en package-private » de A3) : la frontière entre modules est vérifiée par Modulith, celle entre dossiers par ArchUnit. Déploiement module par module (M9-07 à M9-18). |
| Q34 | **Lecture SQL des projections.** Un module n'**écrit** jamais dans les tables d'un autre. Seuls les modules de projection déclarés (search, overview) **lisent** en SQL les tables d'autres modules, en lecture seule, sur une liste tenue par un test d'appartenance des tables (M9-02). Aucun module ne référence les classes internes d'un autre. Remplace « aucun module ne lit les tables d'un autre » de A3 et de CLAUDE.md. |
| Q35 | **Un fichier par DTO.** Plus de classes conteneurs `*Dtos` : chaque DTO ou requête est son propre fichier dans `dto/`. Les conteneurs existants sont éclatés à la tâche de restructuration de leur module. |
| Q36 | **Calendrier.** Tout le plan de restructuration (M9-01 à M9-20) est lancé maintenant, avant la V1 web. Pendant la restructuration d'un module, aucune tâche backend fonctionnelle ne touche ce module. M9-02 (motifs `**` des tranches d'IT) est fusionnée avant le premier déplacement de fichier. |
| Q37 | **Suppression physique.** Aucune donnée clinique n'est supprimée physiquement : `patient`, `entry`, `entry_version`, `link` et les tables de détail courantes. Peuvent l'être : les tables d'association (`entry_tag`, `patient_tag`), les purges planifiées (`contact_request`, `spring_session`, fichiers d'export et justificatifs, dont les lignes restent) et les sessions. Chaque table concernée est listée en E (« Suppressions physiques autorisées »). Précise la règle « rien n'est supprimé physiquement » de CLAUDE.md et A4.8. |
| Q38 | **SQL natif dans un module JPA.** Il passe par des fragments Spring Data (`*NativeQueries` + `EntityManager`), pour garder une seule unité de travail par module ; un `UPDATE` natif suivi d'une lecture JPA impose un `refresh`. `JdbcClient` est réservé aux modules et sous-domaines non JPA : search, overview, graphe et timeline (linking, clinical), et la recherche publique de l'annuaire (directory). Précise Q32, dont le « keyset admin » reste en `EntityManager` natif. |
