# Document K – Plan d'action · Prise de rendez-vous en ligne (API), jalon M14

22 novembre 2026. **Validé par l'utilisateur le 22 novembre 2026.** Plan de réalisation de la prise de rendez-vous en ligne côté API (`psymate-api`), **dans le module `agenda`** (aucun nouveau module). Ce document copie les sections K1, K2.1, K3, K4, K5, K6.1 et K7 du document du web, `psymate-web/docs/K-plan-prise-de-rendez-vous.md`, renumérotées K1 à K7 : K2.1 devient K2 et K6.1 devient K6, les autres gardent leur numéro. La partie web (décisions Q60 à Q66 du G de psymate-web, tâches M14-09 à M14-15, prompts web K8, revue K9) est dans le document du web ; ce qui y reste ouvert (K10) est repris tel quel à la fin de ce document. Pour toute tâche M14, ce document fait foi (K4 DDL, K5 contrat). Les décisions Q71 à Q82 sont inscrites dans `docs/G-decisions.md` (G10) ; en cas de contradiction, G l'emporte.

Renvois : un « Qn » sans précision est une décision du G de psymate-api ; une décision du web est dite « du G de psymate-web ». Le prompt de M14-01 (K7) est copié tel quel : ses numéros de section (K2.1, K4, K6.1, K7) visent le document du web.

## K1. Périmètre

Ce que la fonction doit permettre :

- Un visiteur, sans compte, choisit un créneau sur la page publique d'un praticien et laisse son nom, son prénom, son e-mail et, s'il le veut, son téléphone.
- Il confirme son adresse par un lien reçu par e-mail. Rien n'entre dans l'agenda avant ce clic.
- Selon le réglage du praticien, la demande devient un rendez-vous confirmé tout de suite (acceptation automatique) ou attend sa réponse (validation manuelle, réglage par défaut).
- Le praticien règle dans ses préférences : activation, mode de confirmation, plages ouvertes, durée d'un créneau, délai minimal, horizon, modalités.
- Le demandeur suit et annule son rendez-vous par un lien personnel. Il est prévenu par e-mail de chaque changement.
- Ses données sont effacées par paliers (K3.5).

Hors M14 : compte patient ; paiement ou acompte ; rappels par SMS ; rappel automatique la veille ; liste d'attente ; choix du motif ou d'un type de consultation ; plusieurs lieux ; jours fériés et congés (le praticien bloque un créneau en y posant un rendez-vous) ; synchronisation Google (M12) ; champ libre « motif » (écarté exprès, voir Q72) ; déplacement du rendez-vous par le demandeur (il annule et redemande).

## K2. Décisions (tranchées par l'utilisateur le 22 novembre 2026)

Décisions de l'API, inscrites dans le G de psymate-api (G10) par M14-01. Celles du web (K2.2 du document du web) vont dans le G de psymate-web par M14-09.

