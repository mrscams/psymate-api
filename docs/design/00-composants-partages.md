# Fiche 00 · Composants partagés (shell, en-têtes, layouts, panneaux, états)

> Spécification pour agents. Pas de maquette propre : chaque composant est visible dans les maquettes citées. Palette : Nordique. Document B, B2 (navigation, en-tête patient permanent), B7 ; `design/README.md` « Conventions communes ». Posés une fois par **M1-07** (shell praticien) et **M0-07** (layout public) ; la barre admin par **M7-07**. Toute fiche d'écran suppose ces composants et ne les redéfinit pas.
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre ; livré par les tâches web UX-A1 à UX-A7) : repliements à 375 px de la barre, de l'en-tête patient et de la navigation Mon compte ; anneau de focus des contrôles natifs et token `--admin-focus` de la barre admin ; classe `.pm-link-target` (cibles de 24 px) ; champs `pm-date-field` et `pm-month-field` ; résumé d'erreurs `pm-form-errors` ; titre de repli `entryTitle()`.

## Objectif
Qu'un seul agent construise la barre d'application, l'en-tête patient, la navigation Mon compte, le layout public, la barre admin, le panneau latéral et le composant « état », dans `src/app/shared/ui/*` et `src/app/core/*`, pour que les écrans suivants les réutilisent sans les réinventer. Règle : **un écran ne modifie pas un composant partagé** ; s'il lui manque quelque chose, il ouvre une tâche séparée.

## Emplacement et stores
- `core/auth` : `SessionStore` (chargé par `GET /api/auth/me` au démarrage : identité, profession, `verificationStatus`, et, avec le contrat `v0.10.0`, `role`, `twoFactorEnabled`, `passwordChangeRequired`) ; gardes `authGuard` (401 → `/connexion?next=`), `roleGuard('admin')` (praticien sur `/admin/**` → `/patients`), redirections d'état : `email_pending` → `/confirmer-email`, `rejected` / `suspended` → `/compte-suspendu` (fiche 27).
- `core/preferences` : `PreferencesStore` (M1-16, fiche 14) : chargé au login, optimiste, `localStorage.prefs` = cache de démarrage seulement ; toutes les bascules visibles écrivent ses clés.
- `core/shortcuts` : registre des raccourcis globaux `N` `/` `L` `1` `2` `3` `M` `Échap` `?` (B2), désactivés quand un champ a le focus.
- `shared/ui/app-bar`, `shared/ui/patient-header`, `shared/ui/account-nav`, `shared/ui/public-layout`, `shared/ui/admin-bar`, `shared/ui/side-panel`, `shared/ui/state`, `shared/ui/toast`, `shared/ui/confirm-dialog`, `shared/ui/status-banner`, `shared/ui/form-errors`, `shared/ui/date-field`, `shared/ui/entry-icon` (dont `entryTitle()`) ; styles globaux `shared/ui/shared-ui.global.scss` (anneau de focus des champs natifs, `.sr-only`, `.pm-link-target`, taille des cases).

## Composants

### 1. Barre d'application (praticien) — maquettes 01, 02, 10, 14
- 48 px, fond `#FFFFFF`, bordure basse `#D6DCE6` ; logo + **PsyMate** (jamais « Suivi »), section courante (« Patients », « Mon compte »), champ de recherche globale ouvert par `/` (dans un écran patient il devient la recherche du dossier, fiche 07), bouton langue FR/EN, avatar avec menu (Mon compte, Déconnexion `POST /api/auth/logout`).
- **Sous 600 px** (375 px de référence) : la barre passe sur deux lignes ; première ligne logo + PsyMate, langue et avatar (qui ne rétrécissent pas), section courante masquée ; deuxième ligne, champ de recherche en pleine largeur. Aucun élément ne dépasse la largeur de l'écran ; la marge du contenu tombe à 16 px.
- Sous la barre, quand `verificationStatus ≠ approved` : **bandeau d'état global** ambre (`#FBF1DC` / `#7A5A12`) de la fiche 13 (« Votre dossier professionnel est en cours de vérification · Voir le dossier »), sur toutes les pages praticien, jamais sur les pages publiques ni admin.

