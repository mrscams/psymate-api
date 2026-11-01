# Passation pour l'étude hébergement (M7-06)

## Objet

Ce document prépare l'étude séparée « hébergement HDS, chiffrement, sauvegardes, RGPD » décidée en G Q16. Il n'implémente rien. Il inventorie les décisions que l'étude doit prendre, indique pour chacune le point d'entrée exact dans le code (clé de configuration, interface, table, classe), distingue ce qui est déjà en place de ce qui manque, liste les secrets à provisionner et propose une checklist de mise en production.

Références : A « Plus tard (hors périmètre) », A9 R7, A10 R12 ; C2 ligne M7-06 ; E2 (secrets), E7 O9 et O12 ; G2 Q4, G4 Q16, Q17, Q19. Procédure du premier administrateur : `docs/ops/admin.md` (M7-08), non reprise ici.

État du code inventorié : `main` à `71b4fcf` (backend V1 complet, 33 tâches fusionnées). Les chemins sont relatifs à la racine du dépôt ; les chemins Java omettent le préfixe `src/main/java/com/psymate/`. Les migrations sont dans `src/main/resources/db/migration/`.

Conventions : aucune valeur réelle dans ce document. Les valeurs à fournir sont notées `<à définir>`. Les états sont **Fait**, **Partiel**, **Manquant** ou **À décider**.

## 1. Le code en une page

Ce que la V1 pose déjà, et ce qu'elle ne pose pas. Les sections suivantes détaillent chaque ligne.