| # | Question | Décision |
| --- | --- | --- |
| Q71 | Où vit la prise de rendez-vous en ligne | Dans le module **`agenda`**, sous-domaine « booking » (fichiers préfixés `Booking` ou `PublicBooking`), sans nouveau module. Routes publiques sous `/api/public/booking/**`, dans la chaîne publique existante (`directory.config.PublicSecurityConfig`, sans session ni CSRF). Nouvelle dépendance autorisée `agenda → directory :: api`, pour une seule façade, `PublishedProfileFacade` (slug → praticien publié **et** approuvé) ; `directory` ne dépend toujours pas d'`agenda`. **Aucun compte patient** : aucun nouvel acteur dans `identity`, aucun mot de passe, aucune session pour le demandeur. |
| Q72 | Ce que le demandeur fournit | Nom et prénom (100 caractères chacun), e-mail (320), tous trois obligatoires ; téléphone facultatif (30, règle de `guest.phone`) ; modalité parmi celles que le praticien offre ; consentement coché. **Aucun champ libre** : un motif de consultation est une donnée de santé écrite par un inconnu avant toute relation, on ne le recueille pas. La demande n'est rattachée à aucun dossier. |
| Q73 | E-mail vérifié avant tout | Une demande crée une ligne `booking_request` et envoie un lien à usage unique, valable **30 minutes**. Tant que le lien n'est pas suivi : aucun rendez-vous, aucun créneau retenu, aucun e-mail au praticien. Une demande non vérifiée est supprimée 24 h après sa création. |
| Q74 | Deux modes de confirmation | Préférence `booking.confirmation` : `manual` (défaut) ou `auto`. À la vérification de l'e-mail, l'API crée un **rendez-vous invité** (G Q60) d'origine `online` : statut `tentative` en `manual`, `planned` en `auto`. Le mode est lu au moment de la vérification. En `manual`, le praticien confirme (`POST /api/appointments/{id}/confirm`, existant) ou refuse (`POST /api/appointments/{id}/cancel`, existant). Dans les deux modes il peut toujours annuler ou déplacer ensuite. |
| Q75 | Réglages du praticien | Clés plates (ADR-11) dans `practitioner.preferences`, lues et écrites par `PATCH /api/me/preferences` : `booking.enabled` (défaut `false`), `booking.confirmation` (`manual`), `booking.windows` (liste de `{ day: 1 à 7, lundi = 1, start: "HH:mm", end: "HH:mm" }`, minutes multiples de 5, 21 plages au plus, sans chevauchement le même jour, défaut `[]`), `booking.slotDurationMin` (15 à 180, multiple de 5, défaut 60), `booking.minNoticeHours` (1 à 168, défaut 24), `booking.horizonDays` (7 à 90, défaut 28), `booking.modalities` (sous-ensemble non vide de `in_person`, `video`, `phone`, défaut `["in_person"]`), `booking.timezone` (fuseau IANA, défaut `Europe/Paris`). La prise de rendez-vous est **offerte** si `booking.enabled` vaut `true`, s'il existe au moins une plage, et si le profil est publié et le praticien approuvé. |
| Q76 | Créneaux offerts | Chaque plage est découpée en créneaux de `booking.slotDurationMin`, à partir de son début, dans `booking.timezone`. Un créneau est offert s'il commence au plus tôt à `maintenant + minNoticeHours`, au plus tard à la fin du jour `aujourd'hui + horizonDays`, et s'il ne chevauche aucun rendez-vous actif (`planned`, `tentative`) du praticien. L'API ne publie **que** les créneaux offerts : ni les créneaux pris, ni leur raison, ni l'amplitude réelle de travail. |
| Q77 | Un créneau en ligne est exclusif (précise Q54) | Q54 reste vraie pour le praticien : il peut superposer ses rendez-vous. Pour une demande en ligne, la vérification de l'e-mail se fait sous un verrou transactionnel par praticien : si le créneau n'est plus offert, `409 SLOT_TAKEN` et aucun rendez-vous. Le premier e-mail vérifié obtient le créneau, dans les deux modes. |
| Q78 | Des réponses publiques qui ne révèlent personne | Aucune réponse publique ne dépend de l'identité saisie. `404 BOOKING_NOT_AVAILABLE` couvre sans distinction : slug inconnu, profil non publié, praticien non approuvé, fonction désactivée. À la création : `409 SLOT_NOT_AVAILABLE` si le créneau n'est pas offert (information déjà publique), sinon `202` sans corps, y compris quand l'envoi est ignoré en silence (pot de miel, formulaire soumis en moins de 3 s, plafond d'e-mails atteint). Jamais de « vous êtes déjà patient », jamais d'acceptation automatique réservée aux patients connus : la différence de réponse dirait à n'importe qui qu'une adresse appartient à un patient. |
| Q79 | Plafonds | 5 créations par heure et par IP (règle `public-booking`, `429` avec `Retry-After`), en plus des 60 requêtes par minute de la chaîne publique. 3 e-mails de vérification par heure et par adresse (au-delà : `202`, rien n'est envoyé). Un seul rendez-vous en ligne actif et à venir par adresse et par praticien : à la vérification, `409 BOOKING_ALREADY_ACTIVE` (seul le détenteur de la boîte le voit). |
| Q80 | Lien de gestion | Après la vérification, le demandeur reçoit un lien personnel (jeton aléatoire de 256 bits, stocké haché) pour voir l'état de son rendez-vous et l'annuler, valable jusqu'à la fin du rendez-vous. Seule l'empreinte du jeton est gardée : chaque e-mail de suivi porte donc un nouveau lien, qui remplace le précédent. Les jetons voyagent dans le **corps** des requêtes vers l'API, jamais dans une URL d'API. L'annulation par le demandeur est une annulation ordinaire (`cancelled`, événement `AppointmentCancelled`) ; le praticien est prévenu par e-mail. |
| Q81 | E-mails | Au demandeur : vérification ; demande transmise (`manual`) ou rendez-vous confirmé (`auto`) avec le lien de gestion ; confirmation par le praticien ; refus ; annulation ; déplacement ; expiration. Au praticien, à l'adresse de son compte : nouvelle demande (`manual`), nouveau rendez-vous (`auto`), annulation par le demandeur. **Objet neutre** : ni nom du praticien, ni profession, ni date. Jamais la note de planification. Envoi après validation de la transaction, hors du fil de la requête ; un échec d'envoi ne remonte jamais au demandeur. Langue du demandeur : `locale` envoyée par le web (`fr` ou `en`). |
| Q82 | Expiration et conservation | **Expiration.** En `manual`, une demande sans réponse est annulée d'office 72 h après la vérification, ou au début du créneau si c'est plus tôt ; le demandeur en est prévenu. **Conservation.** (a) Demande non vérifiée : ligne supprimée après 24 h. (b) À la vérification, nom, prénom, e-mail et téléphone passent dans les colonnes `guest_*` du rendez-vous et sont effacés de `booking_request`, qui ne garde que des empreintes et des jetons hachés. (c) Rendez-vous en ligne annulé avant d'avoir eu lieu (refus, expiration, annulation) : `guest_*` à `NULL` **30 jours** après l'annulation. (d) Rendez-vous qui a eu lieu : règle de Q60, 6 mois. (e) Rattachement à un patient (`attach-patient`) : `guest_*` à `NULL` aussitôt, la personne vit alors dans le dossier du praticien. (f) La ligne `booking_request` est supprimée 30 jours après la fin ou l'annulation du rendez-vous. Deux lignes à ajouter à E9. |

## K3. Parcours détaillé

### K3.1 Le demandeur

1. **Fiche publique** (`/praticiens/:slug`). Si la prise de rendez-vous est offerte, un bouton « Prendre rendez-vous » apparaît à côté de « Écrire au praticien ». Sinon, rien ne change.
2. **Choix du créneau** (`/praticiens/:slug/rendez-vous`). En tête : le nom du praticien, la durée d'un créneau, la mention du mode (Q61 du G de psymate-web) et la mention d'urgence (Q63 du G de psymate-web). Dessous : les créneaux offerts, groupés par jour, deux semaines à la fois, dans le fuseau du praticien (nommé à l'écran). Aucun créneau offert : « Aucun créneau disponible pour l'instant », avec un lien vers le formulaire de contact.
3. **Coordonnées.** Après le choix d'un créneau : prénom, nom, e-mail, téléphone (facultatif), modalité si le praticien en offre plusieurs, case de consentement avec un lien vers la page Confidentialité. Bouton « Envoyer ma demande » (`manual`) ou « Réserver ce créneau » (`auto`).
4. **Envoi.** Réponse `202` : écran « Consultez votre boîte mail. Le lien est valable 30 minutes. » Si le créneau vient d'être pris (`409 SLOT_NOT_AVAILABLE`) : retour au choix du créneau, coordonnées conservées à l'écran, liste relue.
5. **Lien de vérification** (`/rendez-vous/confirmer/:token`). La page envoie le jeton. Quatre issues :
   - `manual` : « Votre demande est transmise. Le praticien vous répondra par e-mail. »
   - `auto` : « Votre rendez-vous est confirmé », avec la date, l'heure, la durée et la modalité.
   - lien expiré ou inconnu : « Ce lien n'est plus valable », avec un lien pour recommencer ;
   - créneau pris entre-temps, ou rendez-vous déjà actif avec ce praticien : message dédié, avec un lien vers les créneaux.
6. **E-mail de suite**, avec le lien de gestion.
7. **Gestion** (`/rendez-vous/gerer/:token`). État (« En attente de réponse », « Confirmé », « Annulé », « Passé »), date, heure, durée, modalité, nom du praticien. Bouton « Annuler ma demande » ou « Annuler mon rendez-vous », avec une confirmation. Pas de modification : le demandeur annule et redemande.

### K3.2 Le praticien

1. **Réglages** (Mon compte › Préférences, section « Prise de rendez-vous en ligne »). Interrupteur d'activation ; choix « Je valide chaque demande » ou « Acceptation automatique dans mes plages » ; plages par jour de la semaine ; durée d'un créneau ; délai minimal ; horizon ; modalités ; fuseau (prérempli par celui du navigateur à la première activation). Un lien « Voir ma page de rendez-vous » ouvre la page publique.
2. **Nouvelle demande** (`manual`). E-mail « Nouvelle demande de rendez-vous ». Dans l'agenda : le créneau en pointillé avec la mention « Demande en ligne », et le compteur de l'entrée « Demandes ».
3. **Réponse.** Dans le détail : « Confirmer » (le rendez-vous devient planifié, le demandeur reçoit l'e-mail de confirmation) ou « Refuser » (annulation, e-mail de refus, sans motif).
4. **Mode automatique.** Le rendez-vous arrive planifié, avec la mention « Demande en ligne ». E-mail « Nouveau rendez-vous pris en ligne ».
5. **Ensuite.** Déplacer ou annuler comme tout rendez-vous : le demandeur est prévenu. Le jour venu, créer la fiche patient puis rattacher le rendez-vous (`attach-patient`, tâche M14-15).

### K3.3 États d'une demande

| Moment | `booking_request` | `appointment` | Ce que voit le demandeur |
| --- | --- | --- | --- |
| Formulaire envoyé | créée, non vérifiée | aucun | « Consultez votre boîte mail » |
| Lien suivi, mode `manual` | vérifiée, identité effacée | invité, `tentative`, `origin = online` | « En attente de réponse » |
| Lien suivi, mode `auto` | vérifiée, identité effacée | invité, `planned`, `origin = online` | « Confirmé » |
| Le praticien confirme | inchangée | `planned` | « Confirmé » |
| Le praticien refuse ou annule | `closed_by = practitioner` | `cancelled` | « Annulé » |
| Le demandeur annule | `closed_by = visitor` | `cancelled` | « Annulé » |
| Sans réponse après 72 h | `closed_by = expired` | `cancelled` | « Annulé » (demande expirée) |
| Lien jamais suivi | supprimée après 24 h | aucun | lien expiré |

### K3.4 Cas limites

- **Deux personnes, même créneau.** Aucune ne retient le créneau avant de vérifier son e-mail. La première qui vérifie l'obtient ; la seconde lit « Ce créneau vient d'être pris » et choisit un autre créneau.
- **Le praticien pose un rendez-vous sur un créneau demandé mais pas encore vérifié.** Même issue : `409 SLOT_TAKEN` à la vérification.
- **Le praticien désactive la fonction ou dépublie son profil.** Les pages publiques répondent `404`. Les rendez-vous déjà créés restent, et le lien de gestion continue de fonctionner.
- **Le praticien change de mode.** Le mode appliqué est celui lu à la vérification. Les demandes déjà en attente restent en attente.
- **Lien de vérification suivi deux fois** (double clic, antivirus de messagerie). La seconde fois renvoie le même résultat, sans second rendez-vous.
- **Le praticien rattache l'invité à un patient.** Les colonnes `guest_*` sont vidées : les e-mails au demandeur s'arrêtent. Le lien de gestion reste valable.
- **Adresse saisie par erreur.** Le destinataire reçoit un e-mail de vérification sans nom de praticien dans l'objet ; s'il ne clique pas, tout disparaît en 24 h.

### K3.5 Conservation des données du demandeur

| Cas | Où vivent les données | Durée |
| --- | --- | --- |
| E-mail jamais vérifié | `booking_request` | 24 h, puis ligne supprimée |
| Demande refusée, expirée ou annulée avant d'avoir eu lieu | `appointment.guest_*` | 30 jours après l'annulation, puis colonnes à `NULL` |
| Rendez-vous qui a eu lieu | `appointment.guest_*` | 6 mois après la fin (G Q60) |
| Invité rattaché à un patient | dossier du patient | celle du dossier |
| Empreintes et jetons hachés | `booking_request` | 30 jours après la fin ou l'annulation |

Ce qui échappe à l'effacement : les e-mails déjà envoyés, dans la boîte du demandeur et chez le prestataire d'envoi.

## K4. Modèle de données (migration `V15__agenda_booking.sql`)

```sql
-- Prise de rendez-vous en ligne (K, G Q71 à Q82) : origine et e-mail de l'invité, demandes publiques
ALTER TABLE appointment
  ADD COLUMN guest_email text,
  ADD COLUMN origin      text NOT NULL DEFAULT 'practitioner' CHECK (origin IN ('practitioner','online'));

