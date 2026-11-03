# Écran 18 · Compte et sécurité

> Spécification pour agents. Maquette : `18-compte-securite.html` / `.png`. Palette : Nordique. Document A, module `identity` (M1-11, M7-08), A10.4 ; complète la section 5 de `14-preferences-praticien.md`. Tâche web : **M1-17** (nouvelle, à ajouter au plan C après M1-15).

## Objectif
Donner au praticien la maîtrise de ses identifiants et de sa double authentification depuis un seul écran : changer son mot de passe, activer la 2FA en trois étapes avec ses codes de secours, déconnecter ses autres appareils. L'écran porte aussi le parcours bloquant du premier administrateur (G Q5) : mot de passe à usage unique à remplacer, puis 2FA obligatoire, avant tout accès à `/admin`.

## Route & module
- Routes : `/mon-compte/securite` (navigation « Mon compte », état A ; le panneau d'activation est `/mon-compte/securite/2fa`, état B) ; `/premiere-connexion` (état C, layout public avec barre sombre).
- Déclenchement de `/premiere-connexion` : ni `GET /api/auth/me` (`MeResponse`) ni `GET /api/auth/session` (`SessionResponse`) n'exposent de champ `mustChangePassword`, `role` ou `twoFactorEnabled`. L'état C s'ouvre donc sur deux codes d'erreur : `403 PASSWORD_CHANGE_REQUIRED` renvoyé par `POST /api/auth/login` (étape 1), puis `403 TWO_FACTOR_REQUIRED` renvoyé par toute route `/api/admin/**` (étape 2). `403 ADMIN_ROLE_REQUIRED` renvoie vers `/patients`, pas vers l'état C.
- Mot de passe : `POST /api/auth/password/change` `{email, currentPassword, newPassword, otp?}` (public, sans session) → `204` (toutes les sessions révoquées, aucune ouverte : se reconnecter via `POST /api/auth/login`) ; `202 {otpRequired: true}` si la 2FA est active et `otp` absent ; `401 INVALID_CREDENTIALS` ; `422 PASSWORD_POLICY` (`errors[]` sur `password` : `PASSWORD_TOO_SHORT`, `PASSWORD_CASE`, `PASSWORD_COMPROMISED`, `PASSWORD_CONTAINS_PERSONAL_DATA`) ou `VALIDATION_FAILED` (`PASSWORD_UNCHANGED`, `INVALID_FIELD`) ; `423 ACCOUNT_LOCKED` (`retryAfterSeconds`).
- E-mail : `POST /api/auth/email/change` `{email, newEmail, password}` → `202` dans tous les cas, puis `POST /api/auth/email/confirm` `{token}` → `204` (`410 TOKEN_INVALID_OR_EXPIRED`). Le contrat limite ce changement à un compte `email_pending` (voir Écarts).
- 2FA : `POST /api/me/2fa/enable` → `200 {secret, otpauthUri}` (montrés une fois ; `409 TWO_FACTOR_ALREADY_ENABLED`) ; `POST /api/me/2fa/verify` `{code}` → `200 {recoveryCodes[10]}` (`422 INVALID_CODE`, `409 TWO_FACTOR_NOT_STARTED`, `423 ACCOUNT_LOCKED`) ; `POST /api/me/2fa/recovery-codes/regenerate` → `200 {recoveryCodes[10]}` (`409 TWO_FACTOR_NOT_ENABLED`) ; `POST /api/me/2fa/disable` `{password, code}` → `204` (`422 INVALID_PASSWORD` / `INVALID_CODE`, `409 TWO_FACTOR_REQUIRED_FOR_ADMIN`, `423`).
- Sessions : `POST /api/auth/sessions/revoke-others` → `200 {revoked}` ; durées G Q6 : 12 h d'inactivité, 30 jours avec « rester connecté ».
- Store : `AccountSecurityStore` : `email`, `emailConfirmed` (de `GET /api/auth/session`), `twoFactorEnabled` (déduit, voir Écarts), `pendingSecret`, `recoveryCodes`, `revokedCount`, `firstLoginStep` (`password` | `twoFactor` | `done`).

## Structure
1. **État A** (`/mon-compte/securite`) : barre d'application, navigation « Mon compte » (220 px, « Compte et sécurité » actif), colonne de contenu ≤ 960 px. Cartes dans l'ordre : **E-mail de connexion** (adresse, statut confirmé en `--success`) ; **Mot de passe** (masqué, bouton *Modifier*, rappel des règles) ; **Double authentification** (pastille *Activée* `--success` ou *Non activée* `--warning`, « N codes de secours restants sur 10 », *Régénérer les codes*, *Désactiver* en contour `--danger` ; si non activée : bouton primaire *Activer*) ; **Sessions** (bouton *Déconnecter les autres appareils*, puis « N appareils déconnectés » après l'appel).
2. **État B** : panneau latéral `role="dialog"` 560 px, stepper 3 étapes : 1 *Scanner le QR* (QR SVG rendu depuis `otpauthUri`, `secret` en JetBrains Mono groupé par 4 + *Copier*) ; 2 *Saisir le code* (bloc OTP de la fiche 11, bouton *Vérifier*) ; 3 *Codes de secours* (bandeau succès, grille 2 × 5, *Télécharger (.txt)*, *Copier*, case « Je les ai enregistrés » qui active *Terminer*).
3. **État C** (`/premiere-connexion`) : barre sombre `#1E2633` sans navigation, panneau gauche (explication, progression 1/2 → 2/2, encart `--warning` « aucun accès à l'administration »), panneau droit : formulaire *Étape 1 / 2 · Choisissez votre mot de passe* (e-mail en lecture seule, mot de passe temporaire, nouveau + confirmation, règles cochées au fil de la saisie), puis *Étape 2 / 2 · Activez la double authentification* (QR + secret + bloc OTP, puis codes de secours à confirmer).

## Interactions
- *Modifier* le mot de passe ouvre un panneau : mot de passe actuel, nouveau, confirmation, champ OTP affiché dès que l'API répond `202 otpRequired`. Succès `204` → message « Mot de passe changé, reconnectez-vous » et redirection vers `/connexion` (sessions révoquées par le serveur).
- Erreurs `422 PASSWORD_POLICY` : chaque code de `errors[]` coche en `--danger` la règle correspondante sous le champ ; `PASSWORD_UNCHANGED` et `INVALID_CREDENTIALS` s'affichent sous le formulaire ; `423` affiche le délai (`retryAfterSeconds`) et désactive le bouton.
- Activation 2FA : `enable` à l'ouverture du panneau ; fermer avant `verify` abandonne le secret en attente (le prochain `enable` le remplace). `verify` en succès passe à l'étape 3 ; *Terminer* n'est actif qu'avec la case cochée. Les codes ne sont jamais réaffichés.
- *Régénérer les codes* ouvre une confirmation (« les 10 anciens codes cesseront de fonctionner »), puis réutilise l'étape 3 du panneau. *Désactiver* demande mot de passe + code dans une boîte de dialogue ; le bouton est absent pour un administrateur (`409 TWO_FACTOR_REQUIRED_FOR_ADMIN`).
- *Déconnecter les autres appareils* : confirmation, puis « N appareils déconnectés » (`revoked`, 0 compris) en `aria-live="polite"`.
- État C : après `204` de `password/change`, le front rejoue `POST /api/auth/login` avec le nouveau mot de passe, puis enchaîne l'étape 2 sans quitter la page ; `/admin/**` reste inaccessible (garde de route sur `403 TWO_FACTOR_REQUIRED`) tant que `verify` n'a pas réussi et que les codes de secours n'ont pas été confirmés.

## Tokens
- Cartes blanches bordure `#D6DCE6` rayon 12 px ; titres de carte 12 px majuscules `#5A6679` ; grille libellé 260 px / valeur / action.
- Pastille 2FA activée `#E4F2EA` / `#1E6B45` ; non activée `#FBF1DC` / `#7A5A12` ; *Désactiver* contour et texte `#9A2F22` (seule action destructive de l'écran) ; stepper actif `#3A63C2`, inactif `#D6DCE6`.
- Secret, codes OTP et codes de secours : JetBrains Mono ; champ OTP 150 × 44 px, `letter-spacing: 0.25em` (fiche 11).
- État C : barre `#1E2633`, badge *Admin* `#FBF1DC` / `#1E2633` (fiche 20) ; encart d'avertissement `#FBF1DC` / `#7A5A12`.

## Accessibilité
- Panneau 2FA : `role="dialog" aria-modal="true" aria-labelledby`, focus piégé, `Échap` ferme (avec confirmation si un secret est en attente) ; stepper `<ol>` avec `aria-current="step"`.
- Chaque champ a un `<label>` et une aide `aria-describedby` ; champ OTP `inputmode="numeric" autocomplete="one-time-code"` ; erreurs `errors[]` reliées au champ par `aria-describedby` et annoncées.
- Codes de secours dans `<ul aria-label>` ; QR `role="img"` avec `aria-label`, le secret texte en est l'alternative.
- Aucune information portée par la couleur seule : pastilles et règles ont un texte et une icône.

## Critères d'acceptation
- [ ] Panneau d'activation : `enable` → QR + secret, code valide → `verify` renvoie 10 codes affichés une seule fois ; à la connexion suivante `POST /api/auth/login` répond `202 otpRequired` et la fiche 11 affiche le bloc code (test Playwright de bout en bout).
- [ ] Code faux à `verify`, au `disable` ou au login → message unique « Ce code n'est pas valide ou a déjà été utilisé », sans distinguer la cause ; 5 codes faux → `423` affiché avec le délai.
- [ ] *Désactiver* exige mot de passe + code ; `422 INVALID_PASSWORD` et `422 INVALID_CODE` sont rendus sur le bon champ ; un compte admin ne voit pas le bouton.
- [ ] *Régénérer* : les anciens codes sont refusés au login, l'un des 10 nouveaux est accepté (test d'intégration API + scénario Playwright).
- [ ] Changement de mot de passe : `204` révoque toutes les sessions, le front redirige vers `/connexion` ; `422 PASSWORD_POLICY` coche les règles en erreur.
- [ ] *Déconnecter les autres appareils* affiche le nombre renvoyé par `revoke-others` (0 inclus) ; la session courante reste ouverte.
- [ ] Administrateur fraîchement créé (`make admin`) : login → `403 PASSWORD_CHANGE_REQUIRED` → `/premiere-connexion` ; après le mot de passe, `/admin` répond `403 TWO_FACTOR_REQUIRED` et renvoie sur l'étape 2 ; après `verify` et confirmation des codes, `/admin` s'ouvre.
- [ ] Parcours clavier complet sur les trois états (Tab, Entrée, Échap, cases à cocher) sans souris ; axe-core : 0 violation.

## Écarts avec la maquette
- **Sessions actives et Dernière connexion (M10-06, contrat `v0.11.0`)** : l'API est disponible. `GET /api/me/security` renvoie `sessions[]` (`id` opaque, `createdAt`, `lastAccessAt`, `expiresAt`, `current`, `device`, `city`) et `lastLogin` (`at`, `device`, `city`) ; `POST /api/me/sessions/{id}/revoke` ferme une autre session (`204`, `404` si elle n'est pas au praticien, `409 CURRENT_SESSION` si c'est la courante). `device` est un libellé « navigateur · système » (ex. « Chrome · Windows »), `null` pour une session ou une connexion antérieure à la fonction. **La ville est absente : `city` vaut toujours `null` en V1** (aucune géolocalisation, aucune IP exposée) ; le front n'affiche pas de ville et s'adapte si `city` devient renseignée plus tard. `lastLogin` est la connexion la plus récente, y compris celle qui a ouvert la session courante. Le lien « Voir le journal de sécurité » reste hors contrat. Les codes de secours restants et `twoFactorEnabled` ne sont pas dans cette réponse (voir plus bas).
- Le changement d'e-mail d'un compte confirmé n'est pas couvert par `POST /api/auth/email/change` tel que documenté (seul un compte `email_pending` change d'adresse). **Bouton *Modifier* masqué tant que l'API ne le permet pas** ; écart à remonter côté API.
- Les codes de secours sont **10** (contrat `RecoveryCodesResponse`), pas 8 ; la maquette montre « 8 restants sur 10 », compteur qui dépend lui aussi de `GET /api/me/security`.
- `twoFactorEnabled` n'est exposé qu'au module admin (`AccountDetail`) : en V1 le front le déduit du `202 otpRequired` au login ou du `409` de `enable` / `regenerate`, et le mémorise dans le store de session.
- État C : la reconnexion automatique après `password/change` est une décision de cette fiche (le contrat n'ouvre aucune session) ; le mot de passe temporaire n'est jamais affiché ni rappelé par l'application.

## Hors périmètre V1
Liste et révocation session par session, journal de sécurité côté praticien, passkeys / WebAuthn, 2FA par SMS ou e-mail, changement d'e-mail d'un compte confirmé, délégation de compte (secrétariat).
