# Écran 26 · Mot de passe oublié (demande + nouveau mot de passe)

> Spécification pour agents. Maquette : `26-mot-de-passe-oublie.html` / `.png` (deux étapes côte à côte). Palette : Nordique, layout public. Module `identity` (`password_reset_token`, `security_event`).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : routes et corps alignés sur le contrat (`password-reset/request`, `password-reset/confirm`) ; étape 2 aussi sur `/reset-password?token=` ; pas d'adresse masquée ni de compte à rebours du lien (aucune lecture du jeton) ; toutes les sessions sont révoquées, sans case « Déconnecter les autres appareils », et aucune session n'est ouverte ; pas de mention « demandes consignées dans le journal ». Maquette HTML corrigée.

## Objectif
Réinitialiser un mot de passe sans jamais révéler si un compte existe, avec un lien court (30 min) et une politique de robustesse vérifiée ; la réinitialisation ferme toutes les sessions du compte.

## Route & module
- Routes : `/mot-de-passe-oublie` (étape 1) ; étape 2 sur `/reinitialiser/{token}` et sur `/reset-password?token=…` (même page, les deux formes de lien sont acceptées).
- API :
  - `POST /api/auth/password-reset/request` `{ email }` → toujours `202`, réponse identique que le compte existe ou non ; e-mail avec lien à usage unique valable 30 min si le compte existe ; une nouvelle demande invalide le lien précédent. Limites G Q24 (3 / heure / e-mail, 10 / heure / IP).
  - Aucune route ne lit le jeton : l'étape 2 ne connaît ni l'adresse du compte ni l'heure d'expiration ; un jeton expiré, consommé ou remplacé n'est découvert qu'au `POST` (`410`).
  - `POST /api/auth/password-reset/confirm` `{ token, password }` → `204` : mot de passe remplacé, **toutes les sessions du compte révoquées**, **aucune session ouverte** (se connecter ensuite par l'écran 11, code 2FA compris) ; `410 TOKEN_INVALID_OR_EXPIRED` ; `422 PASSWORD_POLICY` (le jeton n'est alors pas consommé).
- Politique de mot de passe : ≥ 12 caractères, majuscules + minuscules, absent des listes de mots de passe compromis (vérification k-anonymity HIBP côté serveur, hors ligne tolérée), chiffre/symbole **conseillé** non obligatoire ; comparaison avec l'e-mail et le nom (refusés). Hachage Argon2id.

## Structure
Bandeau public ; « ← Retour à la connexion » en haut de chaque carte ; deux cartes-formulaires (520 px), une seule affichée par la page réelle :
1. **Étape 1 sur 2 · Mot de passe oublié** : explication (lien 30 min), champ e-mail, « Envoyer le lien », bloc de statut vert « Si cette adresse correspond à un compte, un e-mail vient de partir… une nouvelle demande remplace le lien précédent », note anti-énumération (« le message est le même qu'un compte existe ou non »).
2. **Étape 2 sur 2 · Choisissez un nouveau mot de passe** : rappel « lien valable 30 minutes après votre demande, à usage unique » (ni adresse du compte, ni compte à rebours), champ nouveau mot de passe avec bouton afficher, **jauge de robustesse** 4 segments avec texte, liste des critères avec coche (4 critères, le dernier « conseillé »), confirmation, phrase « toutes les sessions ouvertes avec l'ancien mot de passe seront fermées, sur tous vos appareils », bouton « Enregistrer le mot de passe », note « le code 2FA sera demandé à la connexion ».
3. **Lien expiré** (`410`) : « Ce lien de réinitialisation n'est plus valable », rappel des 30 minutes et de l'usage unique, « Votre mot de passe n'a pas changé », bouton vers l'étape 1.

## Interactions
- Étape 1 : le message de statut est identique quel que soit le résultat.
- Étape 2 : critères et jauge évaluent en direct côté client (longueur, casse) ; la vérification « compromis » et « données personnelles » est faite au `POST` (message dédié en cas de refus, sans renvoyer le mot de passe).
- Confirmation ≠ mot de passe → erreur sous le champ au blur, pas pendant la frappe.
- `410` au `POST` → état « lien expiré ».
- Après succès (`204`) : aucune session n'est ouverte → écran 11 avec le bandeau « Mot de passe modifié. Toutes vos sessions ont été fermées : connectez-vous avec le nouveau. »

## Tokens
- Statut étape 1 : fond `#E4F2EA`, bordure `#BFE0CD`, texte `#1E6B45`.
- Jauge : segments atteints `#1E6B45`, restants `#D6DCE6` ; critères validés `#1E6B45`, en attente `#5A6679` avec cercle vide.
- Champs de mot de passe en `JetBrains Mono`, `letter-spacing: .1em`.

## Accessibilité
- Les deux formulaires ont `aria-labelledby` ; les critères sont une `<ul>` dont chaque item annonce son état (« validé » / « à faire » en `visually-hidden`).
- Bouton « Afficher le mot de passe » avec `aria-pressed`.

## Critères d'acceptation
- [ ] `forgot` sur un e-mail inconnu et sur un e-mail connu → même code `202`, même corps, temps de réponse comparable.
- [ ] 4ᵉ demande en une heure pour le même e-mail → `429`, message générique côté client.
- [ ] Token > 30 min ou déjà utilisé → `410` ; page « lien expiré » avec retour à l'étape 1.
- [ ] Mot de passe « Password2026! » (présent dans les listes compromises) → `422 PASSWORD_COMPROMISED` ; « 12 caractères » sans majuscule → `422 PASSWORD_POLICY`.
- [ ] Succès → toutes les sessions du compte invalides, aucune session ouverte ; le client arrive sur l'écran 11 avec le bandeau « Mot de passe modifié ».
- [ ] Les liens `/reinitialiser/{token}` et `/reset-password?token={token}` ouvrent la même étape 2.

## Écarts avec la maquette
Maquette HTML corrigée le 11 novembre 2026 (UX-F1) ; le `.png` montre encore l'ancienne version.
- **Pas d'adresse masquée ni de compte à rebours** à l'étape 2 : le contrat n'a aucune route qui lit le jeton (`GET …/reset/{token}` n'existe pas) → « Pour le compte … · expire dans 27:40 » remplacé par le rappel « valable 30 minutes, usage unique » (contrat ; maquette corrigée).
- **Pas de case « Déconnecter tous les autres appareils »** : `POST /api/auth/password-reset/confirm` révoque toujours toutes les sessions (`PasswordResetConfirmRequest` n'a que `token` et `password`) → phrase d'information à la place (contrat ; maquette corrigée).
- **Pas de session ouverte après succès** : bouton « Enregistrer le mot de passe » au lieu de « Enregistrer et me connecter », retour à l'écran 11 (contrat ; maquette corrigée).
- **Pas de mention « demandes consignées dans le journal de sécurité »** : aucun journal de sécurité côté praticien en V1 (fiche 18, hors périmètre) (maquette corrigée).
- **« Redemander dans 2 minutes »** remplacé par « une nouvelle demande remplace le lien précédent », seule règle du contrat (maquette corrigée).
- **Étape 2 sur deux routes** : `/reinitialiser/{token}` et `/reset-password?token=` (lien de l'e-mail) mènent à la même page ; la maquette n'en montre qu'une.
- **Nom** PsyMate dans le bandeau et le pied (maquette corrigée).

## Hors périmètre V1
Clés de sécurité / passkeys, questions secrètes (jamais), récupération via l'administrateur.