ALTER TABLE appointment DROP CONSTRAINT ck_appointment_subject;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_subject CHECK (
  (patient_id IS NOT NULL AND guest_first_name IS NULL AND guest_last_name IS NULL AND guest_phone IS NULL AND guest_email IS NULL AND guest_purged_at IS NULL)
  OR (patient_id IS NULL AND (guest_last_name IS NOT NULL OR guest_purged_at IS NOT NULL))
);
CREATE INDEX ix_appointment_online_pending ON appointment(practitioner_id, starts_at)
  WHERE origin = 'online' AND status = 'tentative';

CREATE TABLE booking_request (
  id                 uuid PRIMARY KEY,
  tenant_id          uuid NOT NULL REFERENCES tenant(id),
  practitioner_id    uuid NOT NULL REFERENCES practitioner(id),
  starts_at          timestamptz NOT NULL,
  ends_at            timestamptz NOT NULL,
  modality           text NOT NULL CHECK (modality IN ('in_person','video','phone')),
  first_name         text,                 -- NULL dès la vérification (Q82 b)
  last_name          text,
  email              text,
  phone              text,
  locale             text NOT NULL DEFAULT 'fr' CHECK (locale IN ('fr','en')),
  email_hash         text NOT NULL,        -- HMAC de l'adresse normalisée : plafonds de Q79
  verify_token_hash  text NOT NULL UNIQUE,
  verify_expires_at  timestamptz NOT NULL,
  verified_at        timestamptz,
  manage_token_hash  text UNIQUE,
  appointment_id     uuid REFERENCES appointment(id),
  closed_by          text CHECK (closed_by IN ('visitor','practitioner','expired')),
  created_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_booking_request_order CHECK (ends_at > starts_at),
  CONSTRAINT ck_booking_request_verified CHECK ((verified_at IS NULL) = (appointment_id IS NULL)),
  CONSTRAINT ck_booking_request_identity CHECK (
    verified_at IS NULL OR (first_name IS NULL AND last_name IS NULL AND email IS NULL AND phone IS NULL)
  )
);
CREATE INDEX ix_booking_request_email ON booking_request(email_hash, created_at);
CREATE INDEX ix_booking_request_created ON booking_request(created_at) WHERE verified_at IS NULL;
CREATE INDEX ix_booking_request_appointment ON booking_request(appointment_id) WHERE appointment_id IS NOT NULL;
```

Propriétaire : module `agenda` (`TableOwnershipTests.TABLE_OWNERS += booking_request → agenda`). V12 et V13 ne sont pas modifiées. `origin` est distincte de la colonne `source` que M12 prévoyait pour Google : M12 devra réutiliser `origin` (valeur `google`) plutôt que d'ajouter `source`.

E9 reçoit deux lignes : `booking_request` (la ligne, 24 h après la création si non vérifiée, 30 jours après la fin ou l'annulation du rendez-vous sinon ; par `agenda`, `BookingPurgeJob`) ; la ligne existante des colonnes invité ajoute `guest_email` et le délai de 30 jours pour un rendez-vous en ligne annulé.

## K5. Contrat d'API (`0.15.0`, tag `contract-v0.15.0` posé par M14-08)

### K5.1 Routes publiques (sans session, `Cache-Control: no-store`)

| Route | Tâche | Rôle | Réponse |
| --- | --- | --- | --- |
| `GET /api/public/booking/practitioners/{slug}` | M14-05 | Dit si la prise de rendez-vous est offerte et avec quels réglages. | `PublicBookingDto { slug, displayName, confirmation, slotDurationMin, modalities, timezone, minNoticeHours, horizonDays }` ; `404 BOOKING_NOT_AVAILABLE` |
| `GET /api/public/booking/practitioners/{slug}/slots?from&to` | M14-05 | Créneaux offerts. `from` et `to` sont des dates `AAAA-MM-JJ` dans le fuseau du praticien, `to` inclus, 31 jours au plus (`422 WINDOW_TOO_LARGE`). | `PublicSlotsDto { timezone, slots: [{ startsAt, endsAt }] }`, instants UTC triés ; `404 BOOKING_NOT_AVAILABLE` |
| `POST /api/public/booking/practitioners/{slug}/requests` | M14-06 | Corps `CreateBookingRequest { startsAt, modality, firstName, lastName, email, phone?, locale?, consent, website, formShownAt }`. | `202` sans corps ; `404 BOOKING_NOT_AVAILABLE` ; `409 SLOT_NOT_AVAILABLE` ; `422` par champ ; `429 RATE_LIMITED` |
| `POST /api/public/booking/requests/verify` | M14-06 | Corps `{ token }`. Crée le rendez-vous invité. Idempotent tant que le jeton est valable. | `200 PublicAppointmentDto` avec `manageToken` ; `404 BOOKING_TOKEN_INVALID` ; `410 BOOKING_TOKEN_EXPIRED` ; `409 SLOT_TAKEN` ; `409 BOOKING_ALREADY_ACTIVE` ; `404 BOOKING_NOT_AVAILABLE` |
| `POST /api/public/booking/manage` | M14-06 | Corps `{ token }`. État du rendez-vous. | `200 PublicAppointmentDto` ; `404 BOOKING_TOKEN_INVALID` |
| `POST /api/public/booking/manage/cancel` | M14-06 | Corps `{ token }`. Annule ; idempotent sur un rendez-vous déjà annulé. | `200 PublicAppointmentDto` ; `404 BOOKING_TOKEN_INVALID` ; `409 APPOINTMENT_NOT_ACTIVE` si le rendez-vous est passé |

`PublicAppointmentDto` : `{ status: pending | confirmed | cancelled | past, cancelledBy?: visitor | practitioner | expired, startsAt, endsAt, durationMin, modality, practitioner: { slug, displayName }, canCancel, manageToken? }`. Il ne porte ni le nom, ni l'e-mail, ni le téléphone du demandeur, ni aucune note.

### K5.2 Routes du praticien (session)

| Route | Tâche | Changement |
| --- | --- | --- |
| `AppointmentDto` | M14-04 | `origin` (`practitioner` ou `online`) ; `guest.email` (nullable, null après la purge). |
| `POST /api/appointments`, `PATCH /api/appointments/{id}` | M14-04 | `guest.email?` (320 au plus, format e-mail, chaîne vide = aucun). `origin` refusé (`UNKNOWN_FIELD`). |
| `GET /api/agenda/requests` | M14-04 | Demandes en ligne en attente : `tentative`, `origin = online`, à venir, tri `startsAt`, 100 au plus. Réponse `{ appointments: [AppointmentDto…] }`. |
| `POST /api/appointments/{id}/confirm`, `/cancel`, `PATCH` | M14-07 | Forme inchangée. Sur un rendez-vous en ligne dont l'invité a un e-mail : e-mail au demandeur (Q81). |
| `GET`, `PATCH /api/me/preferences` | M14-02 | Les huit clés `booking.*` (Q75), erreurs `422` par clé. |

## K6. Tâches, couloirs et parallélisme (API, `psymate-api`)

| ID | Titre | Couloir | Dépend de | Parallèle avec |
| --- | --- | --- | --- | --- |
| M14-01 | Décisions Q71 à Q82 et documentation ; copie de ce plan côté API | docs | — | M14-02, M14-03, M14-04 |
| M14-02 | Préférences `booking.*` (Q75) | identity (preferences) | — | M14-01, M14-03, M14-04 |
| M14-03 | `PublishedProfileFacade` : slug → praticien publié et approuvé | directory (api) | — | M14-01, M14-02, M14-04 |
| M14-04 | `V15`, `origin`, `guest.email`, `GET /api/agenda/requests`, entité `BookingRequest` | agenda | — | M14-01, M14-02, M14-03 |
| M14-05 | Créneaux publics : réglages et créneaux offerts | agenda (+ une règle dans la chaîne publique) | M14-02, M14-03, M14-04 | — |
| M14-06 | Demande, vérification de l'e-mail, rendez-vous, lien de gestion, annulation par le demandeur | agenda | M14-05 | — |
| M14-07 | E-mails de suivi, expiration, purges | agenda | M14-06 | — |
| M14-08 | Seed, tag `contract-v0.15.0`, suivi | racine (`tools/seed`) | M14-07 | — |

**Vagues.** M14-A : 01, 02, 03, 04 (quatre terminaux, couloirs disjoints). M14-B : 05. M14-C : 06. M14-D : 07. M14-E : 08. Effort indicatif : 7 jours.

**Agents.** `backend-module-dev` (M14-02 à M14-07), `seed-perf` (M14-08), Claude directement pour M14-01. `reviewer` avant chaque fusion, puis `make log`.

## K7. Prompts API

Manœuvre : dans `psymate-api`, `git checkout main && git pull && claude --worktree <ID>`, coller le prompt. À la fin : `Lance le sous-agent reviewer sur cette branche et corrige ce qu'il marque bloquant.`, lecture humaine (migration, diff de `contract/openapi.yaml`, un IT), `gh pr merge <n> --squash --delete-branch`.