### 2. En-tête patient permanent — maquettes 02, 03, 04, 05, 07, 16 (B2)
- Sous la barre, sur toutes les routes `/patients/:id/**` et au-dessus de tout panneau : nom affiché, âge, « suivie depuis », drapeaux (puce ambre `#B8862E` / `#FBF1DC`, jamais rouge), **séance courante** (`GET /api/patients/{id}/sessions/current` : « Séance du 24 oct. · en cours depuis 18 min », sinon « Dernière séance : 17 oct. ▾ »), bouton **Démarrer la séance** (`POST /api/patients/{id}/sessions/start`) ou **Clôturer** (`POST /api/sessions/{id}/close`, durée par défaut `sessions.defaultDurationMin`), onglets **1 Vue d'ensemble · 2 Timeline · 3 Graphe** (touches `1` `2` `3`, `aria-current`), bascule **Vue : Simple · Complète** à droite des onglets (écrit `overview.view`, visible seulement sur l'onglet 1).
- **Sous 760 px** : nom, âge et drapeaux, puis la séance courante et le bouton Démarrer / Clôturer empilés dessous ; onglets 1 · 2 · 3 sur leur ligne (défilants en secours), bascule « Vue » sur la ligne suivante. Sous 600 px, les touches `1` `2` `3` ne sont plus affichées dans les onglets (les raccourcis restent actifs).
- **Barre de capture rapide** repliée (« Noter quelque chose… ») : en M1-07 elle ouvre l'éditeur d'entrée (fiche 05) ; la grammaire et l'aperçu sont M6-02. La place est réservée dès M1-07.
- L'état de chaque onglet (filtres, zoom, position) est conservé en mémoire pendant la session de travail : changer d'onglet et revenir ne recharge rien (B2).
- Emplacement réservé, non dessiné en V1 (B9) : marqueur « rapporté par le patient » à côté du nom ; bloc « Reçu du patient » dans la grille de l'écran 2.

