# Écran 11 · Connexion praticien

> Spécification pour agents. Maquette : `11-connexion.html` / `.png`. Palette : Nordique. Document A, module `identity` (M0-04 puis M1-11) ; hors Document B (écran transverse).
>
> Mis à jour le 29 octobre 2026 (G) : durée de session **confirmée** 12 h / 30 jours avec « rester connecté » (Q6) ; nom d'application PsyMate ; codes d'erreur alignés sur F (`EMAIL_NOT_CONFIRMED`, `ACCOUNT_SUSPENDED`, `PASSWORD_CHANGE_REQUIRED` pour le premier admin créé par `make admin`, Q5).

## Objectif
Entrer dans l'espace praticien en moins de 10 secondes, avec double authentification optionnelle, sans jamais exposer d'information sur l'existence d'un compte.

## Route & module
- Route publique : `/connexion` (layout `PublicLayout`, redirection vers `/patients` si déjà authentifié ; paramètre `?next=` conservé).
- API : `POST /api/auth/login {email, password, otp?, rememberMe}` → `200 {session}` ou `401` (message unique « identifiants incorrects », même si l'e-mail n'existe pas) ou `423` (verrouillé, avec `Retry-After`). Session par cookie `HttpOnly; Secure; SameSite=Lax`, durée 12 h, ou 30 j avec « rester connecté » (G Q6). `POST /api/auth/logout`, `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`.
- 2FA : TOTP (RFC 6238, issuer « PsyMate ») activable dans Préférences › Compte et sécurité ; 10 codes de secours à usage unique. Le champ code n'est affiché qu'après un premier `POST` répondant `202 {otpRequired: true}` (la maquette montre l'état « code demandé »).
- Verrouillage : 5 échecs → 15 min (clé e-mail + IP, Bucket4j) ; journalisé dans `security_event`.

## Structure
1. **Panneau gauche** (blanc, bordure droite) : logo PsyMate, accroche (« Le fil de vos patients, séance après séance. »), sous-titre, aperçu décoratif d'une timeline (formes par type), pied : hébergement FR / chiffrement / liens Confidentialité, Mentions légales, Annuaire public.
2. **Panneau droit** (fond `#EEF1F6`) : carte formulaire 420 px : titre « Connexion », lien *Créer un compte*, champ e-mail (`autocomplete="username"`), mot de passe avec bouton afficher (`aria-label`), lien *Mot de passe oublié ?*, bloc **code de vérification** (6 chiffres, `inputmode="numeric"`, `autocomplete="one-time-code"`, lien *Utiliser un code de secours*) visible uniquement si `otpRequired`, case « Rester connecté 30 jours », bouton **Se connecter** 46 px, mention verrouillage + journal.
3. À 390 px : les deux panneaux s'empilent, le panneau gauche se réduit au logo et à l'accroche.

## États
- Erreur identifiants : bandeau `--danger` sur `#FBE9E7` au-dessus du bouton, champs conservés, focus sur mot de passe.
- Verrouillé : bandeau `--warning` avec compte à rebours ; bouton désactivé.
- E-mail non confirmé : `403 {code: "EMAIL_NOT_CONFIRMED"}` → message + bouton « Renvoyer l'e-mail de confirmation ».
- Compte suspendu ou refusé : `403 {code: "ACCOUNT_SUSPENDED"}` → redirection vers l'écran 27 ; pas de détail du motif ici (il est dans l'e-mail et sur l'écran 27).
- Changement de mot de passe exigé (premier administrateur, M7-08) : `403 {code: "PASSWORD_CHANGE_REQUIRED"}` → formulaire de nouveau mot de passe, puis activation obligatoire de la 2FA.

## Tokens
- Carte `#FFFFFF`, bordure `#D6DCE6`, rayon 14 px ; champs 44 px bordure `#B9C2D1` rayon 10 px ; bouton primaire `#3A63C2`.
- Code OTP et mot de passe masqué : JetBrains Mono, `letter-spacing` 0,1–0,25 em.

## Accessibilité
- `<form aria-labelledby>`, `<label for>` sur chaque champ, erreurs via `aria-describedby` + `role="alert"`.
- Bouton afficher le mot de passe : `aria-pressed`.
- Contraste texte gris `#5A6679` sur blanc ≥ 4,5:1 ; focus visible 2 px `#3A63C2`.

## Critères d'acceptation
- [ ] E-mail inconnu et mot de passe faux donnent exactement le même message et le même temps de réponse (± 50 ms, test de timing).
- [ ] 5 échecs → `423` + `Retry-After` ; 6e tentative correcte refusée pendant le délai.
- [ ] Compte avec TOTP : `202 otpRequired` puis `200` avec code valide ; code réutilisé → `401`.
- [ ] Cookie `HttpOnly; Secure; SameSite=Lax` ; « rester connecté » → 30 j, sinon 12 h.
- [ ] axe-core : 0 violation ; parcours clavier complet.

## Décision du 11 novembre 2026
- La connexion garde ses **deux panneaux** (structure ci-dessus et maquette `11-connexion.html`). C’est une exception au conteneur centré de 480 px que la fiche 00 §4 prévoit pour les formulaires publics : la fiche 11 l’emporte pour cet écran (décision du propriétaire).
- La version à une carte introduite par UX-F1 (PR 84) est retirée : la maquette et cette fiche sont revenues à leur état d’origine.

## Hors périmètre V1
SSO (Pro Santé Connect, Google), passkeys, connexion par lien magique.