### M14-01 — Décisions et documentation

```text
Tâche M14-01 — Inscrire le jalon M14 « Prise de rendez-vous en ligne » dans la documentation de psymate-api (docs/ et CLAUDE.md seulement ; aucun code Java, aucune migration).

Contexte : lis ../psymate-web/docs/K-plan-prise-de-rendez-vous.md en entier (il fait foi pour cette tâche), docs/G-decisions.md (Q54, Q58, Q60, Q61 et le format des lignes Q62 à Q70), docs/I-plan-agenda-api.md (I2, I3, I8 : ce que M14 étend), docs/J-plan-profil-360-api.md (modèle d'un plan de jalon), docs/A-architecture.md (A3, A5 : modules et dépendances ; la section Agenda de A4), docs/C-plan.md (C1 et la section du jalon M13, format des tables de tâches), docs/D-orchestration-backend.md (D1, D3, D5), docs/E-modele-donnees.md (E1, E3.14, E4, E9), CLAUDE.md.

Objectif :
- Nouveau fichier docs/K-plan-prise-de-rendez-vous-api.md : copie des sections K1, K2.1, K3, K4, K5, K6.1 et K7 du document du web, renumérotées K1 à K7, avec en tête la date, le renvoi au document du web pour la partie web, et la phrase « Pour toute tâche M14, ce document fait foi (K4 DDL, K5 contrat) ».
- G : nouvelle section « Prise de rendez-vous en ligne, jalon M14 » avec Q71 à Q82 copiées de K2.1, une ligne par question, même style que Q62 à Q70, datées du 22 novembre 2026, « tranchées par l'utilisateur ». Q54 et Q60 reçoivent en fin de texte « Précisée par Q77 » et « Complétée par Q82 ».
- A : A3 et A5 disent que agenda dépend aussi de directory :: api (PublishedProfileFacade) et que directory ne dépend toujours pas d'agenda ; la section Agenda de A4 renvoie à K5.
- C : C1 ajoute le jalon M14 (objectif, démontrable, effort indicatif 7 j) ; nouvelle section pour M14 avec le tableau des tâches M14-01 à M14-08, critères repris mot pour mot de K7.
- D : D1, D3, D5 reçoivent les lignes, vagues et couloirs de K6.1.
- E : E1 ajoute booking_request → agenda ; E3.14 reçoit le DDL de K4, identique octet pour octet ; E4 ajoute la ligne V15__agenda_booking.sql ; E9 reçoit les deux changements décrits sous K4.
- CLAUDE.md : une ligne « La prise de rendez-vous en ligne vit dans agenda (G Q71) : aucun compte patient, e-mail vérifié avant tout rendez-vous, réponses publiques qui ne révèlent personne (Q78). Pour toute tâche M14, docs/K-plan-prise-de-rendez-vous-api.md fait foi. » ; la liste des dépendances d'agenda est mise à jour.
- .claude/agents/backend-module-dev.md : ajoute « docs/K-plan-prise-de-rendez-vous-api.md pour toute tâche M14 » à sa liste de lecture.

Périmètre : docs/**, CLAUDE.md, .claude/agents/backend-module-dev.md. Rien d'autre.

Critères d'acceptation :
- G Q71 à Q82 existent ; Q54 et Q60 y renvoient ;
- chaque tâche M14-01 à M14-08 a sa ligne dans C et D avec les dépendances de K6.1 ;
- le DDL de E3.14 est identique octet pour octet au bloc SQL de K4 ;
- E9 porte la ligne booking_request et la ligne des colonnes invité mise à jour ;
- ./mvnw verify reste vert (aucune source touchée).

Interdits : toute modification de code, de migration ou de contrat ; toute réécriture de docs/progress/index.html ou d'un événement existant ; inscrire une décision que K ne contient pas.

Livrable : PR « M14-01 Prise de rendez-vous en ligne : décisions et documentation », puis make log TASK=M14-01 AGENT=claude STATUS=done SUMMARY="…" PR=<url>.
```

### M14-02 — Préférences `booking.*`

```text
Tâche M14-02 — Préférences booking.* (dépôt psymate-api, module identity, sous-domaine preferences). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K2 Q75 ; tant que M14-01 n'est pas fusionnée, ../psymate-web/docs/K-plan-prise-de-rendez-vous.md fait foi), docs/G-decisions.md Q57 (préférences agenda.*, le précédent à suivre). Code : identity/preferences/Preferences.java et PreferencesSchema.java (clés agenda.*, valeurs par défaut, validation après fusion), leurs tests, et la PR de M11-04.

Objectif :
- Huit clés plates, avec leurs valeurs par défaut renvoyées par GET /api/me/preferences même quand rien n'est enregistré : booking.enabled (false), booking.confirmation ("manual" ; "manual" ou "auto"), booking.windows ([] ; liste de { day, start, end }), booking.slotDurationMin (60), booking.minNoticeHours (24), booking.horizonDays (28), booking.modalities (["in_person"]), booking.timezone ("Europe/Paris").
- Validation à l'écriture (PATCH /api/me/preferences), erreurs 422 avec field = la clé :
  - booking.confirmation hors liste → INVALID_VALUE ;
  - booking.windows : day entier de 1 à 7 ; start et end au format HH:mm, minutes multiples de 5, end strictement après start ; 21 plages au plus ; deux plages du même jour ne se chevauchent pas ; tout objet mal formé ou champ inconnu → INVALID_VALUE ; plus de 21 → OUT_OF_RANGE ;
  - booking.slotDurationMin : entier de 15 à 180, multiple de 5 → OUT_OF_RANGE sinon ;
  - booking.minNoticeHours : entier de 1 à 168 ; booking.horizonDays : entier de 7 à 90 → OUT_OF_RANGE sinon ;
  - booking.modalities : liste non vide, sans doublon, de in_person, video, phone → INVALID_VALUE sinon ;
  - booking.timezone : identifiant IANA connu de java.time.ZoneId → INVALID_VALUE sinon.
- Les plages sont stockées triées (jour, puis début). Aucune règle croisée : booking.enabled = true avec zéro plage est accepté (la fonction n'est alors simplement pas offerte, Q75).
- PreferencesSchema décrit les huit clés (type, bornes, défaut, exemple) pour le contrat ; PreferencesChanged est publié comme pour les autres clés.
- Contrat regénéré (make contract), app.contract.version = 0.15.0 si aucune tâche M14 ne l'a déjà posé.

Périmètre : src/main/java/com/psymate/identity/preferences/**, leurs tests, messages_fr.properties / messages_en.properties (clés sous « # preferences booking (M14-02) »), contract/openapi.yaml (généré).

Critères d'acceptation (PreferencesBookingIT, PreferencesBookingTest) :
- GET sans préférence enregistrée renvoie les huit valeurs par défaut ;
- PATCH { "booking.enabled": true, "booking.confirmation": "auto", "booking.windows": [{ "day": 1, "start": "09:00", "end": "12:00" }] } → 200, valeurs relues à l'identique ;
- des plages envoyées dans le désordre sont relues triées ;
- chaque règle de validation ci-dessus a un cas qui répond 422 avec le bon code sur la bonne clé, et aucune écriture partielle (les autres clés du même PATCH ne sont pas enregistrées) ;
- deux plages du lundi 09:00–12:00 et 11:00–13:00 → 422 INVALID_VALUE sur booking.windows ; 09:00–12:00 et 12:00–14:00 → acceptées ;
- booking.timezone "Europe/Paris" et "America/Montreal" acceptées, "Paris" refusée ;
- les clés existantes (locale, agenda.*, sessions.*) gardent leur comportement : leurs tests restent verts ;
- ContractIT vert, contrat regénéré.

Tests : unitaires pour la validation des plages ; IT Testcontainers pour GET et PATCH.

Interdits : toute table ou colonne nouvelle ; toute route nouvelle ; toute lecture de ces clés hors d'identity dans cette tâche ; dépendance Maven.

Livrable : PR « M14-02 Préférences booking.* », puis make log TASK=M14-02 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M14-03 — `PublishedProfileFacade`

```text
Tâche M14-03 — Façade PublishedProfileFacade dans directory.api (dépôt psymate-api, module directory). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K2 Q71, Q75, Q78), docs/G-decisions.md Q48 (règle « praticien approuvé », lue par PractitionerFacade). Code : directory/api (aujourd'hui deux événements, aucune façade), directory/repository/PractitionerProfileRepository.java (findPublishedRecipient), directory/service/PublicDirectoryService.java (où le filtre « approuvé » est appliqué), identity/api/PractitionerFacade.java, agenda/api/AppointmentExportFacade.java et son implémentation (modèle d'une façade : interface dans api, implémentation dans service, vue dans api).

