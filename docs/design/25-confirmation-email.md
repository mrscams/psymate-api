# Écran 25 · Confirmation d'e-mail

> Spécification pour agents. Maquette : `25-confirmation-email.html` / `.png` (deux états côte à côte : en attente, lien expiré). Palette : Nordique, layout public. Module `identity` (`verification_status = email_pending`).

## Objectif
Faire franchir l'unique étape bloquante après l'inscription : tant que l'e-mail n'est pas confirmé, rien n'est accessible. Dès qu'il l'est, l'outil de suivi s'ouvre (la vérification des diplômes est un parcours séparé, non bloquant).

## Route & module
- Routes : `/confirmer-email` (état « en attente », affiché après l'inscription et à chaque connexion d'un compte `email_pending`) ; `/confirmer-email/{token}` (traitement du lien) ; `/confirmer-email/expire` (état « expiré »).
- API :
  - `POST /api/auth/email/confirm` `{ token }` → `204` + session ouverte → redirection vers `/patients` (liste vide, écran 1). Token : 32 octets aléatoires, haché en base, **24 h**, usage unique.
  - `POST /api/auth/email/resend` `{ email }` → toujours `202` (ne révèle pas l'existence d'un compte) ; limite 1 / minute / compte, 5 / jour ; chaque renvoi invalide le précédent.
  - `POST /api/auth/email/change` `{ newEmail }` (session `email_pending` uniquement) → nouveau token, ancien invalidé.
  - Compte déjà confirmé (`docs_pending`, `under_review`, `needs_more`, `approved`) : la même route, **avec session**, `{ newEmail, password }` → `202`, lien de 24 h envoyé à la **nouvelle** adresse ; l'adresse ne bascule qu'à `POST /api/auth/email/confirm`, qui révoque les autres sessions et prévient l'ancienne adresse (M10-04, G Q49, fiche 18).
- Tant que `email_pending` : toutes les routes `/api/**` hors `/api/auth/**` → `403 EMAIL_NOT_CONFIRMED` ; le client redirige vers `/confirmer-email`.

## Structure
Deux cartes (520 px) sur fond `#EEF1F6`, bandeau public avec « Connecté·e en tant que … · Se déconnecter » :
1. **En attente** : icône enveloppe (`#E3EAF8`), titre « Un dernier clic : confirmez votre e-mail », adresse en monospace, validité 24 h, 3 étapes (boîte mail + indésirables, cliquer, arrivée sur la liste vide), bouton « Renvoyer l'e-mail » + compte à rebours « Nouveau renvoi possible dans 0:42 », lien « Corriger l'e-mail », rappel que la vérification des diplômes est indépendante.
2. **Lien expiré** : icône horloge (`#FBF1DC`), titre « Ce lien de confirmation a expiré », date d'émission, champ e-mail + « Recevoir un nouveau lien », note « nous ne confirmons pas l'existence d'un compte », lien « Se connecter ».

## Interactions
- Compte à rebours de 60 s après chaque renvoi ; bouton désactivé pendant ce temps (`aria-disabled` + texte).
- Lien ouvert dans un autre navigateur que celui de l'inscription : la confirmation fonctionne et **ouvre une session** dans ce navigateur (pas de redemande de mot de passe) ; l'événement est journalisé.
- Token déjà utilisé → même page « expiré » avec un texte adapté (« Ce lien a déjà servi · Se connecter »).
- « Corriger l'e-mail » → champ en place ; après validation, la carte affiche la nouvelle adresse.
- Polling discret (`GET /api/auth/session` toutes les 10 s, max 30 min) : si l'e-mail est confirmé dans un autre onglet, cette page redirige d'elle-même.

## Tokens
- Icônes sur fonds `#E3EAF8` (info) et `#FBF1DC` (expiré) ; texte secondaire `#5A6679` ; adresse e-mail en `JetBrains Mono` 13 px.
- Compte à rebours en monospace `#5A6679`.

## Accessibilité
- Un `<h1>` par carte (la page réelle n'affiche qu'une carte à la fois) ; `role="status"` sur le compte à rebours et le message « e-mail renvoyé ».
- Le bouton « Renvoyer » désactivé reste focusable et explique pourquoi.

## Critères d'acceptation
- [ ] Compte `email_pending` : `GET /api/patients` → `403 EMAIL_NOT_CONFIRMED` ; le client affiche l'écran 25 ; après confirmation, `/patients` répond `200`.
- [ ] Token valide → `204`, session ouverte, statut `docs_pending`, redirection vers la liste des patients avec bandeau « Déposez vos justificatifs quand vous voulez ».
- [ ] Token de plus de 24 h ou déjà consommé → page « expiré », aucune session créée.
- [ ] Deux renvois en moins d'une minute → second refusé (`429`) côté API, bouton désactivé côté client.
- [ ] `resend` sur un e-mail inconnu → `202`, aucun e-mail envoyé, même temps de réponse ± 50 ms (anti-énumération).
- [ ] Changement d'e-mail → ancien token refusé, nouveau token accepté.

## Hors périmètre V1
Connexion par lien magique, vérification par SMS.
