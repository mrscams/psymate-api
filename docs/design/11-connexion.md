# Écran 11 · Connexion praticien

> Spécification pour agents. Maquette : `11-connexion.html` / `.png`. Palette : Nordique. Document A, module `identity` (M0-04 puis M1-11) ; hors Document B (écran transverse).
>
> Mis à jour le 29 octobre 2026 (G) : durée de session **confirmée** 12 h / 30 jours avec « rester connecté » (Q6) ; nom d'application PsyMate ; codes d'erreur alignés sur F (`EMAIL_NOT_CONFIRMED`, `ACCOUNT_SUSPENDED`, `PASSWORD_CHANGE_REQUIRED` pour le premier admin créé par `make admin`, Q5).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : les deux panneaux sont remplacés par le **layout public à une carte** de la fiche 00 §4 (bandeau, accroche d'une ligne au-dessus de la carte, carte 480 px, mention d'hébergement en dessous, pied public) ; libellés au-dessus des champs, « Mot de passe oublié ? » à droite du libellé, case « Rester connecté·e » ; le bloc code de vérification est un état séparé. Maquette HTML corrigée.

## Objectif
Entrer dans l'espace praticien en moins de 10 secondes, avec double authentification optionnelle, sans jamais exposer d'information sur l'existence d'un compte.

## Route & module
- Route publique : `/connexion` (layout `PublicLayout`, redirection vers `/patients` si déjà authentifié ; paramètre `?next=` conservé).
- API : `POST /api/auth/login {email, password, otp?, rememberMe}` → `200 {session}` ou `401` (message unique « identifiants incorrects », même si l'e-mail n'existe pas) ou `423` (verrouillé, avec `Retry-After`). Session par cookie `HttpOnly; Secure; SameSite=Lax`, durée 12 h, ou 30 j avec « rester connecté » (G Q6). `POST /api/auth/logout`, `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`.
- 2FA : TOTP (RFC 6238, issuer « PsyMate ») activable dans Préférences › Compte et sécurité ; 10 codes de secours à usage unique. Le champ code n'est affiché qu'après un premier `POST` répondant `202 {otpRequired: true}` (la maquette montre l'état « code demandé »).
- Verrouillage : 5 échecs → 15 min (clé e-mail + IP, Bucket4j) ; journalisé dans `security_event`.

## Structure
Layout public de la fiche 00 §4 (bandeau, conteneur centré, pied public), sur fond `#EEF1F6` :
1. **Accroche** d'une ligne au-dessus de la carte : « Le fil de vos patients, séance après séance. »
2. **Carte formulaire** 480 px : titre « Connexion », « Espace réservé aux praticiens. » + lien *Créer un compte*, champ e-mail (`autocomplete="username"`), mot de passe avec bouton afficher (`aria-label`, `aria-pressed`) ; **libellés au-dessus des champs** (style de l'application, pas de libellé flottant) ; lien *Mot de passe oublié ?* **à droite du libellé** du mot de passe ; case « Rester connecté·e sur cet appareil pendant 30 jours » (G Q6) ; bouton **Se connecter** 46 px ; mention verrouillage + journal.
3. **Mention d'hébergement** sous la carte : « Données hébergées en France · chiffrées au repos et en transit · vous restez seul·e propriétaire de vos dossiers. »
4. À 390 px : même ordre, carte pleine largeur.

## États
- **Code demandé** (état B de la maquette, séparé) : après un `POST` répondant `202 {otpRequired: true}`, la carte garde e-mail et mot de passe et ajoute le bloc **code de vérification** (6 chiffres, `inputmode="numeric"`, `autocomplete="one-time-code"`, lien *Utiliser un code de secours*), focus sur le code. Ce bloc n'est jamais affiché avant.
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

## Écarts avec la maquette
Maquette HTML corrigée le 11 novembre 2026 (UX-F1) ; le `.png` montre encore les deux panneaux.
- **Layout public à une carte** : la fiche 00 §4 (plus récente) impose aux formulaires publics le même conteneur centré 480 px et le bandeau partagé → panneau gauche (aperçu de timeline, pied propre) retiré ; l'accroche devient une ligne au-dessus de la carte et la mention d'hébergement passe sous la carte (fiche 00 ; maquette corrigée).
- **Bloc code de vérification** : visible seulement après `202 otpRequired` (contrat) → montré comme état B séparé au lieu d'être dans la carte par défaut (maquette corrigée).
- **Nom** PsyMate au lieu de « Suivi » (G Q1 ; maquette corrigée).

## Hors périmètre V1
SSO (Pro Santé Connect, Google), passkeys, connexion par lien magique.