Objectif :
- directory.api.PublishedProfileFacade : Optional<PublishedProfileView> bySlug(String slug) et Optional<PublishedProfileView> byPractitioner(UUID practitionerId).
- directory.api.PublishedProfileView : practitionerId, tenantId, slug, displayName. Rien d'autre : ni coordonnées, ni photo.
- Les deux méthodes ne renvoient une vue que si le profil est publié ET le praticien approuvé (verification_status = approved, lu par PractitionerFacade, comme Q48 (2)). Slug inconnu, profil non publié, praticien suspendu, rejeté ou clos : Optional vide, sans distinction.
- Implémentation directory.service.PublishedProfileFacadeImpl, transaction en lecture seule.
- Aucune route, aucun DTO web, aucun changement du contrat.

Périmètre : src/main/java/com/psymate/directory/api/**, directory/service/PublishedProfileFacadeImpl.java, une méthode de lecture ajoutée à PractitionerProfileRepository si nécessaire, leurs tests.

Critères d'acceptation (PublishedProfileFacadeIT) :
- profil publié d'un praticien approuvé → vue avec les quatre champs, par slug et par identifiant ;
- slug inconnu → vide ; profil existant mais non publié → vide ; profil publié d'un praticien suspendu → vide ;
- ModularityTests et ArchitectureTests verts ; directory ne dépend toujours que d'identity :: api, events :: api et shared ;
- ContractIT vert, contrat inchangé.

Tests : IT Testcontainers.

Interdits : exposer une entité ou un repository dans api ; ajouter un champ à la vue ; toute dépendance de directory vers agenda ; route ; migration ; dépendance Maven.

Livrable : PR « M14-03 PublishedProfileFacade », puis make log TASK=M14-03 …
```

### M14-04 — Migration V15, origine, e-mail de l'invité, demandes en attente

```text
Tâche M14-04 — Agenda : migration V15, origine d'un rendez-vous, e-mail de l'invité, liste des demandes en attente, entité BookingRequest (dépôt psymate-api, module agenda). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K2 Q74, Q82 ; K4 DDL ; K5.2), docs/I-plan-agenda-api.md I8 (rendez-vous invité, GuestPurgeJob), docs/E-modele-donnees.md E3.14, E4, E9. Code : agenda/model/Appointment.java, agenda/dto (GuestDto, GuestRequest, AppointmentDto, AppointmentMapper, AppointmentAssembler), agenda/service/AppointmentService.java et GuestPurgeJob.java, agenda/controller (AgendaController, AppointmentForbiddenFieldsAdvice), agenda/repository/AppointmentRepository.java, V13__agenda_guest.sql, les IT AppointmentGuestIT et GuestPurgeJobIT.

Objectif :
- src/main/resources/db/migration/V15__agenda_booking.sql : le bloc SQL de K4, identique octet pour octet. V12 et V13 ne sont pas touchées.
- Appointment : guestEmail (nullable) et origin (enum AppointmentOrigin : PRACTITIONER, ONLINE ; défaut PRACTITIONER). Un rendez-vous créé par POST /api/appointments a toujours origin = practitioner.
- GuestRequest.email (facultatif, 320 au plus, format e-mail, chaîne vide = aucun ; INVALID_FORMAT, OUT_OF_RANGE sur guest.email) à la création et au PATCH (absent ou null = inchangé, chaîne vide = effacé, comme phone). GuestDto.email (null si inconnu ou purgé). AppointmentDto.origin ("practitioner" | "online"). origin est refusé en écriture (UNKNOWN_FIELD) à la création et au PATCH.
- attach-patient vide aussi guest_email. Les séries recopient guest_email.
- GuestPurgeJob : met aussi guest_email à NULL. Deux délais : app.agenda.guest-purge.retention (P180D, inchangé) et app.agenda.guest-purge.online-cancelled-retention (P30D) pour les rendez-vous origin = online ET status = cancelled, comptés depuis cancelled_at (Q82 c).
- GET /api/agenda/requests (AgendaController) : rendez-vous du praticien courant avec status = tentative, origin = online, starts_at >= maintenant, tri starts_at puis id, 100 au plus. Réponse { appointments: [AppointmentDto…] } (nouveau DTO BookingRequestsDto ou réemploi d'un DTO de liste existant s'il a exactement cette forme).
- Entité agenda/model/BookingRequest (toutes les colonnes de K4, UUID nus, aucune association JPA) et agenda/repository/BookingRequestRepository (vide de méthodes métier : elles viennent avec M14-06). TableOwnershipTests : booking_request → agenda.
- AppointmentExportView et les exports ne changent pas dans cette tâche.
- Contrat regénéré, app.contract.version = 0.15.0.

Périmètre : src/main/java/com/psymate/agenda/**, V15__agenda_booking.sql, application.yml (une clé), leurs tests, messages (sous « # agenda (M14-04) »), contract/openapi.yaml (généré), TableOwnershipTests (une ligne).

Critères d'acceptation (AppointmentGuestEmailIT, AgendaRequestsIT, GuestPurgeJobIT complété) :
- POST avec guest { lastName, email: "sara@example.test" } → 201, guest.email renvoyé, origin = "practitioner" ; email "abc" → 422 INVALID_FORMAT sur guest.email ; origin dans le corps → 422 UNKNOWN_FIELD ;
- PATCH { guest: { lastName: "Benali", email: "" } } efface l'e-mail ; e-mail absent → inchangé ;
- attach-patient → guest_email NULL ;
- GET /api/agenda/requests ne renvoie que les rendez-vous tentative d'origine online à venir du praticien courant (un tentative créé par le praticien, un online déjà planned, un online passé, un online d'un autre praticien n'y figurent pas), triés ;
- purge : rendez-vous online annulé il y a 31 jours → guest_* et guest_email NULL, guest_purged_at posé ; annulé il y a 29 jours → intact ; rendez-vous online qui a eu lieu il y a 5 mois → intact, il y a 7 mois → purgé ; rendez-vous practitioner annulé il y a 31 jours → intact (règle des 6 mois) ;
- FlywayMigrationIT (V15), TableOwnershipTests, ArchitectureTests verts ; AppointmentIT, AppointmentGuestIT, AgendaQueryIT, AppointmentSeriesIT et les IT d'export restent verts ;
- contrat 0.15.0 regénéré, ContractIT vert.

Tests : IT Testcontainers par endpoint ; unitaire pour la règle de l'e-mail.

Interdits : modifier V12 ou V13 ; tout DELETE sur appointment ; toute route publique (M14-05, M14-06) ; toute lecture de préférences booking.* ; dépendance Maven ; tout import hors identity.api, patients.api, events.api, shared.

Livrable : PR « M14-04 Agenda : origine, e-mail de l'invité, demandes en attente », puis make log TASK=M14-04 …
```

### M14-05 — Créneaux publics

```text
Tâche M14-05 — Prise de rendez-vous en ligne : réglages publics et créneaux offerts (dépôt psymate-api, module agenda ; une ligne dans directory/config et dans application.yml). Utilise l'agent backend-module-dev. Dépend de M14-02, M14-03 et M14-04.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K2 Q71, Q75, Q76, Q78 ; K5.1), docs/G-decisions.md Q24 (limitation de taux), Q54. Code : directory/config/PublicSecurityConfig.java (chaîne /api/public/**, règles public-directory et public-contact), application.yml (app.rate-limits.rules), directory/controller/PublicContactController.java (modèle d'un contrôleur public documenté), directory/api/PublishedProfileFacade.java (M14-03), identity/api/PractitionerFacade.preferencesOf, agenda/service/AgendaQueryService.java (lecture des rendez-vous actifs d'une fenêtre), agenda/package-info.java.

Objectif :
- agenda/package-info.java : allowedDependencies += "directory :: api". Le commentaire du module est mis à jour.
- agenda/service/BookingSettings (record) et BookingSettingsReader : lit les huit clés booking.* par PractitionerFacade.preferencesOf et les rend typées (ZoneId, List<Window>, Set<AppointmentModality>…), avec les valeurs par défaut si une clé manque.
- agenda/service/BookingAvailability : « offerte » = profil publié et praticien approuvé (PublishedProfileFacade), booking.enabled = true, au moins une plage (Q75).
- agenda/service/SlotCalculator (pur, testable sans base) : à partir des réglages, d'un intervalle de dates, de l'instant courant et des intervalles occupés, rend les créneaux offerts selon Q76 : découpe de chaque plage en créneaux de slotDurationMin depuis son début, dans le fuseau du praticien (une plage qui ne contient pas un créneau entier n'en donne aucun ; le reste en fin de plage est ignoré) ; début >= maintenant + minNoticeHours ; début <= fin du jour (aujourd'hui + horizonDays) dans le fuseau ; aucun chevauchement avec un rendez-vous actif (planned, tentative ; les annulés et les done ne comptent pas). Les changements d'heure sont traités par java.time (un créneau qui n'existe pas le jour du passage à l'heure d'été n'est pas offert).
- agenda/controller/PublicBookingController, @RequestMapping("/api/public/booking") :
  - GET /practitioners/{slug} → PublicBookingDto { slug, displayName, confirmation, slotDurationMin, modalities, timezone, minNoticeHours, horizonDays } ;
  - GET /practitioners/{slug}/slots?from&to → PublicSlotsDto { timezone, slots: [PublicSlotDto { startsAt, endsAt }] }, instants UTC triés ; from et to sont des LocalDate dans le fuseau du praticien, to inclus ; from absent = aujourd'hui, to absent = from + 13 jours ; to avant from → 422 INVALID_VALUE sur to ; plus de 31 jours → 422 WINDOW_TOO_LARGE ;
  - 404 BOOKING_NOT_AVAILABLE dans tous les cas de Q78, avec le même corps ;
  - Cache-Control: no-store sur les deux routes.
- Les créneaux ne portent rien d'autre que début et fin : ni identifiant, ni raison, ni nombre de places.
- Règle de taux public-booking (POST /api/public/booking/practitioners/*/requests, par IP, 5 par heure) déclarée dans application.yml et posée dans PublicSecurityConfig à la suite de public-contact : elle sert M14-06 mais se déclare ici pour ne toucher directory qu'une fois.
- Documentation OpenAPI complète des deux routes (description, exemples, erreurs), comme PublicContactController. Contrat regénéré.

