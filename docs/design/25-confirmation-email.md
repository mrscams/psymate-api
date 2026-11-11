# Écran 25 · Confirmation d'e-mail

> Spécification pour agents. Maquette : `25-confirmation-email.html` / `.png` (deux états côte à côte : en attente, lien expiré). Palette : Nordique, layout public. Module `identity` (`verification_status = email_pending`).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : page **sans session** (un compte `email_pending` ne se connecte pas : `403 EMAIL_NOT_CONFIRMED`, `register` n'ouvre aucune session) ; un seul geste « Renvoyer le lien », précédé du champ « Adresse du compte » quand la page ne connaît pas l'adresse ; « Corriger l'e-mail » seulement quand l'adresse est connue (variante publique de `email/change` avec mot de passe) ; pas de date d'émission sur l'état expiré. Maquette HTML corrigée.

## Objectif
Faire franchir l'unique étape bloquante après l'inscription : tant que l'e-mail n'est pas confirmé, rien n'est accessible. Dès qu'il l'est, l'outil de suivi s'ouvre (la vérification des diplômes est un parcours séparé, non bloquant).

## Route & module
- Routes : `/confirmer-email` (état « en attente », affiché après l'inscription et à chaque connexion d'un compte `email_pending`) ; `/confirmer-email/{token}` (traitement du lien) ; `/confirmer-email/expire` (état « expiré »).
- API :
  - `POST /api/auth/email/confirm` `{ token }` → `204` + session ouverte → redirection vers `/patients` (liste vide, écran 1). Token : 32 octets aléatoires, haché en base, **24 h**, usage unique.
  - `POST /api/auth/email/resend` `{ email }` → toujours `202` (ne révèle pas l'existence d'un compte) ; limite 1 / minute / compte, 5 / jour ; chaque renvoi invalide le précédent.
  - `POST /api/auth/email/change` **sans session** `{ email, password, newEmail }` (compte `email_pending`) → toujours `202` ; nouveau lien à la nouvelle adresse, ancien invalidé ; mots de passe faux comptés dans le verrou e-mail + IP du login (`423`).
  - Compte déjà confirmé (`docs_pending`, `under_review`, `needs_more`, `approved`) : la même route, **avec session**, `{ newEmail, password }` → `202`, lien de 24 h envoyé à la **nouvelle** adresse ; l'adresse ne bascule qu'à `POST /api/auth/email/confirm`, qui révoque les autres sessions et prévient l'ancienne adresse (M10-04, G Q49, fiche 18).
- Tant que `email_pending` : toutes les routes `/api/**` hors `/api/auth/**` → `403 EMAIL_NOT_CONFIRMED` ; le client redirige vers `/confirmer-email`.

## Structure
Deux cartes (520 px) sur fond `#EEF1F6`, bandeau public de la fiche 00 (la page n'a pas de session ; le rappel « Connecté·e en tant que … · Se déconnecter » ne s'affiche que si une session existe) :
1. **En attente** : icône enveloppe (`#E3EAF8`), titre « Un dernier clic : confirmez votre e-mail », validité 24 h, 3 étapes (boîte mail + indésirables, cliquer, arrivée sur la liste vide), puis **un seul geste** :
   - adresse **connue** (juste après l'inscription, ou après un login refusé `EMAIL_NOT_CONFIRMED` qui vient de la donner) : adresse en monospace dans le texte, bouton « Renvoyer le lien » + compte à rebours « Nouveau renvoi possible dans 0:42 », puis « Mauvaise adresse ? Corriger l'e-mail — le lien précédent sera invalidé » ;
   - adresse **inconnue** (page ouverte directement) : champ « Adresse du compte » avec l'aide « Pas reçu ? Saisissez l'adresse du compte et renvoyez le lien », puis le même bouton « Renvoyer le lien » ; pas de « Corriger l'e-mail » (le champ suffit) ;
   - rappel que la vérification des diplômes est indépendante.
2. **Lien expiré** : icône horloge (`#FBF1DC`), titre « Ce lien de confirmation a expiré », « valable 24 h, à usage unique : il a expiré, a déjà servi ou a été remplacé par un envoi plus récent » (sans date d'émission), champ « Adresse du compte » + « Recevoir un nouveau lien », note « nous ne confirmons pas l'existence d'un compte », lien « Se connecter ».

## Interactions
- Compte à rebours de 60 s après chaque renvoi ; bouton désactivé pendant ce temps (`aria-disabled` + texte).
- Lien ouvert dans un autre navigateur que celui de l'inscription : la confirmation fonctionne et **ouvre une session** dans ce navigateur (pas de redemande de mot de passe) ; l'événement est journalisé.
- Token expiré, déjà utilisé, inconnu ou remplacé → `410 TOKEN_INVALID_OR_EXPIRED`, seul code du contrat → même page « expiré », même texte.
- « Corriger l'e-mail » → formulaire en place (mot de passe + nouvelle adresse) ; `202` dans tous les cas → « Si le mot de passe est correct, un nouveau lien est parti à … ».
- Si une session existe : polling discret (`GET /api/auth/session` toutes les 10 s, max 30 min) ; si l'e-mail est confirmé dans un autre onglet, cette page redirige d'elle-même.

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
- [ ] Deux renvois en moins d'une minute → le second répond `202` sans envoyer d'e-mail (contrat : rien ne distingue un refus silencieux), bouton désactivé côté client.
- [ ] `resend` sur un e-mail inconnu → `202`, aucun e-mail envoyé, même temps de réponse ± 50 ms (anti-énumération).
- [ ] Changement d'e-mail → ancien token refusé, nouveau token accepté.

## Écarts avec la maquette
Maquette HTML corrigée le 11 novembre 2026 (UX-F1) ; le `.png` montre encore l'ancienne version.
- **Pas de session** : `register` n'ouvre aucune session et le login d'un compte `email_pending` répond `403 EMAIL_NOT_CONFIRMED` (contrat) → bandeau public sans « Connecté·e en tant que … » (maquette corrigée). Le rappel et le polling de `GET /api/auth/session` ne valent que si une session existe.
- **Un seul geste « Renvoyer le lien »** : libellé unique, champ « Adresse du compte » au-dessus quand la page ne connaît pas l'adresse ; « Corriger l'e-mail » seulement quand l'adresse est connue, via la variante publique de `POST /api/auth/email/change` (contrat ; maquette corrigée pour le libellé, l'état « adresse inconnue » n'est décrit que dans la fiche).
- **Lien expiré sans date d'émission** : aucune route ne lit le jeton, et `410` ne distingue pas « expiré » de « déjà utilisé » → un seul texte, sans « Ce lien a déjà servi » (contrat ; maquette corrigée).
- **Nom** PsyMate dans le bandeau et le pied (maquette corrigée).

## Hors périmètre V1
Connexion par lien magique, vérification par SMS.