### 3. Navigation Mon compte — maquettes 10, 13, 14, 17, 18, 19
- Colonne gauche 220 px : nom, profession · ville ; entrées **Profil public** (pastille verte « publié » si `published`), **Préférences**, **Émotions personnalisées**, **Dossier professionnel** (pastille d'état : en attente / complément demandé / approuvé), **Compte et sécurité**, **Données et export**. Sous 760 px (et non plus seulement à 390 px) : la colonne devient une rangée d'onglets horizontaux défilants (`overflow-x: auto`) en pleine largeur, onglet actif souligné en `primary`, pastilles d'état conservées.
- Routes : `/mon-compte/profil-public`, `/mon-compte/preferences`, `/mon-compte/emotions`, `/mon-compte/dossier-professionnel`, `/mon-compte/securite`, `/mon-compte/donnees`.

### 4. Layout public — maquettes 08, 09, 11, 12, 24, 25, 26, 27
- Sans garde ni `SessionStore` obligatoire ; bandeau : logo PsyMate + « Annuaire des praticiens », liens *Annuaire*, *Comment ça marche*, bouton secondaire **Espace praticien** (→ `/connexion`, ou `/patients` si déjà connecté). Pied : mentions légales, confidentialité, accessibilité. **Pas de numéros d'urgence** (G Q20). Prérendu (SSR ou prerender) pour 08, 09, 24.
- Les formulaires publics (connexion, inscription, mot de passe oublié, contact) partagent le même conteneur centré 480 px et la même validation inline (`errors[]` sous les champs, résumé en haut avec liens vers les champs).
- Exception : la **connexion** (fiche 11) s’affiche en deux panneaux, sans ce conteneur de 480 px, et sans le bandeau ni le pied du layout public, que la maquette 11 ne montre pas (décision du propriétaire, 11 novembre 2026).

### 5. Barre d'administration — maquettes 20, 21, 22, 28
- Fond `#1E2633`, texte `#E6EAF2`, étiquette `ADMIN`, onglets **Validation (n) · Praticiens · Journal · Vocabulaire** (pas de Signalements en V1, G Q26), rappel « Aucune donnée patient n'est accessible depuis l'administration », avatar ambre. **Anneau de focus** des onglets et liens de la barre : 2 px en `--admin-focus` (#8DB7FF, 7,5:1 sur #1E2633), sans halo `primary-tint` ; le cobalt n'y ferait que 2,71:1. Le token est identique dans les trois thèmes, comme la barre, et figure dans l'étude des couleurs (§7 et §8) ; `pnpm check:tokens` vérifie le couple `admin-focus` / `admin-bar-bg`. Module lazy `/admin/**` derrière `roleGuard('admin')` ; aucun import d'un service ou composant des features patient (règle testée, C6).

### 6. Panneau latéral — maquettes 05, 06, 18
- `role="dialog"` `aria-modal="true"` `aria-labelledby`, 480 px (420 px pour l'éditeur de lien), ancré à droite, page visible derrière sous un voile `rgba(30,38,51,.45)` ; piège de focus, retour du focus à l'élément déclencheur, `Échap` ferme (le brouillon est conservé, B2) ; pied fixe avec l'action principale cobalt et les raccourcis en JetBrains Mono. Un seul panneau ouvert à la fois ; l'éditeur de lien peut s'ouvrir par-dessus l'éditeur d'entrée (pile de deux).

### 7. Composant « état », toasts, confirmation — planche 29
- `shared/ui/state` : entrées `kind = empty | loading | error`, `title`, `message`, `action?`, `details?` ; `aria-busy` et `role="status"` en chargement, `role="alert"` en erreur ; squelettes gris `#E4E8F0` sans animation obligatoire ; illustration SVG gris-bleu. Chaque écran l'utilise pour ses trois états (C5).
- `shared/ui/toast` : succès / information / avertissement, 6 s, `aria-live="polite"`, action optionnelle (« Annuler ») ; jamais d'erreur système en toast (elle va dans le composant état ou sous le champ).
- `shared/ui/confirm-dialog` : variante destructive avec le nom à retaper (archiver un patient), bouton `#9A2F22` ; le patient DEMO se supprime sans retaper le nom (fiche 29).
- `shared/ui/form-errors` (`pm-form-errors`) : résumé des `errors[]` d'un `422` en tête de formulaire, `role="alert"`, « n champs à corriger » puis un lien par champ qui y porte le focus ; il reçoit le focus à l'échec de la soumission. Utilisé par l'inscription (12), la fiche patient (16) et tout formulaire qui affiche des erreurs serveur ; les messages restent aussi sous chaque champ.
- Erreurs HTTP transverses dans un intercepteur : `401` → `/connexion?next=` ; `403 EMAIL_NOT_CONFIRMED` → `/confirmer-email` ; `403 ACCOUNT_SUSPENDED` → `/compte-suspendu` ; `423` → compte à rebours `Retry-After` ; `429` → message avec `Retry-After`, saisie conservée ; hors ligne → bandeau « Connexion perdue · vos brouillons sont conservés ».

### 8. Focus, cibles, champs date et titres d'entrée (UX-A2 à UX-A5)
- **Anneau de focus des contrôles natifs** (étude §7, WCAG 1.4.11) : `select`, `textarea` et `input` texte ou date reçoivent au `:focus-visible` le même anneau que les boutons, `outline: 2px solid var(--primary)`, `outline-offset: 2px`, halo `box-shadow: 0 0 0 4px var(--primary-tint)`, imposé par une règle globale quel que soit le style de l'écran. Exclus : les champs Material (`mat-form-field` dessine son focus), cases, radios et curseurs (anneau de leur `:focus-visible` ou de leur `label`), et **les champs sans bordure dont le conteneur porte le focus** par `:focus-within` (recherche de la barre, champ de la barre de commande, saisie de `pm-chip-picker`, et tout champ placé sous `[data-focus-ring="container"]`) : ces champs n'ont aucun anneau propre, le seul anneau est celui du conteneur.
- **Cibles de 24 px** (WCAG 2.5.8) : classe utilitaire `.pm-link-target`, posée par l'écran sur un lien texte autonome (pied public, fil d'Ariane, action de ligne, lien d'en-tête) ; la boîte passe à 24 px de haut par un `padding-block` compensé par une marge négative, sans déplacer le texte. Jamais sur un lien dans une phrase. Cases et radios natives à 18 px au moins, dans un `<label>` englobant qui porte la cible.
- **Champs date et mois** : `pm-date-field` (saisie `jj/mm/aaaa`) et `pm-month-field` (`mm/aaaa`), dans `shared/ui/date-field` : champ texte en JetBrains Mono, format de la locale (`fr` : 28/07/2003, semaine du lundi), saisie clavier, bouton calendrier qui ouvre le datepicker Material, même anneau de focus que les champs texte. Valeur du contrôle : `yyyy-MM-dd` ou `yyyy-MM`, `null` si vide. Remplacent `input[type=date]` et `[type=month]`, rendus selon le navigateur et non selon la locale. Les champs date **et heure** (`datetime-local`, précision « exacte » de l'éditeur d'entrée) restent natifs dans la livraison web : choix à confirmer, il n'est tranché ni par G ni par la fiche 05.
- **Titre de repli d'une entrée** : `entryTitle(entry, locale)` (`shared/ui/entry-icon`) nomme une entrée partout où elle apparaît dans une liste (vue d'ensemble, timeline, sélecteur et nœuds du graphe, rail, recherche, éditeur de lien) : 1) son titre ; 2) sinon les 60 premiers caractères de son texte, coupés au mot, avec « … » ; 3) sinon, pour un ressenti, « Anxiété 6/10 · 24 oct. » ; 4) sinon « Note · 24 oct. » (type et date courte, l'année seulement si ce n'est pas l'année en cours). Quand la projection ne porte pas le texte (vue d'ensemble, timeline), c'est la règle 4 qui s'applique.

