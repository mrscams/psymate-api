# Compte administrateur de bootstrap

## Objectif et références

Créer le tout premier administrateur de PsyMate quand aucun compte admin n'existe encore, sans inscription publique, sans e-mail et sans mot de passe dans le dépôt.

- Architecture : A10 (rôle `admin`, 2FA obligatoire pour `/api/admin/**`).
- Décision : G Q5 (premier administrateur, mot de passe aléatoire de 20 caractères affiché une seule fois, changement obligatoire puis 2FA obligatoire).
- Tâche : M7-08 (module `identity.admin.bootstrap`).

La commande ne fait rien d'autre que créer le compte : elle ne démarre pas de serveur, n'envoie aucun e-mail et ne rejoue aucun événement.

## Prérequis

- La base est démarrée : `make up` (Postgres et Mailpit). Les migrations Flyway s'exécutent au démarrage de la commande elle-même, avant la création du compte : aucun lancement préalable de l'API n'est nécessaire.
- Java 21 et le dépôt à jour. Aucune variable secrète n'est nécessaire.

## Création

```
make admin EMAIL=hushan@gmail.com
```

- Sans `EMAIL`, la commande utilise `ADMIN_EMAIL` lu dans `.env` (copiez `.env.example` en `.env`). `EMAIL=` sur la ligne de commande prime toujours.
- La commande lance l'application avec les profils `local,cli` et l'argument `--create-admin=<e-mail>` : pas de serveur web, pas de bannière, journaux réduits à WARN.
- L'e-mail est normalisé comme à l'inscription (espaces retirés, minuscules).

Ce qui est créé, en une seule transaction :

| Table | Contenu |
|---|---|
| `tenant` | un tenant dont le nom est l'e-mail |
| `practitioner` | `role = 'admin'`, `verification_status = 'approved'`, `email_confirmed_at` et `verified_at` à l'instant de création, `locale = 'fr'`, `preferences = {"security.mustChangePassword": true}`, 2FA inactive |
| `security_event` | un événement `admin_bootstrap` (sans IP, sans détails) |

Valeurs techniques imposées par le schéma : `profession = 'psychologist'` (la colonne est `NOT NULL` avec une contrainte `CHECK` limitée à `psychologist`, `psychiatrist`, `coach` ; un admin n'exerce pas, cette valeur n'a aucune portée métier), `first_name = 'Admin'`, `last_name` = partie locale de l'e-mail, `display_name = 'Administrateur'`.

### Ce qui s'affiche, et pourquoi une seule fois

Le terminal affiche un bloc avec l'e-mail et un mot de passe de 20 caractères (lettres majuscules, minuscules et chiffres, tirés avec `SecureRandom`). Le mot de passe :

- n'existe qu'en mémoire du processus et dans cette sortie ;
- n'est ni écrit en base (seul son haché Argon2id l'est), ni journalisé, ni envoyé par e-mail ;
- ne peut donc pas être renvoyé : si vous le perdez, voir « Perte d'accès ».

Copiez-le tout de suite dans un gestionnaire de mots de passe. Il ne sert qu'à passer le premier login.

### Relance avec le même e-mail

Relancer `make admin` avec un e-mail qui a déjà un compte (même avec une casse différente) n'écrit rien : le message « Un compte existe déjà pour … ; aucun doublon créé. » s'affiche sur la sortie d'erreur et le code de sortie est 1. Aucun mot de passe n'est renvoyé. Un e-mail mal formé donne aussi le code 1 et ne crée rien.

## Premier login

Le mot de passe affiché est à usage unique : le compte porte `security.mustChangePassword`.

1. `POST /api/auth/login` avec l'e-mail et le mot de passe affiché répond `403` avec le code `PASSWORD_CHANGE_REQUIRED`. Aucune session n'est ouverte.
2. `POST /api/auth/password/change` avec `{ "email", "currentPassword", "newPassword" }` répond `204`. Cette route est publique justement parce que l'étape 1 ne laisse aucune session. Elle applique les mêmes protections que le login (verrou après 5 échecs, réponse 401 uniforme). Le nouveau mot de passe doit respecter la politique (12 caractères minimum, majuscules et minuscules, ni e-mail ni nom, pas un mot de passe courant) et différer de l'actuel (`422`). Toutes les sessions du compte sont révoquées, l'événement `password_changed` est journalisé et l'obligation de changement est levée.
3. `POST /api/auth/login` avec le nouveau mot de passe répond `200` et ouvre la session.

Tant que la 2FA n'est pas active, `/api/admin/**` répond `403 TWO_FACTOR_REQUIRED`.

## Activation de la 2FA (obligatoire)

Avec la session de l'étape 3 :

1. `POST /api/me/2fa/enable` renvoie le secret (Base32) et l'URI `otpauth://` à scanner dans une application TOTP.
2. `POST /api/me/2fa/verify` avec un code à 6 chiffres active la 2FA et renvoie les 10 codes de secours, affichés une seule fois. Conservez-les hors ligne.

À partir de là, le login répond `202 {"otpRequired": true}` tant qu'il manque le code, et `/api/admin/**` devient accessible. Un administrateur ne peut pas désactiver sa 2FA.

## Perte d'accès

- Mot de passe perdu : `POST /api/auth/password-reset/request` envoie un lien par e-mail si le SMTP est configuré. Sinon, un second administrateur peut intervenir, ou on recrée un compte avec un autre e-mail (`make admin EMAIL=…`).
- 2FA perdue : utiliser un code de secours à la place du code à 6 chiffres dans le champ `otp`. Sans code de secours, il faut un second administrateur.
- Recommandation : toujours deux administrateurs, avec des adresses différentes. Créez le second de la même façon (`make admin EMAIL=…`), il suit la même procédure.

## Rotation

- Changer son mot de passe : `POST /api/auth/password/change` (e-mail, mot de passe actuel, nouveau mot de passe, et `otp` si la 2FA est active). Toutes les sessions sont révoquées, il faut se reconnecter.
- Départ d'un administrateur : un autre administrateur suspend le compte (suspension manuelle, à venir avec M1-14b).

## Production

La procédure est identique après le premier déploiement, sur l'hôte qui peut joindre la base de production :

```
java -jar psymate-api.jar --spring.profiles.active=<profil de déploiement>,cli --create-admin=<e-mail>
```

- Exécutez-la dans un terminal privé, que personne ne regarde et qui n'est pas enregistré.
- Ne redirigez jamais la sortie vers un fichier ni vers un journal (`> fichier`, `| tee`, historique de CI, journal d'un orchestrateur) : le mot de passe y resterait en clair.
- `<profil de déploiement>` est le profil Spring du déploiement (le dépôt ne définit que `local` et `test` : la configuration de production vient entièrement de l'environnement). Le profil `cli` s'y ajoute et désactive le serveur web.
- Aucun e-mail n'est envoyé, aucun secret n'est lu depuis le dépôt.
- Changez le mot de passe et activez la 2FA immédiatement, puis créez le second administrateur.