Périmètre : src/main/java/com/psymate/agenda/** (fichiers nouveaux préfixés Booking, PublicBooking ou Slot), agenda/package-info.java, directory/config/PublicSecurityConfig.java (une ligne), application.yml (une règle), leurs tests, messages (sous « # booking (M14-05) »), contract/openapi.yaml (généré).

Critères d'acceptation (SlotCalculatorTest, PublicBookingSlotsIT) :
- plage lundi 09:00–12:00, créneaux de 60 min → trois créneaux 09:00, 10:00, 11:00 heure de Paris, renvoyés en UTC ; créneaux de 45 min → 09:00, 09:45, 10:30, 11:15 ; plage 09:00–09:30 avec des créneaux de 60 min → aucun ;
- un rendez-vous planned de 10:00 à 10:50 retire le créneau de 10:00 et laisse 09:00 et 11:00 ; un tentative le retire aussi ; un cancelled ne retire rien ; un rendez-vous de 09:30 à 10:30 retire 09:00 et 10:00 ;
- minNoticeHours = 24 : un créneau qui commence dans 23 h n'est pas offert, dans 25 h il l'est ; horizonDays = 7 : rien au-delà ;
- fuseau America/Montreal : les instants UTC renvoyés correspondent aux heures locales de Montréal ;
- le dernier dimanche de mars, une plage 02:00–04:00 ne produit pas de créneau à une heure qui n'existe pas ;
- booking.enabled = false, zéro plage, profil non publié, praticien suspendu, slug inconnu → 404 BOOKING_NOT_AVAILABLE, corps identiques (le test les compare) ;
- la réponse des créneaux ne contient aucun champ hors startsAt et endsAt, et aucun créneau occupé ;
- fenêtre de 32 jours → 422 WINDOW_TOO_LARGE ; 61 requêtes en une minute depuis une IP → 429 ;
- les deux routes répondent sans session et sans cookie ; aucun en-tête Set-Cookie ;
- ModularityTests vert avec agenda → directory :: api ; directory n'importe rien d'agenda ; ContractIT vert.

Tests : unitaires nombreux pour SlotCalculator (c'est le cœur) ; IT Testcontainers pour les deux routes.

Interdits : exposer un rendez-vous, un nom ou un créneau occupé ; lire practitioner_profile en SQL depuis agenda ; écrire quoi que ce soit en base ; POST public (M14-06) ; toute autre modification de directory ; dépendance Maven.

Livrable : PR « M14-05 Créneaux publics », puis make log TASK=M14-05 …
```

### M14-06 — Demande, vérification, rendez-vous, lien de gestion

```text
Tâche M14-06 — Prise de rendez-vous en ligne : demande, vérification de l'e-mail, création du rendez-vous, lien de gestion, annulation par le demandeur (dépôt psymate-api, module agenda). Utilise l'agent backend-module-dev. Dépend de M14-05.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K2 Q72 à Q74, Q77 à Q82 ; K3.3, K3.4 ; K4 ; K5.1), docs/G-decisions.md Q38 (SQL natif par fragment *NativeQueries), Q49 (réponses qui n'énumèrent rien). Code : directory/service/ContactRequestService.java (pot de miel, délai de 3 s, enregistrement puis envoi après validation de la transaction sur un pool privé, échec d'envoi jamais remonté) et ContactMailer.java ; identity/auth/service/AuthMailer.java (gabarits, app.web.base-url, langue) et la façon dont identity hache ses jetons ; agenda/service/AppointmentService.java (création d'un rendez-vous invité, événements) ; agenda/model/BookingRequest.java et V15 (M14-04) ; agenda/service/SlotCalculator, BookingSettingsReader, BookingAvailability (M14-05) ; shared/ratelimit.

Objectif :
- POST /api/public/booking/practitioners/{slug}/requests, corps CreateBookingRequest { startsAt (instant UTC), modality, firstName (requis, 100), lastName (requis, 100), email (requis, format, 320), phone? (30, règle de guest.phone), locale? ("fr" | "en", défaut "fr"), consent (doit valoir true), website (pot de miel), formShownAt (requis) }.
  - Ordre : validation de forme (422 par champ, codes REQUIRED, INVALID_FORMAT, OUT_OF_RANGE, INVALID_VALUE) ; 404 BOOKING_NOT_AVAILABLE si la fonction n'est pas offerte ; 409 SLOT_NOT_AVAILABLE si startsAt n'est pas le début d'un créneau offert (SlotCalculator) ou si modality n'est pas dans booking.modalities ; puis 202 sans corps.
  - Sous le 202, ignoré en silence, sans ligne ni e-mail : pot de miel rempli ; formulaire soumis en moins de 3 s ou dans le futur ; quatrième demande dans l'heure pour la même empreinte d'e-mail (Q79, comptée sur booking_request.email_hash).
  - Sinon : ligne booking_request (identité en clair, email_hash = HMAC-SHA-256 de l'adresse en minuscules sans blancs avec le secret app.booking.hash-secret, verify_token_hash = SHA-256 d'un jeton aléatoire de 256 bits, verify_expires_at = maintenant + app.booking.verify-ttl, défaut PT30M), puis, après validation de la transaction et hors du fil de la requête, e-mail de vérification au demandeur avec le lien {app.web.base-url}/rendez-vous/confirmer/{jeton}. Aucun rendez-vous, aucun e-mail au praticien.
- POST /api/public/booking/requests/verify { token } :
  - jeton inconnu → 404 BOOKING_TOKEN_INVALID ; expiré et non vérifié → 410 BOOKING_TOKEN_EXPIRED ;
  - déjà vérifié → 200 avec l'état courant du même rendez-vous (idempotent), sans nouveau jeton de gestion : manageToken est absent de cette seconde réponse ;
  - sinon, dans une seule transaction, sous un verrou consultatif transactionnel par praticien (pg_advisory_xact_lock, fragment BookingNativeQueries, G Q38) : 404 BOOKING_NOT_AVAILABLE si la fonction n'est plus offerte ; 409 BOOKING_ALREADY_ACTIVE s'il existe déjà un rendez-vous origin = online, actif, à venir, du même praticien avec le même guest_email (casse et blancs ignorés) ; 409 SLOT_TAKEN si le créneau n'est plus offert (Q77) ; sinon création du rendez-vous invité (guest_* depuis la demande, guest_email, origin = online, modalité et horaires de la demande, statut tentative si booking.confirmation = manual, planned si auto), événement AppointmentPlanned, puis booking_request : verified_at, appointment_id, manage_token_hash, et first_name, last_name, email, phone mis à NULL.
  - Réponse 200 PublicAppointmentDto avec manageToken (le seul moment où il est renvoyé).
  - Après validation de la transaction : e-mail au demandeur (« demande transmise » en manual, « rendez-vous confirmé » en auto) avec le lien {app.web.base-url}/rendez-vous/gerer/{jeton de gestion} ; e-mail au praticien (adresse du compte, sa langue) : « nouvelle demande » ou « nouveau rendez-vous », avec le nom du demandeur, la date et un lien vers /agenda.
  - Sur 409 SLOT_TAKEN et BOOKING_ALREADY_ACTIVE, la demande reste non vérifiée : elle sera supprimée par la purge.
- POST /api/public/booking/manage { token } → 200 PublicAppointmentDto ; jeton inconnu → 404 BOOKING_TOKEN_INVALID.
- POST /api/public/booking/manage/cancel { token } : annule le rendez-vous (status cancelled, cancelled_at, cancel_reason = libellé i18n « Annulé par le demandeur »), booking_request.closed_by = visitor, événement AppointmentCancelled, e-mail au praticien. Déjà annulé → 200, sans second e-mail. Rendez-vous commencé ou done → 409 APPOINTMENT_NOT_ACTIVE.
- PublicAppointmentDto { status, cancelledBy?, startsAt, endsAt, durationMin, modality, practitioner { slug, displayName }, canCancel, manageToken? } : status = pending (tentative), confirmed (planned, à venir), cancelled, past (planned ou done dont la fin est passée) ; cancelledBy lu dans closed_by ; canCancel vrai si pending ou confirmed. practitioner est lu par PublishedProfileFacade.byPractitioner ; si le profil n'est plus publié, slug vaut null et displayName reste celui de PractitionerFacade.
- Quand le praticien annule un rendez-vous en ligne (POST /api/appointments/{id}/cancel), booking_request.closed_by = practitioner (les e-mails viennent avec M14-07).
- Gabarits d'e-mail fr et en (BookingMailer), objets neutres (Q81) : « Confirmez votre demande de rendez-vous », « Votre demande de rendez-vous », « Votre rendez-vous ». Le corps porte le nom du praticien, la date et l'heure dans le fuseau du praticien avec le nom du fuseau, la durée, la modalité, le lien, et la phrase « PsyMate n'est pas un service d'urgence ». Jamais la note de planification.
- Propriétés : app.booking.hash-secret (variable d'environnement APP_BOOKING_HASH_SECRET, valeur d'exemple non réelle par défaut pour le développement et les tests), app.booking.verify-ttl.
- Documentation OpenAPI complète des quatre routes. Contrat regénéré.

Périmètre : src/main/java/com/psymate/agenda/** (fichiers Booking*, PublicBooking* ; AppointmentService pour closed_by seulement), application.yml, gabarits d'e-mail et messages (sous « # booking (M14-06) »), leurs tests, contract/openapi.yaml (généré).

Critères d'acceptation (PublicBookingRequestIT, PublicBookingVerifyIT, PublicBookingManageIT, BookingRulesTest) :
- demande bien formée sur un créneau offert → 202, une ligne booking_request non vérifiée, un e-mail au demandeur dont l'objet ne contient ni le nom du praticien ni sa profession, aucun rendez-vous, aucun e-mail au praticien ;
- pot de miel rempli ou formShownAt il y a 1 s → 202, aucune ligne, aucun e-mail ; quatrième demande de la même adresse dans l'heure → 202, aucun e-mail ; sixième création de la même IP dans l'heure → 429 ;
- startsAt qui n'est pas un créneau offert → 409 SLOT_NOT_AVAILABLE ; modalité non offerte → 409 SLOT_NOT_AVAILABLE ; consent false → 422 ; e-mail invalide → 422 INVALID_FORMAT sur email ; slug inconnu ou fonction désactivée → 404 BOOKING_NOT_AVAILABLE ;
- deux demandes identiques au nom près, l'une pour une adresse qui a déjà un rendez-vous chez ce praticien, l'autre non : réponses HTTP identiques (statut, corps, en-têtes hors date) ;
- vérification en mode manual → 200 status pending, rendez-vous invité tentative, origin online, guest_email renseigné, AppointmentPlanned publié, identité à NULL dans booking_request, un e-mail au demandeur avec un lien de gestion, un e-mail au praticien ; en mode auto → status confirmed, rendez-vous planned ;
- le mode appliqué est celui du moment de la vérification (demande créée en manual, préférence passée à auto, vérification → planned) ;
- second appel de verify avec le même jeton → 200, même rendez-vous, pas de second rendez-vous ni de second e-mail, pas de manageToken ;
- jeton inconnu → 404 ; jeton vieux de 31 min → 410 ; les jetons n'apparaissent jamais en clair en base (le test relit la ligne) ni dans les journaux ;
- deux demandes vérifiées pour le même créneau, l'une après l'autre : la seconde → 409 SLOT_TAKEN, un seul rendez-vous ; les deux en parallèle (deux fils) : exactement un rendez-vous ;
- un rendez-vous posé par le praticien sur le créneau entre la demande et la vérification → 409 SLOT_TAKEN ;
- même adresse, second créneau chez le même praticien alors que le premier rendez-vous est actif et à venir → 409 BOOKING_ALREADY_ACTIVE ; chez un autre praticien → accepté ;
- manage → état correct pour pending, confirmed, cancelled (avec cancelledBy), past ; manage/cancel → rendez-vous cancelled, closed_by visitor, AppointmentCancelled, un e-mail au praticien ; second cancel → 200 sans e-mail ; rendez-vous passé → 409 ;
- PublicAppointmentDto ne contient ni nom, ni e-mail, ni téléphone du demandeur, ni planningNote (le test inspecte le JSON) ;
- un échec du serveur d'envoi ne change aucune réponse HTTP ;
- GET /api/agenda et GET /api/agenda/requests du praticien montrent la demande après vérification, jamais avant ;
- ModularityTests, ArchitectureTests, TableOwnershipTests, ContractIT verts.

Tests : unitaires pour les règles (états, idempotence, empreinte) ; IT Testcontainers pour chaque route, avec lecture des e-mails comme le font les IT des mailers existants.

Interdits : créer un patient ; écrire dans une table d'un autre module ; journaliser un nom, une adresse, un téléphone ou un jeton ; renvoyer une réponse qui dépend de l'identité saisie avant la vérification ; retenir un créneau avant la vérification ; un champ libre de motif ; appeler un service externe ; dépendance Maven.

Livrable : PR « M14-06 Demande en ligne, vérification et lien de gestion », puis make log TASK=M14-06 …
```

### M14-07 — E-mails de suivi, expiration, purges

```text
Tâche M14-07 — Prise de rendez-vous en ligne : e-mails de suivi au demandeur, expiration des demandes sans réponse, purges (dépôt psymate-api, module agenda). Utilise l'agent backend-module-dev. Dépend de M14-06.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K2 Q81, Q82 ; K3.3 ; K3.5), docs/E-modele-donnees.md E9. Code : agenda/service/AppointmentService.java (confirm, cancel, update, attach-patient et leurs événements), BookingMailer et le pool d'envoi (M14-06), agenda/service/GuestPurgeJob.java et directory/service/ContactPurgeJob.java (modèle d'une tâche planifiée : planificateur privé, cron en propriété, run() appelable par les tests, journal du nombre de lignes seulement).

Objectif :
- E-mails au demandeur, pour un rendez-vous origin = online dont guest_email n'est pas NULL, envoyés après validation de la transaction et hors du fil de la requête :
  - confirm (tentative → planned) : « Votre rendez-vous est confirmé » ;
  - cancel d'un tentative par le praticien : « Votre demande n'a pas pu être acceptée », sans motif ;
  - cancel d'un planned par le praticien : « Votre rendez-vous est annulé », sans motif ;
  - PATCH qui change startsAt ou la durée : « Votre rendez-vous est déplacé », ancienne et nouvelle date ;
  - PATCH qui ne change que la note ou l'identité de l'invité : aucun e-mail.
  Objets neutres (Q81), lien de gestion rappelé quand le rendez-vous reste actif, langue = booking_request.locale. Le cancel_reason du praticien n'est jamais transmis.
- Le lien de gestion ne peut pas être reconstruit (seule son empreinte est stockée) : chaque e-mail de suivi émet un nouveau jeton de gestion et remplace manage_token_hash. Le lien le plus récent est le seul valable ; les gabarits le disent en une phrase.
- BookingExpiryJob (cron app.booking.expiry.cron, défaut toutes les 15 min ; app.booking.pending-ttl, défaut PT72H) : annule les rendez-vous origin = online, status = tentative, dont la vérification date de plus de pending-ttl ou dont starts_at est passé ; cancel_reason = libellé i18n « Demande expirée », booking_request.closed_by = expired, événement AppointmentCancelled, e-mail « Votre demande a expiré » au demandeur. run() appelable par les tests ; idempotent.
- BookingPurgeJob (cron app.booking.purge.cron, défaut 0 45 3 * * * UTC) : supprime les lignes booking_request non vérifiées créées depuis plus de app.booking.unverified-retention (PT24H), et les lignes vérifiées dont le rendez-vous est terminé ou annulé depuis plus de app.booking.closed-retention (P30D). Journalise des nombres, jamais d'identité.
- E9 est déjà à jour (M14-01) : le code doit lui correspondre exactement.

Périmètre : src/main/java/com/psymate/agenda/**, application.yml, gabarits et messages (sous « # booking (M14-07) »), leurs tests.

Critères d'acceptation (BookingNotificationsIT, BookingExpiryJobIT, BookingPurgeJobIT) :
- confirmer une demande en ligne → un e-mail au demandeur, objet neutre, dans sa langue ; confirmer un tentative créé par le praticien → aucun e-mail ;
- refuser une demande → e-mail de refus qui ne contient pas le motif saisi par le praticien ; annuler un rendez-vous planned en ligne → e-mail d'annulation ;
- déplacer → e-mail avec les deux dates ; changer la note → aucun e-mail ; invité sans e-mail ou rattaché à un patient → aucun e-mail ;
- après un e-mail de suivi, l'ancien lien de gestion répond 404 BOOKING_TOKEN_INVALID et le nouveau fonctionne ;
- expiration : demande vérifiée il y a 73 h → cancelled, closed_by expired, e-mail ; il y a 71 h → intacte ; créneau commencé → expirée ; une demande déjà confirmée n'expire jamais ; second passage → aucun effet, aucun e-mail ;
- purge : demande non vérifiée de 25 h → ligne supprimée, de 23 h → conservée ; demande vérifiée dont le rendez-vous est annulé depuis 31 jours → ligne supprimée, le rendez-vous reste ; depuis 29 jours → conservée ; aucune ligne appointment n'est jamais supprimée ;
- un échec du serveur d'envoi ne fait échouer ni confirm, ni cancel, ni PATCH, ni les tâches planifiées ;
- ContractIT vert, contrat inchangé ; tous les IT de l'agenda restent verts.

Tests : IT Testcontainers ; horloge injectée pour les délais.

Interdits : transmettre le motif d'annulation ou la note de planification ; envoyer un e-mail pour un rendez-vous d'origine practitioner ; tout DELETE sur appointment ; SMS ; rappel avant le rendez-vous ; dépendance Maven ; service externe.

Livrable : PR « M14-07 E-mails de suivi, expiration et purges », puis make log TASK=M14-07 …
```

### M14-08 — Seed, tag de contrat, suivi

```text
Tâche M14-08 — Seed de la prise de rendez-vous en ligne, tag du contrat, suivi (dépôt psymate-api, tools/seed et racine). Utilise l'agent seed-perf. Dépend de M14-07.

Contexte : lis CLAUDE.md, docs/K-plan-prise-de-rendez-vous-api.md (K4, K5, K6), la PR de M13-03 (seed et tag de contrat du jalon précédent), tools/seed (comment le seed pose les préférences agenda.* et les rendez-vous invités), le Makefile (make seed, make contract).

Objectif :
- Seed, tailles small et au-delà : le praticien de démonstration au profil publié (le compte « claire » de tools/seed, Accounts.java et DemoStory.java ; relève son slug réel et cite-le dans la PR) a booking.enabled = true, booking.confirmation = manual, des plages du lundi au vendredi 09:00–12:00 et 14:00–18:00, créneaux de 60 min, délai 24 h, horizon 28 jours, modalités in_person et video. Un second praticien publié est en mode auto. Les autres n'ont rien d'activé.
- Pour ce praticien : deux demandes en ligne en attente (rendez-vous invités tentative, origin online, guest_email en example.test, avec leur ligne booking_request vérifiée) dans les sept jours à venir, et un rendez-vous en ligne confirmé. Le seed reste déterministe et rejouable.
- Vérification finale du contrat : make contract ne produit aucun diff ; app.contract.version = 0.15.0 ; tag contract-v0.15.0 posé sur le commit de fusion (tools/tag-contract.sh, comme M13-03).
- docs/couverture-ecrans.md et le suivi sont mis à jour ; make log.

Périmètre : tools/seed/**, Makefile si nécessaire, docs/couverture-ecrans.md, docs/progress (événement nouveau seulement).

Critères d'acceptation :
- après make db-reset && make seed SIZE=small : GET /api/public/booking/practitioners/<slug du praticien de démonstration> → 200 ; …/slots renvoie des créneaux ; GET /api/agenda/requests de ce praticien renvoie les deux demandes ;
- le seed rejoué deux fois donne le même résultat ;
- ./mvnw verify vert ; ContractIT vert ; le tag existe et pointe sur un contrat 0.15.0.

Interdits : adresses e-mail ou téléphones réels ; modification du code de production ; modification d'une migration.

Livrable : PR « M14-08 Seed et tag contract-v0.15.0 », puis make log TASK=M14-08 AGENT=seed-perf STATUS=done SUMMARY="…" PR=<url>.
```

## Points ouverts (K10 du document du web, repris tels quels)

Tranché par l'utilisateur le 22 novembre 2026 :

- **Valeurs par défaut** : lien de vérification 30 minutes ; expiration d'une demande 72 h ; délai minimal 24 h ; horizon 28 jours ; créneau de 60 minutes ; conservation 24 h, 30 jours, 6 mois (K3.5).
- **Prénom obligatoire** pour le demandeur, alors qu'il est facultatif pour un invité saisi par le praticien.

Encore à trancher par l'utilisateur (aucune proposition n'était faite, la validation ne les couvre pas) :

- **Numéros d'urgence** affichés (Q63 du G de psymate-web) : lesquels, et pour quels pays. À fournir avant la validation des maquettes de M14-09 ; ne bloque aucune tâche API.
- **Nom d'expéditeur des e-mails.** « PsyMate » dans la boîte de réception dit déjà quelque chose à qui partage la boîte. Un nom d'expéditeur neutre est possible, au prix d'e-mails moins reconnaissables. En attendant, M14-06 garde l'expéditeur actuel de l'API.

À faire valider par un juriste ou un DPO (aucune tâche ne le tranche) :

- **Rôles au sens du RGPD** : PsyMate sous-traitant du praticien pour ses rendez-vous, responsable pour l'annuaire ; mention d'information du formulaire et texte du consentement.
- **Hébergement de données de santé** si la France est visée : une demande de rendez-vous chez un psychologue est une donnée de santé.
- **Durées de conservation** de K3.5.
- **Mineurs** : aucune date de naissance n'est demandée ; la question de l'autorité parentale reste entre le praticien et le demandeur.

Reporté après M14 :

- Plafond de demandes en attente par praticien, si des abus apparaissent malgré la vérification de l'e-mail.
- Liste de blocage côté praticien (adresse ou téléphone).
- Rappel automatique avant le rendez-vous ; SMS.
- Congés et jours fériés ; plusieurs durées ou types de consultation ; « premier rendez-vous » distinct du suivi ; acceptation automatique par type de rendez-vous.
- Déplacement du rendez-vous par le demandeur.
- `nextSlotAt` de la fiche publique calculé depuis les créneaux offerts (aujourd'hui saisi à la main par le praticien).
- Les rendez-vous en ligne dans l'export du compte : colonne « Origine » et e-mail de l'invité dans `rendez-vous.md` (G Q61).
- Synchronisation Google (M12) : les créneaux offerts devront tenir compte des plages occupées de Google.