## Tokens
- Surfaces : `#EEF1F6` marges, `#FFFFFF` sous le texte, bordures `#D6DCE6` ; texte `#1E2633` / `#5A6679` ; un seul élément cobalt `#3A63C2` par zone ; lavande `#8B7BD6` réservée aux ressentis ; drapeaux ambre, jamais rouge sur un patient. Variables CSS de `Etude-couleurs-Nordique.md` §8 (dont `--admin-bar-bg`, `--admin-bar-text`, `--admin-focus` pour la barre admin), thème `data-theme` light / dark / soft (M6-04), appliqué par `PreferencesStore.theme`.
- Formes par type d'entrée (icônes partagées `shared/ui/entry-icon`) : note rectangle, événement losange, situation barre, ressenti cercle, séance carré arrondi, médicament capsule.

## Accessibilité
- Landmarks : `<header>` (barre), `<nav aria-label>` (onglets, Mon compte, admin), `<main>`, `<aside>` ; lien « Aller au contenu ».
- Tous les raccourcis ont un équivalent cliquable ; focus visible 2 px cobalt décalé de 2 px + halo, y compris sur les contrôles natifs (§8), `--admin-focus` sur la barre admin ; cibles de 24 px au moins (`.pm-link-target`), 44 px sur tablette ; zoom 200 % sans perte ; `prefers-reduced-motion` respecté.
- Textes via `$localize`, français d'abord (G Q23) ; libellé « patient » remplacé par « client » selon `vocabulary.subjectLabel`.

## Critères d'acceptation
- [ ] Un composant de `shared/ui/*` n'importe rien de `features/**` (règle ESLint `import/no-restricted-paths`, testée).
- [ ] La barre, l'en-tête patient et les onglets sont rendus une seule fois par route ; changer d'onglet puis revenir conserve filtres, zoom et position (e2e).
- [ ] `Démarrer la séance` crée la séance et l'en-tête passe à « en cours » sans rechargement ; `Clôturer` propose la durée par défaut des préférences.
- [ ] Le bandeau d'état global apparaît sur toutes les pages praticien tant que `verificationStatus ≠ approved`, jamais sur `/annuaire` ni `/admin`.
- [ ] Un praticien qui tape `/admin` arrive sur `/patients` ; un visiteur sans session sur `/patients` arrive sur `/connexion?next=/patients`.
- [ ] `Échap` ferme le panneau, rend le focus au déclencheur et conserve le brouillon.
- [ ] Chaque composant a ses trois états (vide, chargement, erreur) via `shared/ui/state` ; axe-core 0 violation « serious » sur la page patient, Mon compte, une page publique et une page admin.

- [ ] À 375 × 812, `document.documentElement.scrollWidth` vaut 375 sur `/patients`, `/patients/:id`, `/patients/:id/timeline` et `/mon-compte/preferences` (e2e `mobile-375`), axe-core 0 violation.
- [ ] L'anneau de focus d'un `select` natif d'un panneau et d'un onglet de la barre admin atteint 3:1 au moins ; `pnpm check:tokens` vérifie `admin-focus` sur `admin-bar-bg`.
- [ ] Aucune cible de moins de 24 px hors liens dans une phrase sur `/connexion`, `/mon-compte/emotions` et `/admin/journal`.
- [ ] Une date de naissance s'affiche « 28/07/2003 » en `fr` ; aucune liste n'affiche le libellé seul « Note » pour une entrée sans titre.

## Écarts avec les maquettes
- **Mobile (UX-A1)** : les maquettes sont dessinées à 1440 px ; les repliements sous 760 et 600 px (§1, §2, §3) ne sont dessinés dans aucune maquette et sont décrits ici seulement.
- **Focus et cibles (UX-A2, UX-A3)** : les maquettes ne montrent pas d'état focus ; la règle des contrôles natifs, `--admin-focus` et `.pm-link-target` (§5, §8) ne changent rien à leur rendu au repos. Maquettes non modifiées.
- **Champs date (UX-A4)** : les maquettes 10 et 16 montrent des dates en mono `jj/mm/aaaa` ; `pm-date-field` les rend ainsi, avec un bouton calendrier en plus, non dessiné. Maquettes non modifiées.
- **Titres de repli (UX-A5)** : les maquettes 02, 02b et 03 écrivent « Note · date » pour une note sans titre (corrigées par UX-F1) ; les autres maquettes montrent des entrées titrées.

## Hors périmètre V1
Marqueur « rapporté par le patient » et bloc « Reçu du patient » (B9, compagnon) ; onglet Signalements (V1.1, G Q26) ; agenda dans l'en-tête (G Q25) ; menu de langue au-delà de FR/EN.
