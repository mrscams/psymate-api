# Écran 26 · Mot de passe oublié (demande + nouveau mot de passe)

> Spécification pour agents. Maquette : `26-mot-de-passe-oublie.html` / `.png` (deux étapes côte à côte). Palette : Nordique, layout public. Module `identity` (`password_reset_token`, `security_event`).

## Objectif
Réinitialiser un mot de passe sans jamais révéler si un compte existe, avec un lien court (30 min), une politique de robustesse vérifiée et la possibilité de déconnecter les autres appareils.

## Route & module
- Routes : `/mot-de-passe-oublie` (étape 1), `/reinitialiser/{token}` (étape 2).
- API :
  - `POST /api/auth/password/forgot` `{ email }` → toujours `202` ; e-mail avec lien si le compte existe (y compris suspendu ; **pas** si `email_pending` : dans ce cas l'e-mail invite à confirmer d'abord). Limite 3 / heure / e-mail et 10 / heure / IP. `security_event` `password_reset_requested` sur le compte concerné.
  - `GET /api/auth/password/reset/{token}` → `{ email (masqué), expiresAt }` ou `410` (expiré / consommé).
  - `POST /api/auth/password/reset` `{ token, password, revokeOtherSessions }` → `204`, session ouverte (puis 2FA si activée), e-mail « votre mot de passe a été modifié », `security_event` `password_changed`. Token 30 min, usage unique, invalidé par toute nouvelle demande.
- Politique de mot de passe : ≥ 12 caractères, majuscules + minuscules, absent des listes de mots de passe compromis (vérification k-anonymity HIBP côté serveur, hors ligne tolérée), chiffre/symbole **conseillé** non obligatoire ; comparaison avec l'e-mail et le nom (refusés). Hachage Argon2id.

## Structure
Bandeau public avec « ← Retour à la connexion » ; deux cartes-formulaires (520 px) :
1. **Étape 1 sur 2 · Mot de passe oublié** : explication (lien 30 min), champ e-mail, « Envoyer le lien », bloc de statut vert « Si cette adresse correspond à un compte, un e-mail vient de partir… redemander dans 2 minutes », note anti-énumération + journal de sécurité.
2. **Étape 2 sur 2 · Choisissez un nouveau mot de passe** : compte concerné (monospace) + compte à rebours d'expiration « 27:40 », champ nouveau mot de passe avec bouton afficher, **jauge de robustesse** 4 segments, liste des critères avec coche (4 critères, le dernier « conseillé »), confirmation, case « Déconnecter tous les autres appareils (recommandé…) » cochée par défaut, bouton « Enregistrer et me connecter », note 2FA + e-mail de confirmation.

## Interactions
- Étape 1 : le message de statut est identique quel que soit le résultat ; le bouton se désactive 2 min.
- Étape 2 : critères et jauge évaluent en direct côté client (longueur, casse, similarité e-mail/nom) ; la vérification « compromis » est faite au `POST` (message dédié en cas de refus, sans renvoyer le mot de passe).
- Confirmation ≠ mot de passe → erreur sous le champ au blur, pas pendant la frappe.
- Token expiré pendant la saisie (compte à rebours à 0) → le bouton redirige vers l'étape 1 avec message.
- Après succès : session ouverte ; si 2FA → écran 11 à l'étape code ; sinon → `/patients`.

## Tokens
- Statut étape 1 : fond `#E4F2EA`, bordure `#BFE0CD`, texte `#1E6B45`.
- Jauge : segments atteints `#1E6B45`, restants `#D6DCE6` ; critères validés `#1E6B45`, en attente `#5A6679` avec cercle vide.
- Champs de mot de passe en `JetBrains Mono`, `letter-spacing: .1em`.

## Accessibilité
- Les deux formulaires ont `aria-labelledby` ; les critères sont une `<ul>` dont chaque item annonce son état (« validé » / « à faire » en `visually-hidden`).
- Le compte à rebours est `aria-live="off"` (pas d'annonce chaque seconde) avec une annonce unique à 5 min et à 0.
- Bouton « Afficher le mot de passe » avec `aria-pressed`.

## Critères d'acceptation
- [ ] `forgot` sur un e-mail inconnu et sur un e-mail connu → même code `202`, même corps, temps de réponse comparable.
- [ ] 4ᵉ demande en une heure pour le même e-mail → `429`, message générique côté client.
- [ ] Token > 30 min ou déjà utilisé → `410` ; page « lien expiré » avec retour à l'étape 1.
- [ ] Mot de passe « Password2026! » (présent dans les listes compromises) → `422 PASSWORD_COMPROMISED` ; « 12 caractères » sans majuscule → `422 PASSWORD_POLICY`.
- [ ] `revokeOtherSessions = true` → toutes les autres sessions invalides ; `false` → conservées.
- [ ] Succès → e-mail « mot de passe modifié » envoyé, `security_event` visible dans l'écran 14 (Compte et sécurité) du praticien.

## Hors périmètre V1
Clés de sécurité / passkeys, questions secrètes (jamais), récupération via l'administrateur.