| Sujet | Déjà fait | Ce qui manque |
| --- | --- | --- |
| Mots de passe | Argon2id (sel 16, hash 32, 19 MiB, 2 itérations), politique 12 caractères + liste de mots de passe courants, hachage factice anti-chronométrage (`identity/auth/SecurityConfig.java`, `identity/auth/PasswordPolicy.java`) | Rien de bloquant ; paramètres Argon2 au minimum OWASP, à réévaluer avec la machine cible |
| Chiffrement applicatif | AES-256-GCM, IV 12 octets aléatoire, tag 128 bits, AAD liée au contexte : justificatifs et zips d'export (`identity/verification/DocumentCipher.java`), secrets TOTP (`identity/auth/TotpSecretCipher.java`) | Aucune version de clé ni identifiant de clé dans les blobs ; une seule clé maîtresse brute partagée ; pas de rotation. Données cliniques en clair en base |
| URL signées | HMAC-SHA256 avec clé dérivée, comparaison en temps constant ; justificatifs 5 min, fichiers d'export 7 j | Le téléchargement d'export repose sur la seule URL, route `permitAll` |
| Purges | Justificatifs 90 j, exports 7 j, `contact_request` 30 j, `event_publication` complétées 30 j (section 2.5) | Aucune rétention sur `security_event` ni `auth_token` ; `practitioner.purge_after` posé mais jamais exécuté |
| Append-only | `REVOKE UPDATE, DELETE` pour `psymate_app` sur `security_event`, `patient_version`, `verification_decision`, `document_access_log`, `entry_version` (grâce par trigger) | Les `REVOKE` ne s'appliquent qu'au rôle `psymate_app` ; en local l'application se connecte en propriétaire |
| Limitation de débit | Bucket4j, règles `app.rate-limits.rules.*` (60/min annuaire, 5/h contact, 600/min session), IPv6 en /64, 429 RFC 9457 | En mémoire, mono-instance ; pas de règle IP sur l'inscription et le login (verrou e-mail+IP seulement) |
| Filtres | Chemins décodés partout (`identity/auth/RequestPaths.java`), 2FA obligatoire sur `/api/admin/**`, blocage `email_pending` | CSRF désactivé (JSON + SameSite=Lax), pas de CSP ni HSTS explicite, pas de CORS |
| Audit | `security_event` append-only ; `shared/audit/AuditFilter.java` trace `patient.read` et `patient.write` sur patients, entrées, liens, séances ; `document_access_log` à chaque lecture de justificatif ; vue `admin_journal_v` | Liste et création de patients, recherche globale et `/api/me/export*` non audités ; `export_requested` jamais écrit ; échec d'écriture d'audit seulement journalisé |
| Sessions | Spring Session JDBC, 12 h ou 30 j, cookie HttpOnly + Secure + SameSite=Lax, anti-fixation, révocation par praticien | Principal sérialisé en Java dans `spring_session_attributes` (contient l'e-mail) ; nettoyage des sessions expirées laissé au défaut du framework |
| Événements | Outbox `event_publication`, rejeu filtré au démarrage, déduplication par verrou advisory, purge 30 j | Pas de verrou multi-instance sur les tâches planifiées |
| Exploitation | Compose local (pgvector/pg16, Mailpit), CI `verify` + contrat, rôle `psymate_app` par script d'init, `tools/tag-contract.sh` | Pas de Dockerfile, pas de profil de production, pas de déploiement, pas de scan de dépendances, pas de configuration de journaux, `/actuator/health`, `/actuator/info` et `/v3/api-docs` publics |

## 2. Décisions attendues de l'étude

Chaque sous-section donne le contexte, puis un tableau « décision · options · point d'entrée code · état ».

### 2.1 Hébergement et infrastructure

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D1 Hébergeur certifié HDS (hébergement de données de santé), région, contrat de sous-traitance RGPD | Hébergeur HDS français avec PostgreSQL managé · HDS avec machines virtuelles et PostgreSQL auto-géré · cloud généraliste certifié HDS | Aucun : le dépôt ne contient ni Dockerfile ni configuration de déploiement. `docker-compose.yml` ne sert qu'au poste local (image `pgvector/pgvector:pg16`, Mailpit). Extensions PostgreSQL exigées par `V01__init.sql` : `pgcrypto`, `pg_trgm`, `unaccent`, `vector`, `btree_gist` : à vérifier chez l'hébergeur | À décider |
| D2 Reverse proxy, TLS, en-têtes `X-Forwarded-*` | Proxy de l'hébergeur · Nginx/Caddy devant l'application · exposition directe | `server.forward-headers-strategy` absent d'`application.yml`. 22 appels à `HttpServletRequest.getRemoteAddr()` (`shared/ratelimit/RateLimitFilter.java`, `shared/audit/AuditFilter.java`, `identity/auth/AuthController.java`, `identity/auth/TwoFactorController.java`, `identity/verification/VerificationController.java`, `identity/admin/AdminVerificationController.java`, `identity/admin/AdminPractitionerController.java`, `identity/admin/AppealController.java`, `clinical/export/AccountExportController.java`, `directory/PublicContactController.java`). Le cookie de session est `Secure=true` inconditionnel (`identity/auth/RememberMeCookieSerializer.java`) : TLS obligatoire jusqu'à l'application ou terminaison sur un proxy de confiance | Manquant |
| D3 Nombre d'instances et haute disponibilité | Une instance (état actuel du code) · plusieurs instances derrière un équilibreur | Voir 2.6 : tout l'état volatil est en mémoire JVM | À décider |
| D4 Rôles PostgreSQL et séparation migrations / application | Deux rôles (`psymate` propriétaire pour Flyway, `psymate_app` pour l'application) · un seul rôle | `docker/postgres/init/01-app-role.sql` crée `psymate_app` et pose les privilèges par défaut (local seulement, mot de passe de développement en dur). En local, `application.yml` connecte l'application en propriétaire : les `REVOKE` sont donc inopérants hors tests (`src/test/java/com/psymate/clinical/GraceRuleAppRoleIT.java` montre la configuration attendue : `spring.datasource.username=psymate_app` et `spring.flyway.user=psymate`). Aucune clé `spring.flyway.user`/`password` dans le yml | Partiel |
| D5 Image et chaîne de déploiement | Image OCI par `spring-boot:build-image` (plugin déclaré sans configuration dans `pom.xml`) · Dockerfile · jar sur machine virtuelle | `.github/workflows/ci.yml` : jobs `verify` et `contract` seulement. Aucun profil `prod` : les profils existants sont `local` (défaut via `spring.profiles.default`), `test` et `cli`. Hors `local` et `test`, `app.crypto.key`, `app.storage.dir`, `app.directory.storage-dir` et `app.mail.from` n'ont pas de valeur : l'application refuse de démarrer si l'environnement ne les fournit pas (voulu). Mais sans profil actif, le défaut `local` démarre sur la base locale et la clé de développement sans erreur | Manquant |

Point d'attention D5 : `app.web.base-url` a une valeur par défaut localhost dans tous les profils (`application.yml`). Un oubli en production produit des liens d'e-mail (confirmation, mot de passe oublié) qui pointent vers le poste de développement, sans erreur au démarrage. L'étude devrait demander un profil `prod` qui supprime ce défaut, ou une vérification au démarrage.

### 2.2 Chiffrement, clés, sauvegardes

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D6 Chiffrement au repos de la base | Chiffrement du volume par l'hébergeur (transparent) · chiffrement de colonnes applicatif pour les données cliniques · les deux | Les données cliniques sont en clair : `patient` (`V01__init.sql` : `first_name`, `last_name`, `birth_date`, `phone`, `email`, `context`, `follow_up`), `entry_version.title/body/payload` (`V05__clinical.sql`), `patient_overview.payload` (`V09`). Index de recherche sur ces colonnes : trigramme sur `display_name` (`V01`), GIN trigramme et `tsvector` sur les entrées (`V05`, `V08`). Un chiffrement de colonne casserait la recherche (A6) : il faudrait le limiter aux colonnes non indexées. `pgcrypto` est installée et inutilisée. Seuls justificatifs, zips d'export et `practitioner.totp_secret_enc` sont chiffrés par l'application | À décider |
| D7 Gestion de la clé `app.crypto.key` : coffre, rotation, version de clé | Variable d'environnement injectée par le coffre de l'hébergeur · KMS avec enveloppe (clé de données chiffrée par le KMS) · HSM | Clé AES-256 en base64 (32 octets), lue par `identity/verification/DocumentCipher.java`, `identity/auth/TotpSecretCipher.java` et `directory/IpHasher.java` ; longueur validée au démarrage. **Le format chiffré n'a pas de version de clé** : blob = IV (12) + chiffré + tag, sans octet de version ni identifiant de clé. Changer la clé rend illisibles tous les justificatifs non purgés, tous les exports non expirés et tous les secrets TOTP (les praticiens perdraient leur 2FA). À prévoir avant toute rotation : un préfixe de version dans le blob et un chiffrement à double clé pendant la transition. La clé AES est la clé maîtresse brute, partagée par les deux chiffreurs (séparation par AAD seulement) ; seule la clé de signature des URL est dérivée (`HMAC-SHA256(clé, "psymate-signed-url")`) ; `IpHasher` réutilise la clé brute comme clé HMAC du sel quotidien. Valeur de développement en dur dans `application.yml` (profil `local`, placeholder `APP_CRYPTO_KEY`) : rien n'interdit cette clé en production | Partiel |
| D8 Sauvegardes : fréquence, chiffrement, lieu, test de restauration | Sauvegarde managée de l'hébergeur (instantanés + WAL) · `pg_dump` planifié chiffré · les deux | Aucun code. Périmètre à couvrir : la base PostgreSQL **et** le dossier `app.storage.dir` (justificatifs et exports chiffrés, `identity/verification/LocalStorageService.java`) **et** `app.directory.storage-dir` (photos, `directory/LocalPhotoStorage.java`). Une sauvegarde de la base sans la clé `app.crypto.key` ne permet pas de relire les fichiers ni les secrets TOTP : la clé doit être sauvegardée séparément, dans le coffre | Manquant |
| D9 RPO et RTO | RPO 24 h / RTO 1 jour ouvré (bêta) · RPO 1 h / RTO 4 h · continu | Aucun code. Contraintes connues : l'outbox `event_publication` rejoue au démarrage les publications incomplètes de plus de `app.events.republish-older-than` (`events/EventRepublisher.java`), ce qui tolère un arrêt brutal ; les jobs d'export `queued`/`running` de plus d'1 h sont requalifiés `failed` (`clinical/export/ExportJobService.java`) ; aucun rattrapage des purges si l'instance est arrêtée à l'heure du cron | À décider |
| D10 Chiffrement en transit interne (application vers base, vers stockage, vers SMTP) | TLS imposé par l'hébergeur · `sslmode=require` dans l'URL JDBC · réseau privé seul | `spring.datasource.url` n'existe qu'en profil `local` (sans TLS). `spring.mail` ne définit que `host` et `port` (placeholders `MAIL_HOST`, `MAIL_PORT`), sans `starttls` ni identifiants | Manquant |

### 2.3 Stockage des fichiers (justificatifs, exports, photos)

Décision reportée par G Q17 et E7 O9. Le code est prêt à changer d'implémentation.

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D11 Stockage des justificatifs et des exports en bêta | Stockage objet S3 de l'hébergeur HDS · MinIO auto-hébergé · disque persistant chiffré (état actuel) | Interface `identity/verification/StorageService.java` : `put(key, byte[])`, `get(key)`, `delete(key)`, `exists(key)`. Seule implémentation : `identity/verification/LocalStorageService.java` (`app.storage.dir`, anti-traversée). Façade inter-modules `identity/api/EncryptedStorageFacade.java` (`put`, `get`, `delete`, `exists`, `sign`), implémentée par `identity/verification/EncryptedStorageFacadeImpl.java` : chiffre avec `DocumentCipher` (AAD = clé de stockage) puis délègue à `StorageService`. Clés : `verification/{practitionerId}/{docId}.bin` (`identity/verification/VerificationService.java`), `exports/{jobId}.zip` (`clinical/export/ExportJobRunner.java`). Une implémentation S3 de `StorageService` suffit ; limite : l'interface manipule des `byte[]` complets et le zip d'export est construit en mémoire (`ExportJobRunner.buildZip`) | Partiel |
| D12 Photos de profil | Même stockage objet · CDN · rester local | `directory/PhotoStorage.java` (package-private : `store`, `read`, `delete`), `directory/LocalPhotoStorage.java` sous `app.directory.storage-dir`, clé `profiles/{practitionerId}/{uuid}.jpg`, **non chiffré**. Aucune photo n'est servie en V1 (`directory/PublicProfileMapper.java` renvoie une URL nulle ; `PhotoStorage.read` n'a aucun appelant) | Partiel |
| D13 Rétention des fichiers | Garder les valeurs actuelles · ajuster | Justificatifs : 90 j après décision (`identity/admin/AdminVerificationService.java`, `DOCUMENT_RETENTION`), colonnes `verification_document.purge_after/purged_at` (`V03__verification.sql`), purge `identity/verification/PurgeJob.java` cron `0 15 3 * * *` UTC. Exports : 7 j (`clinical/export/ExportJobService.java`, `RETENTION`), table `export_job` (`V11__export_job.sql`), purge `clinical/export/ExportPurgeJob.java` cron `0 45 3 * * *` UTC. Dans les deux cas le fichier est supprimé, la ligne reste | Fait |

### 2.4 Courrier électronique

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D14 Fournisseur SMTP pour l'expéditeur de production `noreply@psymate.com` (G Q4) | Brevo · Postmark · Mailgun · relais SMTP de l'hébergeur HDS | Clé `app.mail.from` lue par `identity/verification/VerificationMailer.java`, `identity/auth/AuthMailer.java`, `identity/admin/AdminMailer.java`, `directory/ContactMailer.java` ; valeur seulement en profils `local` et `test`. Transport : `spring.mail.host`/`port` (`MAIL_HOST`, `MAIL_PORT`), sans identifiants ni TLS. Nom du produit : `app.name`. Liens des e-mails : `app.web.base-url` (`APP_WEB_BASE_URL`). Contenu des e-mails : confirmation, mot de passe oublié, décisions de vérification, rappels admins (`identity/admin/OverdueReminderJob.java`, cron `0 0 7 * * *` UTC), relais des demandes de contact de l'annuaire (le message du visiteur transite par le fournisseur) | À décider |
| D15 Authentification du domaine (SPF, DKIM, DMARC) et traitement des rebonds | Configuration DNS chez le fournisseur · sous-domaine d'envoi dédié | Aucun code (hors dépôt). L'indicateur de santé `management.health.mail` est actif hors profil `test` : la santé publiée par `/actuator/health` dépendra de la joignabilité SMTP | Manquant |

### 2.5 Journaux, audit et rétention

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D16 Rétention de `security_event` | Conservation illimitée (état actuel) · archivage externe au-delà de N mois · purge à N mois par un rôle dédié | Table `security_event` (`V01__init.sql` : `practitioner_id`, `actor_id`, `kind`, `ip inet`, `user_agent`, `details jsonb`, `occurred_at`), `REVOKE UPDATE, DELETE` pour `psymate_app` (`V01`). Écrite par `identity/auth` (login, verrou, 2FA, mot de passe), `identity/admin` (décisions, lectures de justificatifs), `shared/audit/AuditWriter.java` (`patient.read`, `patient.write`). Aucune purge : toute purge devra passer par le rôle propriétaire. Les `ip` et `user_agent` sont des données personnelles : la durée de conservation est à justifier dans le registre | À décider |
| D17 Rétention de `document_access_log` | Idem | Table `document_access_log` (`V03__verification.sql` : `document_id`, `admin_id`, `ip`, `accessed_at`), `REVOKE UPDATE, DELETE`. Écrite à chaque lecture (`identity/verification/VerificationService.java`, `identity/admin/AdminVerificationService.java`). Remarque : la colonne `admin_id` reçoit aussi l'identifiant du praticien quand il relit son propre document | À décider |
| D18 Rétention de `event_publication` et `auth_token` | Garder 30 j (événements) · purger `auth_token` consommés ou expirés | `event_publication` (`V01`) : purge des publications complétées après `app.events.completed-retention` (30 j) par `events/EventPublicationPurgeJob.java` cron `0 30 3 * * *` UTC ; les événements ne portent que des identifiants. `auth_token` (`V01` : `token_hash` SHA-256, `expires_at`, `consumed_at`, `created_ip`) : aucune purge | Partiel |
| D19 Journaux applicatifs (stdout) : collecte, masquage, durée | Collecte par l'hébergeur · agrégateur dédié | Aucune configuration logback/log4j2 : sortie console par défaut de Spring Boot, sans masquage. `shared/problem/GlobalExceptionHandler.java` journalise méthode, URI et trace sur 500. Aucun e-mail ni mot de passe journalisé par le code | Manquant |
| D20 Couverture de l'audit réglementaire | Compléter `AuditFilter` · laisser à l'étude | `shared/audit/AuditFilter.java` : `/api/patients/{id}/**`, `/api/entries/{id}/**`, `/api/links/**`, `/api/sessions/{id}/**` ; non couverts : liste et création de patients, recherche globale, `/api/me/export*`. Le kind `export_requested` est cité en commentaire de `V01` mais jamais écrit. Écriture asynchrone (`shared/audit/AuditWriter.java`, file de 10 000, échec en WARN) : une panne d'INSERT perd des traces sans bloquer la requête | Partiel |

### 2.6 Multi-instance et état en mémoire

Le code est explicitement mono-instance. Si l'étude retient plusieurs instances (D3), chaque ligne ci-dessous devient un chantier.

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D21 Limiteurs de débit | Rester mono-instance · Bucket4j distribué (Redis, PostgreSQL) · limites au proxy | `shared/ratelimit/RateLimiter.java` : seaux dans un `ConcurrentHashMap` par règle, borne 50 000 clés puis seau commun, nettoyage 60 s ; règles `app.rate-limits.rules.*` (`shared/ratelimit/RateLimitProperties.java`) | Fait mono-instance |
| D22 Verrou de connexion | Idem · s'appuyer sur `practitioner.locked_until` seul | `identity/auth/LoginAttemptLimiter.java` : 5 échecs, fenêtre 15 min, verrou 15 min, clé e-mail + `getRemoteAddr` brut (sans /64), en mémoire. Reflet en base : `practitioner.failed_logins`, `locked_until` (`V01`). Une IP peut verrouiller le compte pour toutes les IP | Fait mono-instance |
| D23 Anti-rejeu TOTP | Idem · stocker le dernier pas en base | `identity/auth/OtpVerifier.java` : `Map<UUID, Long> lastStep` en mémoire, perdu au redémarrage | Fait mono-instance |
| D24 Tâches planifiées | Une seule instance exécute les jobs · ShedLock ou verrou advisory · ordonnanceur externe | Aucun `@EnableScheduling`, aucun ShedLock. Cinq `ThreadPoolTaskScheduler` privés : `identity/verification/PurgeJob.java`, `directory/ContactPurgeJob.java`, `events/EventPublicationPurgeJob.java`, `clinical/export/ExportPurgeJob.java`, `identity/admin/OverdueReminderJob.java`. Les purges sont idempotentes ; le rappel aux admins partirait en double | Manquant |
| D25 Files de travail en mémoire | Accepter la perte à l'arrêt · persister | Export : 1 thread, file de 50 (`clinical/export/ExportJobService.java`) ; audit : file de 10 000 (`shared/audit/AuditWriter.java`) ; relais de contact : 2 threads, file de 200 (`directory/ContactRequestService.java`) ; mails asynchrones (`identity/admin/AdminAsyncMail.java`, exécuteurs des services). Un arrêt brutal perd le contenu des files | À décider |

### 2.7 RGPD

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D26 Registre des traitements, DPO, base légale, information des patients | DPO externe mutualisé · DPO interne · pas de DPO (à vérifier au regard des données de santé à grande échelle) | Catégories de données à inscrire : `patient` et `patient_version` (identité, contact, `context`, `follow_up`), `entry_version` (texte clinique), `practitioner` (identité professionnelle, `totp_secret_enc`), `verification_document` (diplômes, pièces d'identité), `contact_request` (`sender_name`, `sender_email`, `message` en clair, `ip_hash`), `security_event` et `document_access_log` (`ip`, `user_agent`), `spring_session_attributes` (principal sérialisé avec e-mail) | Manquant |
| D27 Droit d'accès et portabilité | Export existant MD/PDF · ajouter un format structuré (JSON) | Export patient `clinical/export/PatientExportController.java` (`GET /api/patients/{id}/export?format=md\|pdf`, entrées privées et archivées exclues). Export du compte `clinical/export/AccountExportController.java` + `ExportJobRunner` (zip : `patients.md` + un `.md` et un `.pdf` par patient). Aucun format lisible par machine. Le droit d'accès du **patient** passe par le praticien (aucun compte patient en V1, A11) | Partiel |
| D28 Droit d'effacement face à l'append-only | Anonymisation en place par le rôle propriétaire · export puis suppression physique · refus motivé (dossier médical, conservation légale) | **Aucun mécanisme d'effacement ni d'anonymisation** de patient ou de praticien. `DELETE /api/patients/{id}` archive (`archived_at`, `patients/PatientController.java`) ; les entrées sont archivées, jamais supprimées (E2). Tables append-only protégées par `REVOKE` : `entry_version`, `patient_version`, `security_event`, `verification_decision`, `document_access_log`. Toute opération devra passer par le rôle propriétaire `psymate`, hors application, ou par une procédure SQL dédiée à concevoir | Manquant |
| D29 Durée de conservation après clôture | Dossier patient : 20 ans après le dernier contact (code de la santé publique, à confirmer par l'étude pour les psychologues et coachs non soignants) · praticien refusé : 30 j · compte fermé : à définir | `patient.archived_at` (`V01`) sans échéance. `practitioner.purge_after` posé à +30 j au refus (`identity/admin/AdminVerificationService.java`, `REJECTED_RETENTION` ; `identity/admin/AdminVerificationRepository.java`) mais **aucun traitement ne le lit** : seuls les fichiers de justificatifs sont purgés à 90 j. Le statut `closed` n'est jamais atteint par le code | Manquant |
| D30 Mineurs et consentement des représentants légaux (G Q19) | Champ « représentant légal » et trace du consentement · mention dans les conditions, sans donnée · rien en V1 | Aucune colonne ni logique « mineur ». `patient.birth_date` sert à la phase de vie (`clinical/domain/TimeSpan.java`), à l'âge dans l'export et à la vue d'ensemble. Le public « enfants » n'existe que comme code `children` du vocabulaire de l'annuaire (`V04__directory_profile.sql`, `practitioner_profile.audiences`), à titre informatif | À décider |
| D31 Sous-traitants et transferts hors UE | Hébergeur, fournisseur SMTP, stockage objet : tous en UE · clauses contractuelles types | Aucun appel externe dans le code hormis SMTP. Les demandes de contact de l'annuaire (`directory/ContactMailer.java`) et les e-mails de décision font transiter des données personnelles par le fournisseur SMTP | À décider |

### 2.8 Modèle de menaces

L'étude doit produire un modèle de menaces. Le code fournit les surfaces suivantes.

| Décision | Options | Point d'entrée code | État |
| --- | --- | --- | --- |
| D32 Surfaces publiques non authentifiées | Fermer `/actuator/**` et `/v3/api-docs` au réseau · les mettre derrière le proxy · les laisser | Chaîne principale `identity/auth/SecurityConfig.java` : `/api/**` authentifié, `/api/admin/**` avec rôle admin + 2FA (`identity/auth/AdminTwoFactorFilter.java`), **`anyRequest().permitAll()`** : `/actuator/health`, `/actuator/info` (`management.endpoints.web.exposure.include: health,info`) et `/v3/api-docs` (`springdoc.api-docs.path`) sont publics. Chaîne publique `directory/PublicSecurityConfig.java` : `/api/public/**` sans session, avec limites IP | Partiel |
| D33 Export du compte par URL signée longue durée | Garder 7 j · raccourcir · exiger une session | `/api/me/export` et `/api/me/export/**` sont `permitAll` (`identity/auth/SecurityConfig.java`) ; le fichier se télécharge avec `?exp=&sig=` valable 7 j, sans session (`clinical/export/AccountExportController.java`). Le jeton de statut est un porteur sans expiration. Hors `AuditFilter` et hors règle de débit `session` | À décider |
| D34 En-têtes de sécurité, CSRF, CORS | HSTS et CSP au proxy · dans Spring Security | CSRF désactivé (JSON + SameSite=Lax, `identity/auth/SecurityConfig.java`). Aucune CSP, aucun HSTS explicite, aucune configuration CORS dans `src/main`. `X-Content-Type-Options: nosniff` posé à la main sur les téléchargements de justificatifs | Partiel |
| D35 Limitation à l'inscription et au login | Règle IP `app.rate-limits.rules.*` · au proxy | Inscription ouverte (G Q21) : `/api/auth/register` et `/api/auth/login` n'ont que le verrou e-mail+IP (D22) et les limites de renvoi d'e-mail sur `auth_token` (1/min, 5/j). Une règle Bucket4j s'ajoute par configuration seule | Partiel |
| D36 Chaîne logicielle | Scan de dépendances (Dependabot, OWASP) · SBOM · signature d'image | `.github/workflows/ci.yml` : aucun scan. Dépendances figées dans `pom.xml` (Bouncy Castle `bcprov-jdk18on` pour Argon2, Bucket4j, openhtmltopdf) | Manquant |

Acteurs à considérer dans le modèle : visiteur anonyme de l'annuaire (limites IP, honeypot et délai minimal dans `directory/ContactRequestService.java`), praticien authentifié (isolation par `tenant_id` et `PatientOwnershipGuard`, ADR-09), praticien suspendu ou refusé (`identity/auth/AppealAuthenticator.java`, export du compte), administrateur (2FA obligatoire, journal `admin_journal_v` de `V10__admin_journal.sql`), exploitant de l'hébergeur (données cliniques en clair en base, D6), attaquant disposant d'une sauvegarde (D8 : base + fichiers + clé).

## 3. Secrets et variables à provisionner par environnement

Les noms sont ceux du relaxed binding de Spring Boot (`app.mail.from` se fournit comme `APP_MAIL_FROM`). Seuls `APP_CRYPTO_KEY`, `APP_WEB_BASE_URL`, `MAIL_HOST` et `MAIL_PORT` sont explicitement référencés comme placeholders dans `application.yml` ; les autres sont des clés sans valeur hors profils `local`/`test`, ou des propriétés Spring standard. Aucune valeur n'est donnée ici.

| Variable | Clé Spring | Format | Qui la génère | Rotation | Impact d'une erreur |
| --- | --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | nom du profil de production `<à définir>` | Exploitant | Jamais | Sans valeur, le défaut `local` démarre sur la base locale et la clé de développement |
| `APP_CRYPTO_KEY` | `app.crypto.key` | base64 de 32 octets aléatoires (AES-256) | Exploitant, depuis le coffre de l'hébergeur ou un KMS ; jamais un développeur | **Impossible sans migration** tant que le blob n'a pas de version de clé (D7). Le jour venu : double clé, rechiffrement des `verification_document`, `export_job` et `totp_secret_enc` | Perte : justificatifs, exports et 2FA illisibles. Fuite : lecture de tous les fichiers, forge des URL signées |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | URL JDBC PostgreSQL avec TLS (`sslmode` `<à définir>`) | Hébergeur | À chaque changement d'hôte | Démarrage impossible |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.username` / `password` | rôle applicatif `psymate_app` (D4) | Hébergeur (le rôle) ; mot de passe généré par l'exploitant | Périodique, à définir ; sans arrêt si l'hébergeur accepte deux mots de passe | En propriétaire par erreur : les `REVOKE` append-only sont inopérants |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | `spring.flyway.user` / `password` | rôle propriétaire `psymate` (migrations) | Hébergeur / exploitant | Périodique | Sans ces clés, Flyway migre avec le rôle applicatif, qui ne possède pas les tables |
| `APP_STORAGE_DIR` | `app.storage.dir` | chemin absolu d'un volume persistant, sauvegardé | Exploitant | Non | Perte du volume : justificatifs et exports perdus (D11). Remplacé par les identifiants S3 si D11 retient le stockage objet |
| `APP_DIRECTORY_STORAGE_DIR` | `app.directory.storage-dir` | chemin absolu d'un volume persistant | Exploitant | Non | Perte des photos (D12) |
| `APP_WEB_BASE_URL` | `app.web.base-url` | origine HTTPS du frontend `<à définir>` | Produit | À chaque changement de domaine | Liens d'e-mail vers le poste de développement, sans erreur |
| `APP_MAIL_FROM` | `app.mail.from` | adresse d'expéditeur (`noreply@psymate.com` selon G Q4) | Produit | Non | Démarrage impossible si absente hors `local`/`test` |
| `MAIL_HOST` / `MAIL_PORT` | `spring.mail.host` / `port` | hôte et port du fournisseur SMTP (D14) | Fournisseur SMTP | À chaque changement de fournisseur | Aucun e-mail (confirmation d'inscription bloquée) |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | `spring.mail.username` / `password` | identifiants ou clé API SMTP | Fournisseur SMTP | Périodique, côté fournisseur | Rejet SMTP |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` (ou équivalent du fournisseur) | `spring.mail.properties.mail.smtp.starttls.enable` | `true` | Exploitant | Non | Envoi en clair |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `server.forward-headers-strategy` | `framework` ou `native` selon le proxy (D2) | Exploitant | Non | Toutes les IP vues sont celles du proxy : limites partagées, verrous, journaux et `ip_hash` faussés |
| `ADMIN_EMAIL` | lu par `make admin` (voir `docs/ops/admin.md`) | adresse du premier administrateur | Produit | Sans objet | Utilisé une seule fois au bootstrap |
| Mot de passe de `psymate_app` dans `docker/postgres/init/01-app-role.sql` | sans objet en production | valeur de développement en dur | Ne jamais réutiliser | Sans objet | Le script ne sert qu'au conteneur local |
| Identifiants du stockage objet (si D11 retient S3/MinIO) | à créer avec l'implémentation | endpoint, bucket, clé d'accès, secret | Hébergeur | Périodique | À définir avec l'implémentation |
| Sauvegarde hors ligne de `APP_CRYPTO_KEY` | sans objet | copie scellée dans le coffre, séparée des sauvegardes de la base | Exploitant | Avec la clé | Sans elle, une restauration ne relit ni fichiers ni 2FA |

## 4. Checklist de mise en production (ordre proposé)

Avant toute donnée réelle (A9 R7 : bêta à 50 praticiens avec données réelles).

1. **Contrat d'hébergement HDS signé** (D1), sous-traitance RGPD annexée, région et extensions PostgreSQL confirmées.
2. **Registre des traitements et DPO** (D26), durées de conservation arrêtées (D13, D16, D17, D18, D29), position sur les mineurs (D30).
3. **Rôles PostgreSQL** : `psymate` propriétaire pour Flyway, `psymate_app` pour l'application (D4), TLS sur la connexion (D10). Vérifier après la première migration que les `REVOKE` de `V01`, `V03` et `V05` s'appliquent bien au rôle applicatif.
4. **Coffre et clé** : générer `APP_CRYPTO_KEY` dans le coffre, en sauvegarder une copie scellée séparée (D7, D8). Décider si la version de clé dans les blobs est implémentée avant la bêta ou après ; si après, acter qu'aucune rotation n'est possible d'ici là.
5. **Stockage des fichiers** (D11, D12) : volume persistant sauvegardé, ou implémentation S3 de `StorageService` livrée et testée.
6. **Profil de production** (D5) : supprimer les défauts localhost, fournir toutes les variables de la section 3, vérifier que l'application refuse de démarrer sans elles.
7. **Reverse proxy et TLS** (D2) : HTTPS de bout en bout ou proxy de confiance, `server.forward-headers-strategy` posé, HSTS et CSP au proxy (D34), `/actuator/**` et `/v3/api-docs` fermés au public (D32).
8. **SMTP** (D14, D15) : fournisseur choisi, SPF/DKIM/DMARC en place, envoi de test des quatre familles d'e-mails (confirmation, mot de passe, décision de vérification, contact annuaire).
9. **Sauvegardes** (D8, D9) : base + volumes de fichiers, chiffrées, restauration testée une fois avec la clé du coffre, RPO/RTO écrits.
10. **Mono-instance assumée** (D3, D21 à D25) ou chantiers multi-instance livrés. Une seule instance tant que rien n'est fait.
11. **Journaux** (D19) : collecte, durée, masquage ; alerte sur les WARN d'`AuditWriter` et les 500 du `GlobalExceptionHandler`.
12. **Premier administrateur** : procédure `docs/ops/admin.md` exécutée en production, 2FA activée, mot de passe à usage unique détruit.
13. **Limites et audit** (D20, D35) : règle IP sur inscription et login si l'étude le demande ; décision sur `export_requested` et sur l'export permitAll (D33).
14. **Déploiement progressif** 5 → 20 → 50 praticiens (A9 R7, G Q21), avec revue des `security_event` et de la volumétrie après chaque palier.
15. **Effacement** (D28) : procédure écrite et testée sur une base de test avant la première demande réelle, même si elle est manuelle.

## 5. Ce que l'étude n'a pas à refaire

- La décision du stockage de développement (disque local chiffré, E7 O9) et du format d'export (MD/PDF, G Q18) : prises.
- Les valeurs de limitation de débit (G Q24) : conservées sauf problème constaté.
- Le compagnon patient (A11) et l'IA V2 (A8) : hors de cette étude ; les tables réservées `patient_device`, `patient_submission`, `entry_embedding`, `entry_chunk`, `ai_feedback` et `patient_access` sont vides et le restent.
- L'agenda Google (G Q25) : hors V1.

Les trois points les plus urgents pour l'étude, par leur coût si on les découvre tard : la version de clé dans les blobs chiffrés (D7, sans quoi aucune rotation n'est possible), l'absence de tout mécanisme d'effacement ou d'anonymisation (D28, D29) et la séparation des rôles PostgreSQL en production (D4, sans quoi l'append-only n'est qu'une convention).
